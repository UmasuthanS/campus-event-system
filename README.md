# Campus Event Discovery & RSVP System

Stage 1 setup for the University of Ruhuna, Faculty of Engineering.

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
optional for Stage 1 startup, health verification, and tests. The Mongo driver
may log background connection failures when no database is running.
Only `GET /api/v1/health` is public; other backend requests are denied.
Authentication, events, and dashboards are reserved for later stages.

## Optional local MongoDB configuration

The default URI is `mongodb://127.0.0.1:27017/campus_events` with short connection
timeouts. To override it, copy and load the development example before startup:

```sh
cd backend
cp .env.example .env
# Edit .env for your local database, then export its values:
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

Spring Boot does not load `.env` automatically. On Windows, set `MONGODB_URI`
and optionally `SERVER_PORT` in your shell before running `mvnw.cmd`.
Keep real credentials out of Git. These settings are for local development;
production deployment configuration is outside Stage 1.

## Verify builds

```sh
cd frontend
npm ci
npm run build
cd ../backend
./mvnw clean verify
```

Backend integration tests start the HTTP server with an unavailable MongoDB
address and verify public health and denial of other routes. Build output is
written to `frontend/dist/` and `backend/target/` (ignored by Git).
