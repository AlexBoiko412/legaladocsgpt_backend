# Local Development Guide

---

## Prerequisites

- **Docker Desktop** - all infrastructure runs in containers
- **Java 21** - only needed if you want to run tests without Docker or use an IDE
- **An AI API key** - OpenAI or Groq (free tier). This is the only secret you need to provide.

---

## First-Time Setup

```bash
git clone https://github.com/YOUR_USERNAME/legaldocsgpt_backend.git
cd legaldocsgpt_backend

# Copy the example env file
cp .env.example .env
```

Open `.env` and fill in your AI key. The minimum required config:

```env
# Pick one:
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...

# OR use Groq (free tier, fast):
AI_PROVIDER=groq
GROQ_API_KEY=gsk_...
```

Everything else - database credentials, JWT secrets, MinIO keys - has working defaults in `.env.example` that are fine for local development.

---

## Starting the Stack

```bash
docker compose up
```

First run takes **3–5 minutes** - Maven downloads all dependencies inside Docker and builds all 6 services. Subsequent starts take ~20 seconds (cached).

Watch the logs until you see all services report healthy:
```
auth-service       | Started AuthServiceApplication in 4.2 seconds
document-generator | Started DocumentGeneratorApplication in 3.8 seconds
...
```

Or wait for all health checks to pass:
```bash
docker compose ps   # all should show "healthy"
```

---

## Starting the Frontend

The frontend is a separate repo:

```bash
git clone https://github.com/YOUR_USERNAME/legaldocsgpt_frontend.git
cd legaldocsgpt_frontend

cp .env.example .env.local
# NEXT_PUBLIC_API_URL=http://localhost:8080 is already set in the example

npm install
npm run dev
```

Open http://localhost:3000.

---

## What's Running

| Service | URL | Credentials |
|---|---|---|
| Frontend | http://localhost:3000 | - |
| API Gateway | http://localhost:8080 | - |
| Auth Service | http://localhost:8081 | - |
| Document Generator | http://localhost:8082 | - |
| Template Service | http://localhost:8083 | - |
| Storage Service | http://localhost:8084 | - |
| Document Worker | http://localhost:8085 | - |
| RabbitMQ Management | http://localhost:15672 | guest / guest |
| MinIO Console | http://localhost:9001 | see .env MINIO_ROOT_USER/PASSWORD |
| MailHog (email UI) | http://localhost:8025 | - |
| Auth DB (Postgres) | localhost:5433 | see .env POSTGRES_USER/PASSWORD |
| Document DB (Postgres) | localhost:5434 | see .env POSTGRES_USER/PASSWORD |
| MongoDB | localhost:27017 | - |

**Default app accounts:**
- Admin: `admin` / `admin123`
- Create your own via `/signup`

---

## Running Tests

```bash
# All tests across all modules
mvn test -B

# Specific module
mvn test -pl document-worker -B
mvn test -pl auth-service -B

# Specific test class
mvn test -pl document-worker -Dtest=DocumentJobInternalServiceTest -B
```

**Test types:**
- **Unit tests** (no Spring context, no Docker): `JwtUtilTest`, `AuthServiceTest`, `WordProcessingServiceTest`, `EditTokenServiceTest`
- **WebMvc tests** (Spring MVC slice, mocked beans): `StorageControllerCallbackTest`
- **Testcontainers integration tests** (real Postgres in Docker): `DocumentJobInternalServiceTest`, `DocumentRepositoryTest`

Testcontainers requires Docker to be running. Tests start their own Postgres container - they don't use the one from `docker compose up`.

---

## Making Changes

The dev Dockerfiles use volume mounts so your local source code is mounted into the container. Services restart automatically on code changes (Spring DevTools).

```bash
# Restart a single service after changes
docker compose restart auth-service

# Rebuild a service if you changed pom.xml
docker compose up --build auth-service
```

If you change `shared/`, rebuild it first:
```bash
# The init-shared service does this automatically on compose up,
# but you can also run it manually:
docker compose run --rm init-shared
```

---

## Adding a New Template

Templates are seeded by `DatabaseSeeder` in `template-service` on startup. To add one:

1. Create a `.docx` shell template with `${variableName}` placeholders for the fields that AI will fill
2. Upload it via the admin UI at `/admin/templates` (login as admin first)
3. Or call `POST /api/templates/admin` directly with the `.docx` file and field definitions

---

## Environment Variables Reference

All variables are documented in `.env.example` with inline comments. Key ones:

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | One of these | OpenAI API key |
| `GROQ_API_KEY` | One of these | Groq API key (free tier) |
| `AI_PROVIDER` | Yes | `openai` or `groq` |
| `JWT_SECRET` | Yes | 32+ char secret for signing JWTs |
| `GOOGLE_CLIENT_ID` | Optional | For Google OAuth2 login |
| `GOOGLE_CLIENT_SECRET` | Optional | For Google OAuth2 login |
| `POSTGRES_USER` | Yes | Shared Postgres username |
| `POSTGRES_PASSWORD` | Yes | Shared Postgres password |
| `MINIO_ROOT_USER` | Yes | MinIO admin username |
| `MINIO_ROOT_PASSWORD` | Yes | MinIO admin password |

---

## Troubleshooting

**Services fail to start / can't connect to database:**
Databases need to be healthy before services start. If something starts too fast:
```bash
docker compose down && docker compose up
```

**`init-shared` keeps rerunning:**
This is normal - it's a one-shot service that installs the shared library into the Maven cache. It completes in ~30s on first run, then ~2s on subsequent runs (cached).

**OnlyOffice is slow or doesn't load:**
OnlyOffice is a heavy container (~800MB). Give it 60–90 seconds on first start. If it fails health checks, allocate more memory to Docker Desktop (8GB+ recommended for the full stack).

**Tests fail with "connection refused":**
Docker must be running for Testcontainers. Check `docker ps`.

**Google OAuth doesn't work:**
That's expected in local dev without `GOOGLE_CLIENT_ID` set. Email/password auth works without it.
