# Campus Event Discovery & RSVP System

Project setup, branded frontend, and MongoDB domain models for the University of Ruhuna, Faculty of Engineering.

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
Only `GET /api/v1/health` and `GET /api/v1/health/database` are public; other backend requests are denied.
Authentication, events, and dashboards are reserved for later stages.

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
# Edit .env for your database, then export its values:
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
the existing record. `passwordHash` is excluded from JSON serialization. Authentication,
registration flows, and event APIs are not implemented in this stage.

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

Live tests create and drop their own uniquely named `campus_stage3_test_*` database.
Use a local test server with permission to create/drop databases. They verify
connectivity, all five repositories, ObjectId references, UTC auditing, indexes,
duplicate rejection, and idempotent club seeding. Build output is
written to `frontend/dist/` and `backend/target/` (ignored by Git).
