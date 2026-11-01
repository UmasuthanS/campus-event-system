# Campus Event Discovery & RSVP System

Project setup, branded frontend, MongoDB domain models, and role-based authentication for the University of Ruhuna, Faculty of Engineering.

- `frontend/`: React, Vite, JavaScript, Tailwind CSS
- `backend/`: Java 21, Spring Boot, Maven, Web, Security, Validation, Data MongoDB

## Prerequisites

Install Node.js 22.12+ (Node 24 LTS recommended) with npm and JDK 21.
Set `JAVA_HOME` to your JDK 21 installation. On macOS:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

The backend includes Maven Wrapper (`mvnw`, or `mvnw.cmd` on Windows).
The first build requires internet access to download dependencies and Maven.

## Start locally

From the repository root, start the backend:

```sh
cd backend
# Generate a local signing key once per shell; use your saved .env key for repeat starts.
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

In another terminal, start the frontend:

```sh
cd frontend
npm ci
npm run dev
```

Open the URL printed by Vite (normally `http://127.0.0.1:5173`).
Both development servers bind to loopback. Vite proxies `/api` to the backend
at `http://127.0.0.1:8080`; update the proxy if you change the backend port.

```sh
curl --fail http://127.0.0.1:8080/api/v1/health
# {"status":"UP"}
```

Health reports application liveness and does not query MongoDB. MongoDB is
optional for default-profile startup, liveness verification, and the default tests. The Mongo driver
may log background connection failures when no database is running.
Health endpoints and the registration/login POST endpoints are public.
`GET /api/v1/users/me` requires a valid Bearer token. Public discovery GET endpoints
return clubs and published events. Management CRUD and full dashboards are reserved
for later stages.

## Local MongoDB setup

With Docker installed, start an isolated local development database (no credentials,
loopback access only). The named volume retains data when the container stops:

```sh
docker run -d --name campus-mongodb -p 127.0.0.1:27017:27017 -v campus-mongodb-data:/data/db mongo:7.0
# Later: docker stop campus-mongodb / docker start campus-mongodb
```

An existing local MongoDB installation or a MongoDB Atlas database can also be used.
Configure the connection through `MONGODB_URI`; keep credentials in your local
ignored `.env` or shell environment, never in source control.

From the repository root:

```sh
cd backend
cp .env.example .env
# Edit .env for your database and set JWT_SECRET using openssl rand -base64 32.
# Then export its values:
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

Spring Boot does not load `.env` automatically. On Windows, set the variables in
your shell before running `mvnw.cmd`. The example activates the `dev` profile,
creates document indexes, and inserts IEEE, ComES, and AIESEC if missing. Repeated
startup preserves existing club records. MongoDB must be reachable in this profile;
index or seed failures stop startup. No users or passwords are seeded.

| Variable | Default / purpose |
| --- | --- |
| `MONGODB_URI` | Local `campus_events` database, 2-second server-selection and connection timeouts |
| `SERVER_PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | No profile by default; set `dev` for local indexes and seeding |
| `MONGODB_AUTO_INDEX_CREATION` | `false` by default, `true` in `dev` |
| `SEED_CLUBS` | `true` in `dev`; set `false` to disable club seeding |

Without `dev`, seeding is disabled even if `SEED_CLUBS=true`. Indexes are declared
on the models and created when auto-index creation is enabled. Existing duplicate
records must be resolved before creating unique indexes. These are development
settings; production database migrations and access controls are outside this stage.

Verify database connectivity:

```sh
curl --fail http://127.0.0.1:8080/api/v1/health/database
# HTTP 200: {"status":"UP"}
# HTTP 503: {"status":"DOWN"} when MongoDB is unavailable
```

This endpoint runs MongoDB `ping` through `MongoTemplate` and exposes no connection
details. Liveness at `/api/v1/health` remains independent of database availability.
To run without MongoDB, use a shell without the exported `dev` profile and leave
auto-index creation disabled.

## Database models

All document IDs and reference IDs are BSON `ObjectId` values. References are
stored as IDs without `DBRef`; MongoDB does not enforce referential integrity.
`createdAt` and `updatedAt` are audited UTC `Instant` values stored as BSON dates
with millisecond precision. Event and review timestamps also use `Instant`.

| Collection | Model / indexes |
| --- | --- |
| `users` | User; unique registration number and email; roles STUDENT, CLUB_ADMIN, SUPER_ADMIN |
| `clubs` | Club; unique code for idempotent seeding |
| `events` | Event; start date and compound club/start-date indexes; DRAFT, PUBLISHED, CANCELLED, COMPLETED |
| `event_registrations` | EventRegistration; unique event/user pair and user/creation-date lookup; CONFIRMED, CANCELLED |
| `organizer_applications` | OrganizerApplication; user/club and club/status lookups; PENDING, APPROVED, REJECTED |

A cancelled RSVP retains its event/user pair; future RSVP operations should update
the existing record. `passwordHash` is excluded from JSON serialization. Event management APIs are not implemented.

## Authentication

Set `JWT_SECRET` to at least 32 cryptographically random bytes encoded as Base64
(`openssl rand -base64 32`). Empty, malformed, or weak keys stop startup. Keep the
key in the ignored backend `.env` or your shell. `JWT_TTL_SECONDS` defaults to 900
and accepts 60–900 seconds; `JWT_ISSUER` defaults to `campus-event-system`.
Tests generate their own ephemeral signing key and do not need `JWT_SECRET`.

| Endpoint | Result |
| --- | --- |
| `POST /api/v1/auth/register` | 201 with access token and public account profile |
| `POST /api/v1/auth/login` | 200 with access token and public account profile |
| `GET /api/v1/users/me` | 200 with current account profile; Bearer token required |

Registration JSON fields are `fullName`, `registrationNumber`, `email`, and
`password`. Login uses `email` and `password`. Registration numbers accept
`EG/YYYY/NNN` or `EG/YYYY/NNNN` (case-insensitive input). Names require 2–100
characters. Passwords require 12–72 characters, a letter, a number, and at most
72 UTF-8 bytes (BCrypt's limit). Passwords are never trimmed; only BCrypt hashes
are stored. Email and registration number are normalized before duplicate checks.
User unique indexes are ensured lazily on registration, including outside `dev`,
so concurrent duplicates are rejected without requiring MongoDB during startup.

Registration always assigns STUDENT; unsupported fields such as `role` are rejected.
Existing STUDENT, CLUB_ADMIN, and SUPER_ADMIN accounts use the same login endpoint. JWT validation
checks HS256 signatures, issuer, audience, and expiry. `/users/me` also checks the
current database record and role. Every authenticated request compares its role claim
with the stored role; stale privileges are denied immediately.

Errors use `{status, code, message, errors}`. Statuses include 400 for invalid input,
401 for invalid credentials/tokens, 403 for forbidden access, 409 for duplicates,
and 503 for database unavailability. Credentials and connection details are omitted.

Use the existing frontend Register/Login pages. Successful authentication opens the matching
`/student/dashboard`, `/club-admin/dashboard`, or `/super-admin/dashboard` placeholder.
Role-mismatched routes show `/unauthorized` (403). JWTs remain in React memory;
no credentials are saved in browser storage or cookies. Reloading requires login.
Expiry and HTTP 401 clear the session. Logout clears browser memory; issued JWTs
expire naturally within their short lifetime. No refresh tokens are implemented.
Vite's existing `/api` proxy keeps frontend requests on the same origin. API security
is stateless and uses explicit Authorization headers; CSRF is disabled because
no authentication cookies are used. Use HTTPS for any non-local deployment.

## Role-based access

| API | Access |
| --- | --- |
| `GET /api/v1/events`, `GET /api/v1/clubs` | Public, read-only discovery; events include PUBLISHED records only |
| `GET /api/v1/users/me` | Any of the three roles |
| `/api/v1/student/**` | STUDENT only |
| `/api/v1/club-admin/**` | CLUB_ADMIN or SUPER_ADMIN |
| `/api/v1/super-admin/**` | SUPER_ADMIN only |

Each protected area exposes only `GET .../access` for verifying permissions at this
stage. `GET /api/v1/club-admin/clubs/{clubId}/access` additionally checks ownership.
Club Admins must appear in that club's `adminIds` (BSON ObjectIds); Super Admins
can access any existing club. Upcoming club management methods must reuse
`@PreAuthorize("@clubAuthorization.canManage(authentication, #clubId)")` in addition
to the protected URL rules. There are no public role changes, admin registration,
club ownership changes, or management CRUD endpoints. Unauthenticated protected
requests return 401; insufficient permissions return 403. Database failures fail
closed with 503. Deleting an account invalidates its tokens.

Frontend event/club pages retain their clearly labeled demo listings; connecting
them to discovery data is outside this stage.

### Development-only Super Admin bootstrap

Use the ignored backend `.env` with `SPRING_PROFILES_ACTIVE=dev`. Set all of:

```dotenv
BOOTSTRAP_SUPER_ADMIN=true
BOOTSTRAP_SUPER_ADMIN_NAME=
BOOTSTRAP_SUPER_ADMIN_REGISTRATION_NUMBER=
BOOTSTRAP_SUPER_ADMIN_EMAIL=
BOOTSTRAP_SUPER_ADMIN_PASSWORD=
```

Fill the blank values locally using the same registration/password validation
rules documented above, export the `.env`, then start the backend. Missing or
invalid values stop startup without exposing credentials. The bootstrap creates
only a SUPER_ADMIN with a BCrypt hash. Existing matching Super Admins are left
unchanged; conflicting student/admin identities stop startup and are never
promoted or overwritten. It is safe to rerun with the same identity.

After the first successful startup, set `BOOTSTRAP_SUPER_ADMIN=false` and remove
the bootstrap credentials from the environment. The process is disabled by default
and unavailable outside `dev`, even when its flag is enabled. Club Admin assignment
and club ownership provisioning belong to the later approval/management stages.

## Verify builds

```sh
cd frontend
npm ci
npm run build
cd ../backend
./mvnw clean verify
```

Default tests use an unavailable MongoDB address and verify liveness, HTTP 503
for database health, denial of other routes, and seed profile isolation. Live
persistence tests are skipped unless `MONGODB_TEST_URI` is supplied:

```sh
cd backend
MONGODB_TEST_URI='mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=2000&connectTimeoutMS=2000' ./mvnw clean verify
```

Live tests create and drop their own uniquely named `campus_stage3_test_*` and
`campus_auth_test_*` and `campus_rbac_test_*` databases.
Use a local test server with permission to create/drop databases. They verify
connectivity, all five repositories, ObjectId references, UTC auditing, indexes,
duplicate rejection, idempotent club seeding, registration, BCrypt hashing, login,
role/ownership restrictions, bootstrap safeguards, validation, and token expiry. JWT unit tests also run without MongoDB. Build output is
written to `frontend/dist/` and `backend/target/` (ignored by Git).
