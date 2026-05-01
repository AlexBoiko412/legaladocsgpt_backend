# API Reference

All endpoints are accessed through the API Gateway at `http://localhost:8080`.
Authentication uses an `HttpOnly` cookie named `token` (JWT). The gateway validates it on every request and injects `X-User-*` headers for downstream services.

---

## Auth - `/api/auth`

### POST /api/auth/signup
Create a new local account.

**Request**
```json
{ "username": "alex", "email": "alex@example.com", "password": "secret123" }
```

**Response - 200 OK**
Sets `token` cookie. Empty body.

**Errors**
- `400` - username too short (< 3 chars), email already exists, username already exists

---

### POST /api/auth/login
Authenticate with username or email + password.

**Request**
```json
{ "username": "alex", "password": "secret123" }
// or
{ "email": "alex@example.com", "password": "secret123" }
```

**Response - 200 OK**
Sets `token` cookie. Empty body.

**Errors**
- `401` - invalid credentials

---

### POST /api/auth/logout
Clear the session cookie.

**Response - 200 OK**
Clears `token` cookie. Empty body.

---

### GET /api/auth/validate
Used internally by the API Gateway to validate every request. Also used by the Next.js middleware.

**Response - 200 OK**
```json
{
  "id": 1,
  "username": "alex",
  "email": "alex@example.com",
  "role": "ROLE_USER"
}
```

**Errors**
- `401` - missing or invalid token

---

### GET /api/auth/me
Get the current user's profile.

**Response - 200 OK**
```json
{
  "email": "alex@example.com",
  "username": "alex",
  "role": "ROLE_USER",
  "provider": "LOCAL"
}
```

---

### PATCH /api/auth/me/password
Change password. Only available for `LOCAL` provider accounts (not Google OAuth).

**Request**
```json
{ "currentPassword": "oldpass", "newPassword": "newpass123" }
```

**Response - 200 OK**

**Errors**
- `400` - new password too short, wrong current password, Google account

---

### POST /api/auth/forgot-password
Initiate password reset. **Always returns 200** - even if the email doesn't exist, to prevent email enumeration.

**Request**
```json
{ "email": "alex@example.com" }
```

**Response - 200 OK** (always, empty body)

A reset link is sent to the email if the account exists and is a LOCAL account. The link expires in 1 hour and is single-use.

---

### POST /api/auth/reset-password
Complete password reset using the token from the email link.

**Request**
```json
{ "token": "raw-token-from-email-link", "newPassword": "newpass123" }
```

**Response - 200 OK**

**Errors**
- `400` - `{ "message": "Reset token is invalid or has expired" }` - token not found, already used, or expired
- `400` - new password too short (< 8 chars)

---

### GET /api/auth/oauth2/authorization/google
Redirect to Google OAuth2 login. Handled by Spring Security - not a JSON endpoint. Navigate the browser here directly.

---

## Documents - `/api/documents`

Requires authentication. All operations are scoped to the current user's documents.

### GET /api/documents
List all documents for the current user.

**Query params**
- `search` (optional) - case-insensitive title search

**Response - 200 OK**
```json
[
  {
    "jobId": "uuid",
    "title": "Employment Contract",
    "status": "COMPLETED",
    "lastEditedAt": "2025-04-01T14:30:00",
    "pdfUrl": "/api/storage/download-raw?key=documents/uuid/v2.pdf",
    "errorDetails": null
  }
]
```

**Status values:** `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`

---

### POST /api/documents/generate
Start a new document generation job. Returns immediately - generation is async.

**Request**
```json
{
  "templateId": "mongo-object-id",
  "data": {
    "employerName": "Acme Corp",
    "employeeName": "Jane Smith",
    "startDate": "2025-05-01"
  }
}
```

**Response - 200 OK**
```json
{ "jobId": "uuid", "status": "PENDING" }
```

Poll `GET /api/documents/status/:jobId` to track progress.

---

### GET /api/documents/status/:jobId
Poll generation status for a job.

**Response - 200 OK**
```json
{
  "jobId": "uuid",
  "status": "COMPLETED",
  "errorDetails": null,
  "pdfUrl": "/api/storage/download-raw?key=documents/uuid/v1.pdf"
}
```

---

### GET /api/documents/:jobId/editor-config
Get the OnlyOffice editor configuration for a document. Includes a signed JWT for the OnlyOffice Document Server.

**Response - 200 OK**
```json
{
  "document": { "fileType": "docx", "key": "...", "title": "...", "url": "..." },
  "editorConfig": { "callbackUrl": "...", "user": { "id": "...", "name": "..." } },
  "token": "onlyoffice-jwt"
}
```

---

### POST /api/documents/:jobId/finalize
Trigger AI refinement of the document. Sends the current content + a refinement prompt to the AI worker via RabbitMQ. Returns immediately.

**Request**
```json
{ "refinementPrompt": "Add a non-compete clause for 12 months" }
```
`refinementPrompt` is optional - if omitted, the document is re-generated from the same template data.

**Response - 200 OK** (empty body)

---

### POST /api/documents/:jobId/convert
Convert the `.docx` to PDF. Triggered after edits in OnlyOffice.

**Response - 200 OK** (empty body)

---

### PATCH /api/documents/:jobId/title
Rename a document.

**Request**
```json
{ "title": "New Title" }
```

**Response - 200 OK** (empty body)

---

### DELETE /api/documents/:jobId
Delete a document and all its versions. Only the owner can delete.

**Response - 204 No Content**

---

### GET /api/documents/:jobId/versions
Get version history for a document, newest first.

**Response - 200 OK**
```json
[
  {
    "id": 3,
    "version": 3,
    "source": "REFINEMENT",
    "refinementPrompt": "Add a non-compete clause",
    "createdAt": "2025-04-01T15:00:00"
  },
  {
    "id": 1,
    "version": 1,
    "source": "INITIAL",
    "refinementPrompt": null,
    "createdAt": "2025-04-01T14:30:00"
  }
]
```

---

### POST /api/documents/:jobId/versions/:version/restore
Restore a document to a previous version. The current content is snapshotted first (a new version is created), then the document is overwritten with the target version.

**Response - 200 OK** (empty body)

---

## Templates - `/api/templates`

### GET /api/templates
List all available templates. Cached in Redis.

**Response - 200 OK**
```json
[
  {
    "id": "mongo-id",
    "name": "Employment Contract",
    "description": "Standard employment agreement",
    "category": "Employment",
    "fields": [
      { "key": "employerName", "label": "Employer Name", "type": "text", "required": true },
      { "key": "startDate", "label": "Start Date", "type": "date", "required": true }
    ]
  }
]
```

---

### GET /api/templates/:id
Get a single template by ID.

---

### POST /api/templates/admin
**Admin only.** Upload a new template. Accepts `multipart/form-data`.

**Form fields**
- `file` - `.docx` shell template
- `name`, `description`, `category` - metadata
- `fields` - JSON array of field definitions

---

### PUT /api/templates/admin/:id
**Admin only.** Update an existing template.

---

### DELETE /api/templates/admin/:id
**Admin only.** Delete a template.

---

## Storage - `/api/storage`

### GET /api/storage/download-raw?key=:key
Download a file (`.docx` or `.pdf`) by its MinIO key. Returns the file bytes directly.

**Example:** `/api/storage/download-raw?key=documents/abc-uuid/v2.pdf`

---

### POST /api/storage/callback
**Internal - called by OnlyOffice Document Server only.** Handles document save callbacks from OnlyOffice after the user edits a document in the browser. Updates the stored `.docx` in MinIO based on the callback status code.

| Status | Meaning |
|---|---|
| 1 | Document being edited (no action) |
| 2 | Document ready to save |
| 3 | Save error |
| 6 | Forcefully saved |

---

## Error Response Format

All errors from all services follow this structure:

```json
{
  "timestamp": "2025-04-01T14:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "A user with this email already exists",
  "code": "INVALID_INPUT"
}
```

**Error codes:** `INVALID_INPUT`, `UNAUTHORIZED`, `NOT_FOUND`, `THIRD_PARTY_API_ERROR`
