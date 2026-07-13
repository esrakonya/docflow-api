# Docflow API

Docflow API is a REST API that extracts structured data (vendor name, invoice number, amount, VAT, line items, etc.) from invoices and other documents using AI (Anthropic Claude or Google Gemini). Processing after upload is handled asynchronously via Kafka, and the result is optionally reported back through a signed webhook.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Environment Variables](#environment-variables)
- [API Usage](#api-usage)
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
-  API key-based authentication + per-minute rate limiting (Redis)
-  Monthly usage quotas (free/pro tier)
-  Swappable file storage between MinIO (S3-compatible) and local disk
-  A simple, server-rendered (Thymeleaf) admin dashboard

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

On failure, Kafka's `@RetryableTopic` mechanism retries a few times; as a last resort, the message is routed to a dead-letter topic (`document-uploaded-dlt`).

### Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.5 |
| Database | PostgreSQL + Flyway migrations |
| Messaging | Apache Kafka |
| Cache / Rate limiting | Redis |
| File storage | MinIO (S3-compatible) or local disk |
| AI | Spring AI (Anthropic Claude / Google Gemini) |
| Admin UI | Thymeleaf |
| API documentation | springdoc-openapi (Swagger UI) |

## Requirements

- **Java 21** (JDK, for building and running)
- **Docker & Docker Compose** (to spin up Postgres, Kafka, Redis, and MinIO)
- An **Anthropic** and/or **Google Gemini** API key

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

### 2. Set the required environment variables

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export GEMINI_API_KEY=...
```

(You don't need to provide both — whichever is selected in `docflow.ai.provider` is the one that's used; see [Environment Variables](#environment-variables).)

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080` by default.

### 4. Verify

```bash
curl http://localhost:8080/actuator/health
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Environment Variables

| Variable | Required? | Default | Description |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | Yes, if `docflow.ai.provider=anthropic` | — | Anthropic Claude API key |
| `GEMINI_API_KEY` | Yes, if `docflow.ai.provider=google` | — | Google Gemini API key |
| `ADMIN_USERNAME` | No | `admin` | Username for the admin panel / client registration endpoint |
| `ADMIN_PASSWORD` | No (**but change this in production**) | `admin123` | Admin panel password |

> 🔒 **Production note:** Make sure to change `ADMIN_PASSWORD`. If you run with the default value, the application will log a security warning on startup.

Also note that MinIO and database connection details in `src/main/resources/application.yaml` are currently hardcoded literal values — if you're running in a different environment, you'll need to edit this file (or use command-line overrides such as `--spring.datasource.password=...`).

## API Usage

All `/api/v1/documents/**` endpoints require authentication via an `X-API-KEY` header.

### Registering a new client (API key)

```bash
curl -X POST http://localhost:8080/api/v1/admin/clients \
  -u admin:admin123 \
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

> ⚠️ `rawApiKey` is shown only once in this response — the server only stores its hash. If you lose it, you'll need to register a new client.

### Uploading a document

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "X-API-KEY: <rawApiKey>" \
  -F "file=@invoice.pdf" \
  -F "callbackUrl=https://your-system.com/webhooks/docflow"
```

`callbackUrl` is optional; if provided, a signed webhook is sent once processing completes.

Response headers include the current rate-limit status:
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

### Supported file types and limits

- Formats: `image/png`, `image/jpeg`, `application/pdf`
- Max file size: 10 MB (per file), 50 MB (total request size)

## Admin Panel

A simple dashboard, protected by HTTP Basic Auth (`ADMIN_USERNAME` / `ADMIN_PASSWORD`), is available at `http://localhost:8080/admin/dashboard`. It lists documents and their processing status across all clients, paginated.

## Webhooks

When a document finishes processing and a `callbackUrl` was provided, a `POST` request is sent in the following format:

```
POST {callbackUrl}
X-Invox-Signature: <hmac-sha256-signature>
Content-Type: application/json

{ "documentId": "...", "status": "PROCESSED", ... }
```

You should verify the `X-Invox-Signature` header using the `webhookSecret` you received at client registration. If delivery fails (e.g. the target server is unreachable), the system retries a few times with increasing delays; if all attempts fail, the error is logged.

Webhook URLs pointing to `localhost`, private IP ranges (`10.x`, `192.168.x`, etc.), or cloud metadata addresses (`169.254.169.254`) are rejected (SSRF protection).

## Storage

The `storage.type` setting in `application.yaml` can be set to `minio` (default) or `local`. When set to `local`, files are saved under `app.upload.dir` (default `./uploads`).

Both storage backends have a scheduled cleanup job that removes files older than a configured age.

## Testing

```bash
./mvnw test
```

Integration tests use Testcontainers with real Postgres/Kafka containers — Docker must be running to execute them.

## Known Limitations

This project is under active development. Currently known gaps:

- Admin user management is not database-backed; it runs on a single in-memory user.
- No payment/plan-upgrade flow (e.g. Stripe) is integrated; `free`/`pro` tiers currently only exist as quota limits.
- Uploaded files are not scanned for viruses/malware.

Contributions and feedback are welcome.
