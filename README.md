# TicketDesk API

[![Verify](https://github.com/cenkerenozbek/ticketdesk-api/actions/workflows/verify.yml/badge.svg)](https://github.com/cenkerenozbek/ticketdesk-api/actions/workflows/verify.yml)

A REST API for support tickets. Users manage their own requests; support agents handle status changes, comments and reporting.

**Stack:** Java 21 · Spring Boot 4.1.1 · Spring Security · JPA/Hibernate · PostgreSQL 17 · Flyway · Maven · Docker

## Features

- Role and ownership checks with HTTP Basic authentication and BCrypt passwords.
- `OPEN → IN_PROGRESS → RESOLVED` workflow with transactional status history and row locking.
- Comments, ticket filtering, sorting and pagination.
- Ticket counts by status using SQL `GROUP BY`.

## Run locally

Requires Docker and Docker Compose.

```sh
git clone https://github.com/cenkerenozbek/ticketdesk-api.git
cd ticketdesk-api
docker compose up --build
```

API: `http://localhost:8080/api/tickets`. Migrations and demo data load automatically.
If the default ports are busy, use `DB_PORT=55433 API_PORT=18080 docker compose up --build` and access the API on port `18080`.

All demo accounts use the password `demo-password`:

| Email | Role |
| --- | --- |
| user@ticketdesk.local | USER |
| other@ticketdesk.local | USER |
| agent@ticketdesk.local | AGENT |

Compose is configured for local use. Remote deployment requires HTTPS and separate credentials.
Data persists in a named volume; `docker compose down` keeps it, while `docker compose down -v` deletes it.

## Example request

```sh
curl -i -u user@ticketdesk.local:demo-password \
  -H 'Content-Type: application/json' \
  -d '{"title":"Wi-Fi unavailable","description":"Cannot connect from the library.","priority":"HIGH"}' \
  http://localhost:8080/api/tickets
```

Returns `201 Created` with the ticket ID, owner and initial `OPEN` status.

## Endpoints

Owners can access their own tickets; agents can access all tickets.

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/tickets` | USER |
| GET | `/api/tickets` | USER: own tickets; AGENT: all |
| GET | `/api/tickets/{id}` | Owner or AGENT |
| PATCH | `/api/tickets/{id}/status` | AGENT |
| GET | `/api/tickets/{id}/history` | Owner or AGENT |
| POST | `/api/tickets/{id}/comments` | Owner or AGENT |
| GET | `/api/tickets/{id}/comments` | Owner or AGENT |
| GET | `/api/reports/tickets-by-status` | AGENT |

Ticket lists accept `status`, `priority`, `page`, `size` and `sort` parameters.
Pages start at `0`; page size defaults to `20` and is limited to `100`.

## Tests

Requires Java 21. The Maven Wrapper downloads Maven automatically.

```sh
# Unit and HTTP/security tests
./mvnw verify

# Include integration tests against a separate PostgreSQL database
# Requires Docker; test database uses port 55432
docker compose -f compose.test.yaml up -d --wait
./mvnw verify -Ppostgres-it
docker compose -f compose.test.yaml down
```

**56 automated tests** cover access control, validation, persistence, reporting, transactions and concurrent status changes.
CI also builds the Docker image and verifies data survives container recreation.

## Further reading

- [Detailed setup, API examples and design decisions (Turkish)](docs/development-guide.md)
- [Validation report (Turkish)](VALIDATION.md)
- [Acceptance checks (Turkish)](docs/acceptance.md)
- [SQL examples](docs/sql-practice.sql)
