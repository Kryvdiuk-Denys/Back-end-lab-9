# Coworking Microservices

Java 17 and Spring Boot 3.3 application split into independent Maven modules. `workspace-service` owns workspace data, `booking-service` owns booking data, and `common` contains the shared DTO and gRPC contracts. Booking calls workspace through a configurable HTTP or gRPC client; the services depend only on `common`.

## Stack

- Java 17, Spring Boot 3.3
- Spring MVC, Thymeleaf UI
- Spring Data JPA, PostgreSQL
- Liquibase migrations
- MapStruct DTO mapping
- Spring Security with OIDC login for UI and JWT bearer tokens for API
- Keycloak realm for users and roles

## Run

Prerequisites:

- JDK 17
- Maven
- Docker with Docker Compose

From the project directory, build and verify the Maven reactor:

```bat
mvn verify
```

On Windows, if this project is in a directory with non-ASCII characters and `protoc` reports that the `.proto` file does not exist, run Maven from an unused temporary drive alias so the native compiler receives an ASCII path:

```powershell
$projectDirectory = (Get-Location).Path
subst R: $projectDirectory
Set-Location R:\
mvn verify
Set-Location $projectDirectory
subst R: /d
```

Start the full stack with Docker Compose (Maven is not required on the host):

```powershell
docker compose up -d --build
```

Booking UI and API: `http://localhost:8080`  
Workspace service API: `http://localhost:8082/api/workspaces`  
Keycloak admin console: `http://localhost:8081`

The booking service proxies `/api/workspaces` so existing clients can keep using port 8080. Each service has its own `application.yml`, main class, tests, Dockerfile, port, and database. The booking schema stores a workspace ID and name snapshot without a foreign key to the workspace database. Compose uses separate fresh volumes for booking and workspace data; the previous single-database volume is left intact.

Admin login: `admin` / `admin`  
Realm: `coworking`

The root Maven project is an aggregator. To run the services directly from the host, start PostgreSQL and Keycloak with `docker compose up -d postgres workspace-db keycloak`, then run each service in its own terminal. Build the service jar with `-am` so Maven also builds `common`:

```powershell
mvn -pl workspace-service -am package
java -jar .\workspace-service\target\workspace-service-0.1.0.jar
```

```powershell
mvn -pl booking-service -am package
java -jar .\booking-service\target\booking-service-0.1.0.jar
```

If Keycloak was already running before the realm file was changed, recreate it:

```bat
docker compose down
docker compose up -d
```

If `docker` is not recognized, install Docker Desktop and restart the terminal.

If `mvn` is not recognized, install Apache Maven and add its `bin` directory to `PATH`, or run the project from IntelliJ IDEA using the Maven tool window. This project does not currently include Maven Wrapper files, so `mvnw.cmd` is not available.

If Maven is not installed locally but Docker and Java are available, the Maven image can build both jars for host execution:

```powershell
$env:Path += ";C:\Program Files\Docker\Docker\resources\bin"
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9-eclipse-temurin-17 mvn -pl booking-service -am -DskipTests package
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9-eclipse-temurin-17 mvn -pl workspace-service -am -DskipTests package
java -jar .\booking-service\target\booking-service-0.1.0.jar
java -jar .\workspace-service\target\workspace-service-0.1.0.jar
```

## Demo Users

| Username | Password | Role    |
|----------|----------|---------|
| admin    | admin    | ADMIN   |
| manager  | manager  | MANAGER |
| user     | user     | USER    |

## UI Scenarios

Open `http://localhost:8080`.

- Unauthenticated users are redirected to Keycloak.
- `admin` can create, read, update, and delete bookings and workspaces.
- `manager` can create and update bookings/workspaces, but cannot delete.
- `user` can create bookings and read data.
- `/admin` is protected by `SecurityFilterChain` and requires `ADMIN`.

## API Scenarios

Ready HTTP requests are in `http/coworking-booking.http`.

The file covers:

- getting JWT tokens from Keycloak
- CRUD for `/api/workspaces`
- CRUD for `/api/bookings`
- `403 Forbidden` from `SecurityFilterChain` on `/api/admin/status`
- `403 Forbidden` from `@PreAuthorize` on `DELETE /api/bookings/{id}` for non-admin users

## Async Booking Work

Creating a booking queues three simulated slow side effects in the `bookingTaskExecutor`: confirmation email, receipt PDF generation, and access-service synchronization. The HTTP call does not wait for them. The executor uses a four-thread pool, a bounded queue of 50 tasks, and the `booking-async-` thread prefix. When the queue is full, the API returns `503 Service Unavailable` with a retry message.

`POST /api/bookings/preflight` starts independent availability and price-estimate calls, then combines their results. The caller waits for at most 2.5 seconds. An individual call failure is converted into a `CHECK_FAILED` result; a timeout returns `503`.

The async settings and simulated delays are in `src/main/resources/application.yml`. `mvn test` covers fire-and-forget behavior, executor threads and concurrency, exception handling, bounded waits, and queue rejection.

## Quartz Scheduler

Quartz uses a PostgreSQL JDBC job store. Liquibase adds its `QRTZ_*` tables through `db/changelog/003-quartz-schema.yaml`; Quartz schema initialization is disabled so restarts do not drop persisted schedules. The reminder job runs every 15 minutes and daily, while expired confirmed bookings are marked completed each day. Both jobs use `@DisallowConcurrentExecution`, persistent job data, and service methods with transaction boundaries.

Trigger values override job defaults through Quartz's merged `JobDataMap`. Each cron trigger has a `DO_NOTHING` misfire policy and uses `booking.quartz.time-zone` (default `Europe/Kyiv`, invalid IDs fall back to GMT). Reminder delivery is represented by a persisted `reminder_sent_at` marker and a log entry; repeated runs therefore skip reminders already processed. Job failures are logged and left for the next scheduled fire.

## Message broker (Lab 8)

`workspace-service` publishes a `WorkspaceCreatedEvent` after a workspace insert commits. RabbitMQ routes the persistent JSON message through the durable `coworking.booking.workspace-created` queue, and `booking-service` stores the event in its own database. The event contract is a DTO in `common`; the consumer does not depend on producer-service classes or call it over HTTP in this flow. Read received events from `GET /api/events/workspaces` on the booking service.

Start the broker and services with `docker compose up -d --build`. RabbitMQ uses the Compose service name `rabbitmq` inside Docker and `localhost` by default for IDE runs. Set `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD` to override the defaults. The management console is at `http://localhost:15672` (default login `coworking` / `coworking`).

For the event demo, send an authenticated `POST /api/workspaces` directly to `http://localhost:8082`, then read `http://localhost:8080/api/events/workspaces`. To demonstrate consumer downtime, run `docker compose stop booking-service`, create two workspaces through port 8082, then run `docker compose start booking-service`. Both events remain in RabbitMQ and appear in the booking endpoint after the consumer reconnects. RabbitMQ's data volume preserves its durable queue across broker restarts.

The consumer inserts events with `ON CONFLICT (event_id) DO NOTHING`; redelivery therefore leaves one stored row. A unit test covers repeated processing of the same event.

## Interservice communication (Lab 9)

`booking-service` reads workspace data through the `WorkspaceLookupPort`. Set `WORKSPACE_CLIENT_TYPE` to `http` (default) or `grpc` to select its adapter. The HTTP adapter is a declarative Spring HTTP interface, with connection and read timeouts configured by `WORKSPACE_HTTP_CONNECT_TIMEOUT` and `WORKSPACE_HTTP_READ_TIMEOUT`. The gRPC adapter calls `WorkspaceLookupService/GetWorkspace` and applies `WORKSPACE_GRPC_DEADLINE` to every call.

The `common` module contains `src/main/proto/workspace_interservice.proto`; Maven generates the Java message and service classes during the build. `workspace-service` hosts that gRPC service when `WORKSPACE_GRPC_SERVER_ENABLED=true`; its port is `WORKSPACE_GRPC_SERVER_PORT` (default `9090`). The server is disabled by default. For a local run, use `localhost:9090`; Docker Compose points booking to `workspace-service:9090` by default. The REST URL remains configurable with `WORKSPACE_SERVICE_URL`.

To run the demo over gRPC in Docker Compose, set these variables in the shell or `.env`, then rebuild and start the stack:

```text
WORKSPACE_CLIENT_TYPE=grpc
WORKSPACE_GRPC_SERVER_ENABLED=true
```

The same `WorkspaceLookupPort` handles either transport. Missing workspaces become HTTP 404 responses; timeouts, connection failures, and other upstream errors become HTTP 503 responses. The other workspace operations use the declarative HTTP adapter through `WorkspaceCatalogPort` and `WorkspaceManagementPort`.

Manual token request example:

```bash
curl -X POST http://localhost:8081/realms/coworking/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=coworking-api" \
  -d "client_secret=api-secret" \
  -d "username=user" \
  -d "password=user"
```

Use `access_token` as:

```text
Authorization: Bearer <token>
```

## Acceptance Criteria Mapping

| AC | Implementation |
|----|----------------|
| AC1 | Booking and workspace CRUD APIs run in separate service modules with independent controllers, services, repositories, and entities. |
| AC2 | Database schema is created by Liquibase changelog `db/changelog/001-init-schema.yaml`; Hibernate uses `ddl-auto: validate`. |
| AC3 | Thymeleaf UI supports core CRUD scenarios for bookings and workspaces. |
| AC4 | UI uses OIDC login through Keycloak and returns with an authenticated session. |
| AC5 | `/api/**` accepts `Authorization: Bearer <token>` JWT requests without UI session. |
| AC6 | `/api/admin/**` and `/admin/**` require `ADMIN` in `SecurityFilterChain`; non-admin receives 403. |
| AC7 | Service delete/update methods use `@PreAuthorize`; non-admin delete is blocked by method security. |
| AC8 | `http/coworking-booking.http` contains ready demonstration requests. |
| AC9 | Booking uses the declarative `WorkspaceHttpApi` over `RestClient` with configured connect and read timeouts. |
| AC10 | Workspace lookup, catalog, and management ports keep controllers and services independent from clients. |
| AC11 | `common/src/main/proto/workspace_interservice.proto` generates the shared protobuf and gRPC contract at build time. |
| AC12 | `workspace-service` starts its token-protected gRPC server only when enabled and stops it with the application. |
| AC13 | Booking's gRPC adapter uses a configured target and per-call deadline through the same lookup port as HTTP. |
| AC14 | `WORKSPACE_CLIENT_TYPE` selects HTTP or gRPC; unsupported values fail startup. |
| AC15 | REST and gRPC addresses have local defaults and Compose overrides. |
| AC16 | Both adapters translate not-found and upstream failures into domain exceptions; the API returns 404 or 503. |

## Multi-module lab

The root `pom.xml` has `pom` packaging and aggregates `common`, `workspace-service`, and `booking-service`. Shared test dependency management is declared in the root POM. Both services depend on `common` for DTOs and the generated gRPC contract. Run `mvn verify` from this directory to build and verify the full reactor.
