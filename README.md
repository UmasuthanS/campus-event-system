# Campus Event Discovery & RSVP System

A full-stack campus event portal for the University of Ruhuna, Faculty of Engineering.
Students discover events, reserve participant seats, and apply to organizing teams.
Club administrators manage their clubs and events; Super Admins manage platform access.
All pages use the university maroon, gold, and white theme.

## Technologies and features

- React 19, Vite, JavaScript, Tailwind CSS, React Router.
- FullCalendar month/week/list views with Luxon and Asia/Colombo display time.
- Java 21, Spring Boot 3.5, Maven Wrapper, Spring Security, BCrypt, signed JWTs.
- Spring Data MongoDB; BSON ObjectId references and UTC timestamps.
- Student registration/login, role and club ownership checks, and role-specific dashboards.
- Active club profiles; soft deactivation and multiple administrator assignments.
- Draft creation, editing, publication, cancellation, and completion of events.
- Published event discovery with title, club, category, period, and date-range filters.
- Capacity-limited RSVP, eligible cancellation, registration history, and private participant lists.
- Organizer applications, approval/rejection, withdrawal, and approved organizing team lists.
- Super Admin statistics, user directory, club management, and access management.

## Folder structure

```text
assets/                         Original university branding and project instructions
frontend/
  public/assets/                University logo
  src/auth/                     In-memory authentication and route protection
  src/components/               Shared navigation, cards, forms, and management controls
  src/pages/                    Discovery, calendar, account, management, and dashboard pages
  src/dashboards/                Dashboard panels
  src/events/                   Shared discovery queries and timezone formatting
  src/registrations/            RSVP and participant components
  src/organizers/               Application and review components
backend/
  config/local.properties.example   Template for ignored persistent JWT configuration
  src/main/java/lk/ac/ruh/eng/campusevents/
    auth/, authorization/, config/  Authentication, role/ownership guards, development seeds
    domain/, repository/            MongoDB models and repositories
    club/, event/, registration/, organizer/, dashboard/, health/, error/
  src/main/resources/           Default and development configuration
  src/test/                     Unit, security, persistence, and HTTP integration tests
```

## Installation and local startup

Requires JDK 21, Node.js 22.12+ with npm, and reachable MongoDB. Maven Wrapper is
included; initial Maven/npm dependency downloads require network access.
Commands below run from the `campus-event-system` repository directory.

On macOS, select an installed JDK 21:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

### 1. MongoDB

Use an existing local MongoDB/Atlas instance, or create a local Docker instance:

```sh
docker run -d --name campus-events-mongo \
  -p 127.0.0.1:27017:27017 \
  -v campus-events-mongo-data:/data/db mongo:7.0
# On subsequent starts, reuse it:
# docker start campus-events-mongo
```

Use only one MongoDB server on port 27017. The Docker example is for loopback-only
development; the named volume preserves records. A replica set is not required.

### 2. Backend configuration and JWT_SECRET

```sh
cd backend
[ -f .env ] || cp .env.example .env
mkdir -p config
if [ ! -f config/local.properties ]; then
  (umask 077; printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)" > config/local.properties)
fi
```

Keep `.env` and `config/local.properties` local; both are Git-ignored. Existing files
are preserved by these commands. Edit `.env` for your MongoDB URI and local settings.
The template enables `dev`, indexes, and idempotent IEEE, ComES, and AIESEC club seeding.
It does not create accounts unless explicitly enabled below.

`config/local.properties` uses Java properties syntax: `JWT_SECRET=value`, without
quotes or `export`. If you copied the properties template, replace its empty key
with a Base64-encoded key generated from **at least 32 cryptographically random bytes**.
Never commit the generated value. Empty, malformed, or short keys fail startup.
Spring Boot imports this file automatically when launched from `backend`.

`.env` uses shell syntax; quote values containing spaces or special characters.
Spring Boot does **not** load `.env` automatically. Load it in the backend terminal:

```sh
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

On Windows, set these environment variables in your shell and use `mvnw.cmd`.
For a restart, press Ctrl+C in the existing backend terminal, reload `.env`, and
run `./mvnw spring-boot:run` again. The Maven goal includes `:run`.

| Setting | Meaning |
| --- | --- |
| `MONGODB_URI` | Defaults to local `campus_events`, with 2-second connection/selection timeouts |
| `SERVER_PORT` | Defaults to 8080; update the Vite proxy if changed |
| `SPRING_PROFILES_ACTIVE=dev` | Enables opt-in development bootstrap/seeds |
| `MONGODB_AUTO_INDEX_CREATION` | Default false; true in dev |
| `SEED_CLUBS` | Default true in dev; inserts missing initial clubs only |
| `JWT_SECRET` | Optional environment override of the persistent local key |
| `JWT_ISSUER` | Defaults to `campus-event-system` |
| `JWT_TTL_SECONDS` | Defaults to 900; accepted range 60–900 seconds |

An exported `JWT_SECRET`, including an empty value, overrides the persistent key.
Use `unset JWT_SECRET` to remove a stale override before loading your configuration.
MongoDB must be reachable in dev because indexes/seeds run during startup. Default
profile liveness can run without MongoDB when auto-index creation is disabled.

### 3. Frontend

In another terminal, from the repository directory:

```sh
cd frontend
npm ci
npm run dev
```

Open the Vite URL, normally **http://127.0.0.1:5173**. The frontend proxies `/api`
to **http://127.0.0.1:8080** through `frontend/vite.config.js`. Both servers bind to
loopback. Keep both running; this avoids CORS and separate browser API configuration.

```sh
curl --fail http://127.0.0.1:8080/api/v1/health
curl --fail http://127.0.0.1:8080/api/v1/health/database
```

Liveness does not query MongoDB. Database health uses `MongoTemplate` ping and
returns 200 UP or 503 DOWN without connection details.

## Accounts and roles

| Role | Permissions |
| --- | --- |
| Public | Active club profiles, published active-club events, calendar, approved team names/roles |
| STUDENT | Own profile, RSVP/cancellation, organizer applications/withdrawal, student dashboard |
| CLUB_ADMIN | Assigned club profiles, event management, private participants and application review |
| SUPER_ADMIN | All clubs/events, administrator assignment/revocation, user directory, platform statistics |

All roles sign in at `/login`; public `/register` creates STUDENT accounts only.
Registration numbers must match `EG/YYYY/NNN` or `EG/YYYY/NNNN`. Names require
2–100 characters. Passwords require 12–72 characters, a letter and number, and at
most 72 UTF-8 bytes. Email and registration number are normalized and uniquely indexed.
Passwords are BCrypt-hashed and never returned in DTOs.

### Create the first Super Admin

In ignored `backend/.env`, enable `SPRING_PROFILES_ACTIVE=dev` and fill these with
your own values; no account credentials are supplied in Git:

```dotenv
BOOTSTRAP_SUPER_ADMIN=true
BOOTSTRAP_SUPER_ADMIN_NAME=
BOOTSTRAP_SUPER_ADMIN_REGISTRATION_NUMBER=
BOOTSTRAP_SUPER_ADMIN_EMAIL=
BOOTSTRAP_SUPER_ADMIN_PASSWORD=
```

Use an unused email/registration number and the validation rules above. Reload
`.env` and restart the backend. Bootstrap creates the account directly; do not
register it through the student form first. Matching Super Admin identities are
left unchanged. Conflicting student/admin identities fail startup and are never
promoted or overwritten. Then sign in through the normal Login page.

After creation, set `BOOTSTRAP_SUPER_ADMIN=false`, remove the credential values,
and unset those credential variables in the terminal or open a fresh terminal.
Accounts persist in MongoDB; disabling bootstrap does not delete them. Bootstrap
is disabled by default and cannot run outside dev.

### Club administrator assignments

A Super Admin opens `/super-admin/club-administrators`, selects an existing account
by email, and assigns it to a club. Assignment promotes a student to CLUB_ADMIN;
they must sign in again after the role change. Multiple admins per club are allowed.
Revoking an assignment immediately removes that club's access while retaining other
assignments and the account's CLUB_ADMIN role. There is no public admin registration.

Optional dev-only demo accounts use `SEED_CLUB_ADMINS=true` and the environment
prefixes `DEMO_IEEE_ADMIN_`, `DEMO_COMES_ADMIN_`, or `DEMO_AIESEC_ADMIN_`, each with
`NAME`, `REGISTRATION_NUMBER`, `EMAIL`, and `PASSWORD`. Leave all four blank to skip
a club; partial identities fail startup. Use unused identities. Disable seeding and
remove credentials after setup. Existing passwords/access are not reset by reruns.

## Demo workflow

1. Start MongoDB, the backend in dev, and Vite. Bootstrap your own Super Admin.
2. Register separate student accounts for a participant, organizer applicant, and
   prospective club admin. Sign in as Super Admin and assign the admin to IEEE.
3. Sign in as that Club Admin. Create a future event with venue, category, capacity,
   registration deadline, and (optionally) organizer capacity/application deadline.
   Save the draft, edit it, then publish it. Drafts remain private.
4. Browse the homepage, `/clubs`, `/events`, and `/calendar`. Search/filter by club,
   title, category, or date. Calendar clicks and event cards open event details.
5. As the participant, register for the event and inspect `/student/dashboard` and
   `/student/my-events`. Cancel an eligible RSVP and check remaining seats update.
6. As the organizer applicant, submit preferred role, motivation, and experience.
   Inspect `/student/my-applications`, initially PENDING. As the assigned Club Admin,
   review the application in event management and approve/reject it. Approved members
   appear in the public organizing team. The student can withdraw when eligible.
7. Verify Club Admin event/participant/application totals and Super Admin platform
   statistics, user search, club profiles, and administrator assignments.
8. Try the same management URLs as a student or unassigned admin; protected APIs
   return 403. Anonymous requests to protected APIs return 401.

## Workflow rules and API map

Stored timestamps are UTC; event forms, cards, dashboards, and calendar use
Asia/Colombo. Event states are DRAFT, PUBLISHED, CANCELLED, COMPLETED. Terminal events
cannot be edited/reopened; completion requires the event to have ended. Inactive
clubs remain stored and hide their public profiles/events. Only assigned admins or
Super Admin can manage a club/event, enforced in Spring services and URL security.

RSVP requires a published future event, active club, open registration deadline,
and free participant capacity. Cancellation is allowed before start or when an
event is CANCELLED; COMPLETED events close cancellation. Repeated cancellation is
idempotent. Re-registration reuses the unique event/student history row.

Organizer recruitment is disabled by default. Set `organizerCapacity` (0–1000; 0
turns it off) and `organizingApplicationDeadline` no later than event start. Students
may apply before this deadline; admins may review PENDING applications until start.
Statuses are PENDING, APPROVED, REJECTED, WITHDRAWN. Only approved applications consume
organizer capacity. Eligible pending/approved applications can be withdrawn before
start or when cancelled. Rejected/withdrawn applicants may reapply with new history.

**Role policy:** a student cannot hold an RSVP and a PENDING/APPROVED organizer
application for the same event. Cancel or withdraw before switching. Organizer
approval does not consume participant seats. Shared atomic event revision checks
enforce duplicate prevention, capacity, and role exclusivity under concurrency.
The event ledger is authoritative; registration/application collections are recoverable
history projections with sequence guards against stale writes.

| API group | Main endpoints |
| --- | --- |
| Auth | `POST /api/v1/auth/register`, `/auth/login`; `GET /api/v1/users/me` |
| Discovery | `GET /api/v1/events`, `/events/{id}`, `/events/categories`, `/clubs`, `/clubs/{id}`, `/clubs/{clubId}/events` |
| Club management | `POST /api/v1/super-admin/clubs`; `PUT /api/v1/club-admin/clubs/{id}`; `PATCH /api/v1/super-admin/clubs/{id}/status`; DELETE soft-deactivates |
| Admin access | `PUT /api/v1/super-admin/clubs/{id}/admins` with email; `DELETE .../admins/{userId}` revokes |
| Event management | `POST /api/v1/clubs/{clubId}/events`; `PATCH /api/v1/events/{id}` and `/events/{id}/status`; managed reads under `/api/v1/club-admin/` |
| RSVP | `POST /api/v1/events/{id}/registrations`; `GET /api/v1/users/me/registrations`; `DELETE /api/v1/registrations/{id}`; authorized `GET /api/v1/events/{id}/registrations` |
| Organizing | `POST /api/v1/events/{id}/organizer-applications`; own GET under `/users/me/organizer-applications`; authorized event GET; `PATCH /api/v1/organizer-applications/{id}/status`; DELETE withdraws |
| Approved team | `GET /api/v1/events/{id}/organizing-team` exposes approved names/roles only |
| Dashboards | `GET /api/v1/club-admin/dashboard`, `/api/v1/super-admin/dashboard`, `/api/v1/super-admin/users?search=&page=0` |

All endpoint paths use `/api/v1`. Discovery accepts `search`, `clubId`, `category`,
`period=all|upcoming|past`, `sort=asc|desc`, and UTC/offset `from`/`to` range filters.
Calendar ranges overlap `[from,to)`. Discovery upcoming includes ongoing events by
end time; dashboard upcoming means published events with a future start. Dashboard
registration totals count confirmed rows including past events; platform summaries
include inactive clubs and all event statuses unless labeled otherwise. User search
returns 20 safe profiles per page with `hasNext`.

Errors use `{status, code, message, errors}`: 400 validation, 401 authentication,
403 permissions, 404 missing/private resources, 409 conflicts/duplicates, and 503
unavailable database. Client-supplied roles, owners, and student IDs are rejected.
JWT verification checks signature, expiry, issuer, audience, and subject. Every
protected request checks the stored account role; stale role claims lose privileges.

## Verification

From the repository directory, with Java 21 selected:

```sh
cd frontend
npm ci
npm run build
cd ../backend
./mvnw clean verify
# Include real MongoDB integration, persistence, and concurrency tests:
MONGODB_TEST_URI='mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=2000&connectTimeoutMS=2000' ./mvnw clean verify
```

Without `MONGODB_TEST_URI`, live database tests are **skipped**, not passed. The
remaining tests verify JWT security, profile isolation, and health/error behavior
without a running database. Tests generate a test-only signing key and do not need
your real JWT_SECRET or bootstrap credentials. Database tests create/drop uniquely
named `campus_*_test_*` databases; use a test server with create/drop permissions.
They never select the application's `campus_events` database. Build outputs are
`frontend/dist/` and `backend/target/`, ignored by Git.

### Stage 12 verification — 2026-11-01

- Java 21 `MONGODB_TEST_URI=... ./mvnw -B -ntp clean verify`: **75 tests passed,
  zero failures/errors/skips**, using real local standalone MongoDB. This includes
  JWT/security, bootstrap, club ownership, event discovery, RSVP/application
  concurrency, dashboard counts, and malformed-query regression coverage.
- `npm ci` and `npm run build`: passed. No frontend dependency changes were needed.
- Chrome browser checks against a separate database verified landing/club profiles,
  event search/filter/empty states, calendar month/week/list and Asia/Colombo dates,
  event clicks, registration/login/duplicate errors, safe post-login returns,
  all dashboards, mobile widths 320/390/820/1440, participant cancellation,
  organizer submission/approval/withdrawal, and event creation/editing/publication.
- Fixed malformed numeric query parameters returning 403 instead of safe 400 errors,
  and restored login return navigation to the existing My Applications page.
- Scanned 271 historical Git blobs for configured local secrets and private-key,
  credential-URI, and access-token patterns: no matches. Local secret files are
  ignored and untracked. This is a targeted repository audit, not a penetration test.
- History already contained 14 meaningful non-merge commits before the final docs
  commit. No history was rewritten, and no push or merge was performed.
- No verification services were unavailable. Browser fixtures were isolated from
  the application database and removed after testing. Limitations are listed below.

## Troubleshooting

- **8080 already in use:** stop the existing backend with Ctrl+C. Use
  `lsof -nP -iTCP:8080 -sTCP:LISTEN` on macOS/Linux to identify the listener before
  stopping it; avoid running duplicate backend instances.
- **Vite ECONNREFUSED / clubs loading error:** confirm backend startup completed,
  check both health endpoints, and ensure Vite proxies the correct port. Mock data
  is not a replacement for a missing backend/database.
- **JWT startup failure:** check the persistent properties file, Base64 key length,
  and stale exported JWT_SECRET overrides; keep the same key across restarts.
- **Bootstrap failure:** check dev profile, all required identity fields, exact
  registration-number format, password rules, and duplicate account identities.
- **MongoDB startup/index errors:** start the configured database and resolve existing
  duplicate data before enabling unique indexes. Never delete application data as a
  shortcut to resolving startup issues.
- **403 after admin promotion:** sign out/in to obtain a JWT with the new role.

## Known limitations

- Development setup only: production deployment, TLS, database credentials/access
  controls, backups, and migrations require separate configuration. No default
  credentials or real secrets are included in the repository.
- Tokens live only in React memory, expire within 15 minutes, and are lost on reload.
  There are no refresh tokens or password reset. Logout clears the browser session;
  an already-issued token expires naturally unless account/role changes invalidate it.
- User management is a directory plus existing club access controls; it does not
  implement user deletion, public admin signup, or arbitrary role changes.
- Public reads cache for up to 30 seconds; mutations invalidate the local cache.
  Dashboards have explicit refresh controls, without live push updates.
- Event RSVP/application ledgers share MongoDB's 16 MB document size limit; very
  large events/application histories need a different storage strategy. Reads repair
  interrupted history projections; they are not cross-collection transactions.
- MongoDB does not enforce reference integrity. Event IDs/references use ObjectIds;
  application services enforce ownership and valid state transitions.
- No notifications, email delivery, advanced analytics, or automated deployment.
- Frontend checks use production builds and browser integration checks; there is
  no separate frontend unit-test framework. Automated backend live tests require MongoDB.
