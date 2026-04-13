# LegaldocsGPT - Backend

> AI-powered legal document platform. Pick a template, describe your situation, and get a fully structured Word/PDF document in under a minute - with a built-in editor and version history.

![CI](https://github.com/YOUR_USERNAME/legaldocsgpt_backend/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?logo=springboot)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## What it does

A user picks a legal template (employment contract, NDA, service agreement…), fills in a short form, and the platform:

1. Publishes a generation job to RabbitMQ
2. A worker pulls the job, fetches the `.docx` shell template from MinIO, calls OpenAI to fill in the clauses, and assembles the final document using docx4j
3. The finished `.docx` is stored in MinIO; Gotenberg converts it to PDF
4. The user gets an in-browser OnlyOffice editor, version history, and one-click PDF download

> **Frontend repo:** [legaldocsgpt_frontend](https://github.com/YOUR_USERNAME/legaldocsgpt_frontend) - Next.js 16, TypeScript, Tailwind

---

## Architecture


See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design decisions and tradeoff explanations.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (virtual threads, pattern matching, text blocks) |
| Framework | Spring Boot 3.4, Spring Cloud Gateway, Spring Security |
| Auth | JWT (JJWT), Google OAuth2 |
| Messaging | RabbitMQ with DLQ and automatic retry |
| Databases | PostgreSQL 16, MongoDB 7, Redis 7 |
| Storage | MinIO (S3-compatible) |
| Document processing | docx4j, Gotenberg |
| AI | OpenAI API (configurable provider - Groq works too) |
| Document editor | OnlyOffice Document Server |
| CI/CD | GitHub Actions → GHCR Docker images |
| Testing | JUnit 5, Mockito, Testcontainers (real Postgres in CI) |

---

## Quick Start

**Prerequisites:** Docker Desktop, an OpenAI or Groq API key (free tier works)

```bash
git clone https://github.com/YOUR_USERNAME/legaldocsgpt_backend.git
cd legaldocsgpt_backend

cp .env.example .env
# Edit .env - only OPENAI_API_KEY or GROQ_API_KEY is required.
# Everything else has working defaults.

docker compose up
```

The stack takes ~60 seconds to initialize on first run (Maven builds inside Docker).

| Service | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Frontend | http://localhost:3000 (start separately) |
| RabbitMQ UI | http://localhost:15672 (guest/guest) |
| MinIO Console | http://localhost:9001 |
| MailHog (email) | http://localhost:8025 |

**Default admin account:** `admin` / `admin123`

---

## Running Tests

```bash
# All tests (unit + Testcontainers integration tests)
mvn test -B

# Single module
mvn test -pl document-worker -B
```

Tests use Testcontainers - Docker must be running. The CI pipeline runs tests before building any Docker image.

---

## Project Structure

```
legaldocsgpt_backend/
├── shared/                  # Shared entities, DTOs, repositories, RabbitMQ config
├── api-gateway/             # Spring Cloud Gateway - routing + JWT auth filter
├── auth-service/            # Signup, login, Google OAuth2, password reset
├── document-generator/      # REST API - creates jobs, serves editor config, versions
├── document-worker/         # RabbitMQ consumer - AI generation + docx assembly + PDF
├── template-service/        # Template CRUD, admin upload, MongoDB + Redis cache
├── storage-service/         # MinIO wrapper - upload, download, OnlyOffice callbacks
├── docs/                    # Architecture docs, API reference
├── docker-compose.yml       # Base infrastructure
├── docker-compose.override.yml  # Dev overrides (ports, hot reload)
├── Dockerfile.base          # Shared Maven build cache layer
└── .github/workflows/ci.yml # Test → Build → Push pipeline
```

---

## Key Design Decisions

- **Why async generation?** AI calls take 10–30s. Publishing to RabbitMQ lets the API return immediately with a `jobId`. The frontend polls `/status/:jobId`. Workers can scale independently.
- **Why a shared library?** `DocumentJob`, `DocumentVersion`, and all RabbitMQ event DTOs live in `shared/` - consumed by both `document-generator` (writes jobs) and `document-worker` (processes them) without duplication.
- **Why two Postgres databases?** Auth and document data have separate scaling, backup, and migration lifecycles. Keeping them isolated prevents auth schema changes from locking document queries.
- **Security at the gateway:** JWT validation happens once in `AuthProxyGlobalFilter` before any downstream service sees the request. Internal services trust the `X-User-*` headers injected by the gateway.

Full explanations in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Docs

- [Architecture & Design Decisions](docs/ARCHITECTURE.md)
- [API Reference](docs/API.md)
- [Local Development Guide](docs/DEVELOPMENT.md)
- [Service Descriptions](docs/SERVICES.md)
