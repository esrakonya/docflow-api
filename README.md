# Docflow API

Docflow API is a REST API that extracts structured data (vendor name, invoice number, amount, VAT, line items, etc.) from invoices and other documents using AI (Anthropic Claude or Google Gemini). Processing after upload is handled asynchronously via Kafka, and the result is optionally reported back through a signed webhook.

On top of the extraction pipeline, the project is a small SaaS in its own right: plan-based quotas and rate limits, Stripe billing (checkout + customer portal + webhook-driven subscription lifecycle), API key rotation, and a session-authenticated, browser-facing client dashboard.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Environment Variables](#environment-variables)
- [Authentication](#authentication)
- [API Usage](#api-usage)
- [Client Dashboard](#client-dashboard)
- [Billing](#billing)
- [Admin Panel](#admin-panel)
- [Webhooks](#webhooks)
- [Storage](#storage)
- [Testing](#testing)
- [Known Limitations](#known-limitations)

## Features

-  AI-powered data extraction from PNG, JPEG, and PDF documents
-  A provider-agnostic AI layer, switchable between Anthropic Claude and Google Gemini via configuration
-  Kafka-based asynchronous processing with automatic retries and a dead-letter topic (DLT) for fault tolerance
-  HMAC-SHA256 signed webhook notifications (with retry)
-  API key authentication with **key rotation**: a client can hold several active keys at once, each labeled, individually revocable, with a safeguard against revoking a client's last active key
-  Per-minute rate limiting (Redis) and monthly usage quotas, both plan-driven (`FREE` / `PRO` / `ENTERPRISE`)
-  **Stripe billing**: hosted Checkout for upgrades, the Stripe Customer Portal for self-service subscription management, and a webhook handler that keeps a client's plan in sync with their subscription state
-  A session-authenticated, server-rendered (Thymeleaf) **client dashboard** - usage overview, API key management, and billing, all behind a login backed by the client's own API key
-  A separate, JWT-protected **admin panel** for browsing all clients' documents and processing status
-  Swappable file storage between MinIO (S3-compatible) and local disk 


## Architecture

```
Client → [API Key Auth] → DocumentController
                                  │
                       (file validation, rate limiting, quota check)
                                  │
                        Save to storage (MinIO / local)
                                  │
                        Kafka: "document-uploaded"
                                  │
                        DocumentProcessingWorker (consumer)
                                  │
                        Send to AI (Claude / Gemini)
                                  │
                        Write result to DB (ExtractedData + line items)
                                  │
                        Send signed webhook (if configured)
```

On failure, Kafka's `@RetryableTopic` mechanism retries up to 3 times with exponential backoff (2s, then doubling); as a last resort, the message is routed to a dead-letter topic (`document-uploaded-dlt`).

Separately from the document pipeline, three independent Spring Security filter chains cover three different kinds of caller - see [Authentication](#authentication)

### Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.5 |
| Database | PostgreSQL + Flyway migrations |
| Messaging | Apache Kafka |
| Cache / Rate limiting | Redis |
| File storage | MinIO (S3-compatible) or local disk |
| AI | Spring AI (Anthropic Claude / Google Gemini) |
| Billing | Stripe (Checkout, Customer Portal, Webhooks) |
| Admin panel & client dashboard | Thymeleaf |
| API documentation | springdoc-openapi (Swagger UI) |

## Requirements

- **Java 21** (JDK, for building and running)
- **Docker & Docker Compose** (to spin up Postgres, Kafka, Redis, and MinIO)
- An **Anthropic** and/or **Google Gemini** API key
- A **Stripe** account (test mode is fine) if you want to exercise the billing flow - the app will still start without one, but checkout/portal calls will fail

> ⚠️ `docker-compose.yml` only includes infrastructure services (Postgres, Kafka, Redis, MinIO). The application itself (`docflow-api`) is not currently part of the compose file — it needs to be run separately (via `./mvnw spring-boot:run` or your IDE), as described below.

## Quick Start

### 1. Start the infrastructure

```bash
git clone https://github.com/esrakonya/docflow-api.git
cd docflow-api
docker-compose up -d
```

This starts:

| Service | Port |
|---|---|
| PostgreSQL | 5432 |
| Kafka | 9092 |
| Redis | 6379 |
| MinIO (API) | 9000 |
| MinIO (Console) | 9001 (`minioadmin` / `minioadminpassword`) |

> Note: the MinIO service pulls `bitnamilegacy/minio, not `minio/minio`. MinIO stopped publishing free images to Docker Hub and quay.io in September 2026, this is the last publicly pullable mirror.

### 2. Configure environment variables

Copy `.env.example` to `.env` and fill in the values (at minimum, an AI provider key), or export the ones you need directly:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export GEMINI_API_KEY=...
```

(You don't need to provide both — whichever is selected in `AI_PROVIDER` is the one that's used; see [Environment Variables](#environment-variables).)

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080` by default. On first startup it seeds an admin user from the configured `ADMIN_USERNAME` and `ADMIN_PASSWORD` values (see [Admin Panel](#admin-panel)). If `STORAGE_TYPE=minio`, it also creates the MinIO bucket if it doesn't already exist.

### 4. Verify

```bash
curl http://localhost:8080/actuator/health
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Environment Variables

| Variable | Required? | Default | Description |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | — | Postgres JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | — | Postgres username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | — | Postgres password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Yes | — | Kafka bootstrap servers |
| `SPRING_DATA_REDIS_HOST` | Yes | — | Redis host |
| `SPRING_DATA_REDIS_PORT` | Yes | — | Redis port |
| `JWT_SECRET` | Yes | — | Base64 secret used to sign admin JWTs (at least 32 bytes) |
| `ADMIN_USERNAME` | Yes | — | Username seeded for the admin panel / admin API on first startup |
| `ADMIN_PASSWORD` | Yes | — | Password seeded for that admin user |
| `AI_PROVIDER` | Yes | — | `anthropic` or `google` |
| `ANTHROPIC_API_KEY` | Yes, if `AI_PROVIDER=anthropic` | — | Anthropic Claude API key |
| `GEMINI_API_KEY` | Yes, if `AI_PROVIDER=google` | — | Google Gemini API key |
| `STORAGE_TYPE` | Yes | — | `minio` or `local` |
| `MINIO_ENDPOINT` | Yes, if `STORAGE_TYPE=minio` | — | MinIO endpoint URL |
| `MINIO_ACCESS_KEY` | Yes, if `STORAGE_TYPE=minio` | — | MinIO access key |
| `MINIO_SECRET_KEY` | Yes, if `STORAGE_TYPE=minio` | — | MinIO secret key |
| `MINIO_BUCKET` | Yes, if `STORAGE_TYPE=minio` | — | Bucket name (created automatically on startup if missing) |
| `STRIPE_SECRET_KEY` | Yes, for billing | — | Stripe secret key (`sk_test_...` / `sk_live_...`) |
| `STRIPE_WEBHOOK_SECRET` | Yes, for billing | — | Signing secret for the `/api/v1/webhooks/stripe` endpoint |
| `STRIPE_SUCCESS_URL` | Yes, for billing | — | Where Stripe Checkout redirects on success (e.g. `http://localhost:8080/payment/success`) |
| `STRIPE_CANCEL_URL` | Yes, for billing | — | Where Stripe Checkout redirects on cancellation |
| `SESSION_COOKIE_SECURE` | No | `true` | Set to `false` only for local HTTP development of the dashboard; must be `true` (or omitted) wherever the app is served over HTTPS |

> 🔒 **Security note:** `ADMIN_USERNAME` and `ADMIN_PASSWORD` must be explicitly configured. The application does not provide a default admin username or password.

See `.env.example` for a ready-to-copy template with all of the above.

## Authentication

There is no single login system - three independent Spring Security filter chains cover three different kinds of caller, and they don't share sessions, tokens, or trust:

| Chain | Covers | Mechanism | Who uses it |
|---|---|---|---|
| API key | `/api/v1/documents/**`, `/api/v1/me/**`, `/api/v1/billing/**` | `X-API-KEY` header, stateless | Machine clients integrating with the API |
| Dashboard session | `/dashboard/**` | Session cookie, obtained by logging in with an API key | A human using the client dashboard in a browser |
| Admin JWT | `/api/v1/admin/**`, `/admin/**`, Swagger UI, actuator endpoints | `Authorization: Bearer <jwt>`, stateless | Admin tooling / scripts |

- A dashboard session is established by exchanging a valid API key for a cookie (see [Client Dashboard](#client-dashboard)) — it is not usable against `/api/v1/**` endpoints, and an API key is not usable against `/dashboard/**`. Likewise, the admin JWT chain has no browser-friendly login page; a JWT is obtained via `POST /api/v1/auth/login` and must be attached to every request as a bearer token (see [Admin Panel](#admin-panel) for what this means in practice).

## API Usage

All `/api/v1/documents/**`, `/api/v1/me/**` and `/api/v1/billing/**` endpoints require an `X-API-KEY` header.

### Registering a new client

Client registration itself requires an admin JWT (see below for how to obtain one):

```bash
curl -X POST http://localhost:8080/api/v1/admin/clients \
  -H "Authorization: Bearer <admin-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"name": "Acme Inc."}'
```

Response:
```json
{
  "clientId": "b3f1...",
  "companyName": "Acme Inc.",
  "rawApiKey": "save-this-key-it-will-not-be-shown-again",
  "webhookSecret": "used-to-verify-webhook-signatures"
}
```

> ⚠️ `rawApiKey` is shown only once in this response — the server only stores its hash. If you lose it, issue a new one (see below) rather than re-registering.

New clients start on the `FREE` plan (100 requests/month, 5 requests/minute). `PRO` (1,000/month, 60/min) and `ENTERPRISE` (10,000/month, 500/min) are available via [Billing](#billing).

### Managing API keys

A client can hold several active keys at once (e.g. one per environment or integration).

**Issue an additional key for yourself:**
```bash
curl -X POST http://localhost:8080/api/v1/me/keys \
  -H "X-API-KEY: <rawApiKey>" \
  -H "Content-Type: application/json" \
  -d '{"label": "Production server"}'
```

**List your keys:**
```bash
curl http://localhost:8080/api/v1/me/keys \
  -H "X-API-KEY: <rawApiKey>"
```

**Revoke a key:**
```bash
curl -X DELETE http://localhost:8080/api/v1/me/keys/{keyId} \
  -H "X-API-KEY: <rawApiKey>"
```

Revoking a client's *last* active key is rejected (`409 Conflict`) - issue a replacement first.

An admin can also issue a key on a client's behalf:
```bash
curl -X POST http://localhost:8080/api/v1/admin/clients/{clientId}/keys \
  -H "Authorization: Bearer <admin-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"label": "Issued by support"}'
```

### Uploading a document

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "X-API-KEY: <rawApiKey>" \
  -F "file=@invoice.pdf" \
  -F "callbackUrl=https://your-system.com/webhooks/docflow"
```

`callbackUrl` is optional; if provided, a signed webhook is sent once processing completes.

Response headers include the current rate-limit/quota status:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 97
```

### Batch upload

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload/batch \
  -H "X-API-KEY: <rawApiKey>" \
  -F "files=@invoice1.pdf" \
  -F "files=@invoice2.png"
```

### Listing documents (paginated)

```bash
curl "http://localhost:8080/api/v1/documents?page=0&size=20&sort=uploadedAt,desc" \
  -H "X-API-KEY: <rawApiKey>"
```

### Getting a single document's detail

```bash
curl http://localhost:8080/api/v1/documents/{id} \
  -H "X-API-KEY: <rawApiKey>"
```

> Note: This endpoint only returns documents belonging to the requesting client; providing another client's document ID returns a `404`.

### Checking your usage

```bash
curl http://localhost:8080/api/v1/me/usage \
  -H "X-API-KEY: <rawApiKey>"
```

### Supported file types and limits

- Formats: `image/png`, `image/jpeg`, `application/pdf` (detected from file content via Apache Tika, not just the extension)
- Max file size: 10 MB per file, 50 MB per request (enforced both by Spring's multipart limits and by the application's own validation)

## Client Dashboard

A server-rendered dashboard at `http://localhost:8080/dashboard` lets a client manage their account in a browser, authenticated by session cookie rather than an API key header on every request.

**Logging in** (`GET /dashboard/login`): enter any one of your active API keys. This exchanges it for a session cookie - the key itself is never stored in the browser.

Once logged in:

| Page | URL | What it does |
|---|---|---|
| Usage overview | `/dashboard` | Current plan, monthly quota used, rate limit |
| API Keys | `/dashboard/keys` | List, create, and revoke API keys (a thin, session-authenticated layer over the same logic as `/api/v1/me/keys`) |
| Billing | `/dashboard/billing` | View current plan, start a Stripe Checkout upgrade, or open the Stripe Customer Portal to manage an existing subscription |

Logging out (button in the header on every page) invalidates the session server-side; the same cookie will not work again afterward.

## Billing

Billing is handled entirely through Stripe:

- **Upgrading**: `POST /api/v1/billing/upgrade?targetPlan=PRO` (or `ENTERPRISE`) creates a Stripe Checkout session and returns its URL (`{"checkoutUrl": "..."}`) — or, from the dashboard, clicking "Upgrade" does the same and redirects the browser directly.
- **Managing an existing subscription**: `POST /api/v1/billing/portal` returns a Stripe Customer Portal URL (`{"portalUrl": "..."}`), where a client can update payment methods, change plans, or cancel.
- **Staying in sync**: `POST /api/v1/webhooks/stripe` receives Stripe's webhook events (signature-verified using `STRIPE_WEBHOOK_SECRET`) and updates the client's plan as their subscription changes — this is what actually applies an upgrade/downgrade/cancellation, not the redirect back from Checkout.
- After Checkout, Stripe redirects the browser to `STRIPE_SUCCESS_URL` or `STRIPE_CANCEL_URL` (by default, `/payment/success` and `/payment/cancel`), which render a plain confirmation page with a link back into the dashboard. The plan change itself may land a moment after that page renders, since it depends on the webhook above.

## Admin Panel

Admin access (both the `/admin/**` HTML views and the `/api/v1/admin/**` REST endpoints) requires a JWT, obtained via:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "'$ADMIN_USERNAME'", "password": "'$ADMIN_PASSWORD'"}'
```

Response: `{"token": "<jwt>"}`. Use it as `Authorization: Bearer <jwt>` on subsequent requests.

The dashboard at `/admin/dashboard` (lists all clients' documents and processing status, paginated) is server-rendered, but the chain protecting it only accepts a bearer token, not a cookie or a login form — there is currently no browser-friendly way to open it directly by navigating to the URL. In practice this means using a REST client (curl, Postman, a browser extension that can set request headers, etc.) rather than a plain address-bar visit. See [Known Limitations](#known-limitations).


## Webhooks

When a document finishes processing and a `callbackUrl` was provided, a `POST` request is sent in the following format:

```
POST {callbackUrl}
X-DocFlow-Signature: <hmac-sha256-signature>
Content-Type: application/json

{ "documentId": "...", "status": "PROCESSED", ... }
```

You should verify the `X-DocFlow-Signature` header using the `webhookSecret` you received at client registration. If delivery fails (e.g. the target server is unreachable), the system retries a few times with increasing delays; if all attempts fail, the error is logged.

Webhook URLs pointing to `localhost`, private IP ranges (`10.x`, `192.168.x`, etc.), or cloud metadata addresses (`169.254.169.254`) are rejected (SSRF protection).

## Storage

The `STORAGE_TYPE` environment variable can be set to `minio` (default) or `local`. When set to `local`, files are saved under `app.upload.dir` (default `./uploads`).

Both storage backends have a scheduled cleanup job that removes files older than a configured age.

## Testing

```bash
./mvnw test
```

Integration tests use Testcontainers (real Postgres and Kafka containers) — Docker must be running to execute them. There is no MinIO container in the test setup: every test mocks `S3Client`/`StorageService`, so nothing in the suite talks to real object storage.

Coverage includes an end-to-end authentication suite (`AuthIntegrationTest`) that drives all three filter chains above over real HTTP — API key issuance/revocation/suspension, the full dashboard cookie+CSRF login/logout cycle, and admin JWT issuance/rejection — without mocking the client-lookup service, so it exercises the real database-backed authentication logic rather than asserting against stubbed responses.

## Known Limitations

This project is under active development. Currently known gaps:

- The initial admin user is seeded from `ADMIN_USERNAME`/`ADMIN_PASSWORD` with no forced password change on first login.
- `/admin/dashboard` has no browser-friendly login; it only accepts a bearer token, which makes it awkward to actually browse (see [Admin Panel](#admin-panel)).
- Uploaded files are not scanned for viruses/malware.

Contributions and feedback are welcome.
 
