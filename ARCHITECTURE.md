# Architecture & Design Decisions

This document explains *why* the system is built the way it is - the tradeoffs, the reasoning, and what would be done differently at different scales.

---

## System Overview

LegaldocsGPT is a Java 21 / Spring Boot 3.4 backend structured as a set of focused microservices behind a single API Gateway. The frontend is a Next.js 16 app that talks exclusively to the gateway.


---

## Why Microservices?

**Honest answer: mostly for portfolio value and learning, not operational necessity.**

At the current scale (single developer, no production traffic), a well-structured monolith would be simpler to operate. The genuine architectural arguments for this split are:

**document-worker is genuinely separate.** It is CPU/IO-intensive, depends on large libraries (docx4j, OpenAI SDK), calls external APIs with unpredictable latency (10–30s per generation), and needs to scale independently from the request-serving tier. Separating it means a slow AI call doesn't block HTTP threads in document-generator.

**auth-service has a different security surface.** Credential storage, JWT signing, OAuth2 flows, password reset tokens - these are high-sensitivity operations. Isolating them means a vulnerability in document handling doesn't immediately compromise auth.

**The rest of the split (template-service, storage-service) is more debatable.** They're small enough that they could live in the same process as document-generator. The split keeps concerns clean but adds inter-service HTTP calls (via Feign) that could be local method calls.

**What this means in practice:** The project demonstrates the ability to design, wire up, and operate a distributed system - service discovery, shared libraries, health checks, Docker networking, RabbitMQ messaging, CI that builds 7 images in parallel. That's the point.

---

## Async Document Generation

The core architectural decision: document generation is asynchronous.

**The flow:**
1. Client calls `POST /api/documents/generate` - returns immediately with `{ jobId, status: "PENDING" }`
2. `document-generator` saves the job to Postgres, publishes a `DocumentGenerationEvent` to RabbitMQ
3. `document-worker` consumes the event, runs the AI pipeline, updates job status to `COMPLETED` or `FAILED`
4. Client polls `GET /api/documents/status/:jobId` until status changes

**Why not WebSocket or Server-Sent Events?**
Polling is simpler to implement, cache, and debug. At the expected request rate (low), the polling overhead is negligible. SSE would be the right upgrade path if real-time feedback becomes important.

**Why RabbitMQ instead of direct HTTP?**
- Natural retry semantics: failed messages go to a dead-letter queue (DLQ) after 3 attempts with exponential backoff, rather than the caller needing to implement retry logic
- Decoupling: document-worker can restart, redeploy, or scale to multiple replicas without document-generator knowing or caring
- Backpressure: if AI is slow, messages queue up rather than causing timeouts or cascading failures

**The DLQ:** `SharedRabbitConfig` configures a DLQ that catches messages exhausting all retries. `DocumentWorker.handleDeadLetter` processes these, marks the job as `FAILED` with an error message, and logs for debugging. This prevents silent message loss.

---

## Security Architecture

**JWT validation at the gateway - once.**

`AuthProxyGlobalFilter` in api-gateway intercepts every inbound request, calls `/api/auth/validate` on auth-service, and injects `X-User-Id`, `X-User-Email`, `X-User-Role` headers downstream. Internal services read these headers via `UserContextHolder` - they don't validate JWTs themselves.

**Why this approach:**
- Single point of truth for auth logic
- Internal services are simpler - they trust headers, don't need JWT libraries
- Easy to add new services without repeating auth code

**The tradeoff:** Internal services trust the headers completely. If something bypassed the gateway (a misconfigured Docker network, a direct call to port 8081), it could forge user identity. In production this is mitigated by keeping internal services on an isolated Docker network (`database` network in docker-compose) not exposed to the outside.

**Password reset tokens:**
- Raw token is 32 bytes of `SecureRandom` - 256 bits of entropy
- Only the SHA-256 hash is stored in the database
- Tokens are single-use (`usedAt` set on redemption) and expire in 1 hour
- `forgotPassword` always returns 200 - prevents email enumeration

---

## Data Storage Decisions

### Two PostgreSQL databases

`auth-db` stores users, sessions, and auth tokens. `document-db` stores document jobs and version history. They are separate containers.

**Why:** Different migration lifecycles. Auth schema changes (adding email verification, changing token structure) shouldn't require coordination with document schema changes. They also have different backup requirements - auth data is more sensitive. The operational cost is low (two Postgres containers in Docker Compose).

### MongoDB for templates

Templates are structured JSON documents with nested field definitions (`fields: [{ key, label, type, required, options }]`). This structure is schema-flexible - different template types have different field shapes. MongoDB's document model fits this naturally. PostgreSQL would work too with a `JSONB` column, but MongoDB gives cleaner query semantics for document arrays.

Templates are cached in Redis after first load. Template data changes rarely (admin-only writes), so cache invalidation on update is straightforward.

### MinIO for file storage

MinIO is an S3-compatible object store that runs as a Docker container. All `.docx` and `.pdf` files are stored there.

**The production path is zero-code change:** swap `S3_ENDPOINT` from `http://minio:9000` to `https://s3.amazonaws.com` and update credentials. The AWS SDK doesn't care - same API.

Stored keys follow a predictable convention: `documents/{jobId}/v{version}.docx`, `documents/{jobId}/v{version}.pdf`. This makes version history storage trivial.

---

## Shared Library Pattern

`shared/` is a Maven module consumed by multiple services. It contains:

- **Entities:** `DocumentJob`, `DocumentVersion`, `JobStatus` - shared between `document-generator` (which creates and reads them) and `document-worker` (which updates them)
- **Repositories:** `DocumentJobRepository`, `DocumentVersionRepository` - defined once, used in both services
- **Event DTOs:** `DocumentGenerationEvent`, `DocumentFinalizeEvent`, `DocumentRestoreEvent`, `DocumentConvertEvent` - the contracts for RabbitMQ messages
- **Shared config:** `SharedRabbitConfig` (queue/exchange names), `FeignUserContextInterceptor` (propagates user headers on inter-service Feign calls)
- **Exception framework:** `AbstractGlobalExceptionHandler`, `GlobalErrorCode`, `BaseBusinessException` - consistent error responses across all services

**The tradeoff:** Shared library creates coupling between services - a breaking change to a shared entity requires recompiling all consumers. This is acceptable here because all services are in the same Maven multi-module project and deploy together. In a true polyglot microservices environment, you'd use a separate versioned library or event schema registry.

---

## Document Version History

Every time a document generation or refinement completes, a `DocumentVersion` snapshot is saved before the job's content is overwritten. This gives users a full audit trail and the ability to restore any previous version.

**Version sources:** `INITIAL` (first generation) and `REFINEMENT` (subsequent AI edits via a user prompt).

**Restore flow:** When restoring version N, the *current* content is snapshotted first (as a new version), then the job content is overwritten with version N's content. The version counter always increments - there's no "going back" that loses history.

This logic lives in `DocumentJobInternalService` and is covered by Testcontainers integration tests that verify snapshot creation, version increment, and the restore-before-overwrite behavior against a real PostgreSQL instance.

---

## CI/CD Pipeline

```
push to main/dev
    ↓
test job (mvn test -B, Testcontainers, ~2min with cache)
    ↓ only if green
build-base (shared Maven fat-jar cache image, ~10s with GHA cache)
    ↓
build-services (6 services in parallel, ~30s each, push to GHCR on main)
```

**Base image strategy:** `Dockerfile.base` runs `mvn dependency:go-offline` once and caches all Maven dependencies. Service Dockerfiles use `FROM legaldocsgpt-base` - they only recompile source, not redownload dependencies. This cuts build time from ~8 minutes to ~30 seconds per service after the first run.

**GitHub Container Registry:** All images are pushed to `ghcr.io/{owner}/legaldocsgpt-{service}:latest` and `:sha`. This means any machine with Docker can run the exact build from any commit.

---

## What Would Be Different in Production

- **Rate limiting** on generation endpoints (Redis token bucket - infrastructure is already there)
- **Email verification** on signup (token infrastructure built, flow not yet wired)
- **Sentry** for error monitoring and performance tracing
- **Horizontal scaling** of document-worker - just run more replicas, RabbitMQ handles distribution
- **Real S3** instead of MinIO - zero code change, env var swap only
- **Secrets management** - currently `.env` file, would move to Vault or cloud secrets manager
- **HTTPS** - add a reverse proxy (Nginx or Caddy) in front of the gateway
