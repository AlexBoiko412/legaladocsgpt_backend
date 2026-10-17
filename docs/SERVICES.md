# Service Descriptions

Each service is a standalone Spring Boot application. They communicate via the API Gateway (HTTP) and RabbitMQ (async events).

---

## shared

**Type:** Maven library (not a deployable service)
**Port:** N/A

The `shared` module is a compiled library consumed by all other services. It contains the building blocks that multiple services need to agree on.

**What's in it:**
- `DocumentJob` and `DocumentVersion` JPA entities - defined once, used in both `document-generator` and `document-worker`
- `DocumentJobRepository` and `DocumentVersionRepository` - Spring Data repositories
- All RabbitMQ event DTOs: `DocumentGenerationEvent`, `DocumentFinalizeEvent`, `DocumentRestoreEvent`, `DocumentConvertEvent`
- `SharedRabbitConfig` - queue names, exchange names, retry/DLQ topology
- `UserContextHolder` - ThreadLocal storage for the current user's identity (set by the gateway headers)
- `FeignUserContextInterceptor` - propagates user identity headers on inter-service Feign calls
- `AbstractGlobalExceptionHandler`, `GlobalErrorCode`, `BaseBusinessException` - unified error response format

**Key design note:** Shared entities mean `document-generator` and `document-worker` share the same Postgres schema and JPA mappings. This is intentional - they're two faces of the same domain (document lifecycle), not truly independent services.

---

## api-gateway

**Port:** 8080
**Framework:** Spring Cloud Gateway (reactive, WebFlux)

The single entry point for all external traffic. Every request from the frontend goes through here.

**Responsibilities:**
- Route requests to the correct downstream service based on path prefix (`/api/auth/**` → auth-service, `/api/documents/**` → document-generator, etc.)
- Authenticate every non-public request by calling `GET /api/auth/validate` on auth-service
- Inject `X-User-Id`, `X-User-Email`, `X-User-Username`, `X-User-Role` headers onto downstream requests
- Block unauthenticated requests with 401 before they reach any service

**Public paths** (no auth required): `/api/auth/login`, `/api/auth/signup`, `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/oauth2/**`, `/api/storage/callback`

**Caching:** Auth validation responses are cached with Caffeine (short TTL) to avoid a round-trip to auth-service on every single request.

**Key class:** `AuthProxyGlobalFilter` - the global WebFilter that intercepts requests, calls validate, injects headers.

---

## auth-service

**Port:** 8081
**Database:** PostgreSQL (`auth-db`)

Manages user identity, credentials, and sessions.

**Responsibilities:**
- Signup / login / logout with `HttpOnly` JWT cookie
- Google OAuth2 via Spring Security - `CustomOAuth2UserService` handles account creation/linking, `CustomOAuth2SuccessHandler` sets the JWT cookie and redirects
- JWT generation and validation (`JwtUtil` - JJWT library, HS256)
- Password change (local accounts only)
- Password reset flow: `TokenService` (SHA-256 hashed tokens, SecureRandom), `EmailService` (async JavaMailSender), `TokenCleanupJob` (daily scheduled cleanup)
- Admin seed account via `DataLoader` (`CommandLineRunner`)

**Tables:** `users`, `auth_tokens` (unified for PASSWORD_RESET and future EMAIL_VERIFICATION)

**Key security decisions:**
- Raw reset tokens never stored - only SHA-256 hash
- `forgotPassword` always returns 200 (prevents email enumeration)
- Google OAuth users are silently ignored in password reset flow

---

## document-generator

**Port:** 8082
**Database:** PostgreSQL (`document-db`, shared schema with document-worker)

The HTTP API layer for the document domain. Handles all REST requests from the frontend related to documents, but does no heavy processing itself.

**Responsibilities:**
- Accept `POST /generate` requests, validate the template exists, create a `DocumentJob` in Postgres, publish `DocumentGenerationEvent` to RabbitMQ, return `jobId` immediately
- Serve `GET /status/:jobId` for polling
- Serve `GET /:jobId/editor-config` - fetches the `.docx` URL from MinIO, generates a signed OnlyOffice JWT, returns the full editor config object
- Accept `POST /:jobId/finalize` for AI refinement - saves current content snapshot, publishes `DocumentFinalizeEvent`
- Accept `POST /:jobId/convert` for PDF conversion - publishes `DocumentConvertEvent`
- Document list, rename, delete, version history, version restore (restore publishes `DocumentRestoreEvent`)

**Key dependency:** Feign client to `template-service` for fetching template metadata and the `.docx` shell file URL.

**User isolation:** Every query uses `userId` from `UserContextHolder` - users can only access their own documents. Ownership is enforced at the repository query level.

---

## document-worker

**Port:** 8085 (HTTP for health check only)
**Database:** PostgreSQL (`document-db`, same as document-generator)
**Queues:** Listens on `document_generation_queue`, `document_finalize_queue`, `document_restore_queue`, `document_convert_queue`

The async processing engine. Does all the heavy lifting.

**Generation flow (`DocumentWorker.handleGenerate`):**
1. Mark job `IN_PROGRESS`
2. Fetch `.docx` shell template bytes from MinIO via StorageClient (Feign)
3. Extract existing content from `.docx` using `WordProcessingService` (docx4j)
4. Build the AI prompt using `PromptBuilder` (includes template structure + user field data)
5. Call AI provider (`OpenAIProvider` - supports both OpenAI and Groq via config)
6. Parse AI response (structured JSON with field values)
7. `WordProcessingService.assembleDocument` - replaces `${variable}` placeholders in the `.docx` shell with AI-generated content
8. Upload assembled `.docx` to MinIO
9. Trigger PDF conversion via Gotenberg (`WordToPdfService`)
10. Upload PDF to MinIO
11. Mark job `COMPLETED`, save `DocumentVersion` snapshot

**Error handling:** Unhandled exceptions propagate to RabbitMQ, which retries 3 times with backoff. After exhausting retries, the message goes to the DLQ. `handleDeadLetter` catches DLQ messages and marks the job `FAILED`.

**Key classes:**
- `DocumentWorker` - RabbitMQ listener, orchestrates the pipeline
- `DocumentJobInternalService` - all Postgres operations (completeJob, failJob, completeJobRestore)
- `WordProcessingService` - docx4j operations (extract text, replace variables, assemble document)
- `WordToPdfService` - calls Gotenberg HTTP API to convert `.docx` → `.pdf`
- `OpenAIProvider` - HTTP client for AI API calls
- `PromptBuilder` - constructs the structured prompt from template metadata + user data

---

## template-service

**Port:** 8083
**Database:** MongoDB (`template-db`) + Redis cache

Stores and serves legal document templates.

**Responsibilities:**
- CRUD for templates (admin-only writes, public reads)
- Template `.docx` shell files stored in MinIO, metadata in MongoDB
- Redis cache for template list and individual templates (invalidated on update/delete)
- `DatabaseSeeder` - seeds initial templates on startup if the collection is empty

**Template structure:** Each template has a `name`, `description`, `category`, and a `fields` array. Each field has `key`, `label`, `type` (text/date/select), `required`, and optional `options`. This drives the form the user fills out before generation.

---

## storage-service

**Port:** 8084
**No database** (stateless - talks to MinIO directly)

A thin wrapper around MinIO with one special responsibility: handling OnlyOffice callbacks.

**Responsibilities:**
- `GET /download-raw?key=...` - stream file bytes from MinIO to the browser
- `POST /callback` - receive save callbacks from OnlyOffice Document Server after a user edits a document. Fetches the updated `.docx` from the OnlyOffice-provided URL, uploads it to MinIO, updates the `docx_key` on the `DocumentJob`.

**OnlyOffice callback status codes:**
| Code | Meaning | Action |
|---|---|---|
| 1 | Being edited | No action |
| 2 | Ready to save | Download and store |
| 3 | Save error | Log and ignore |
| 6 | Forcefully saved | Download and store |

**Feign client:** `DocumentJobClient` - updates `docxKey` on the job after saving the new version from OnlyOffice.
