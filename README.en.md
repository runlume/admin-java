<a href="https://runlume.app">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/brand/wordmark-dark.svg" />
  <img src="docs/brand/wordmark-light.svg" alt="Runlume" width="220" />
</picture>
</a>

# Standard Admin Backend

[![License](https://img.shields.io/github/license/runlume/admin-java?style=flat-square&label=license&color=97ca00)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-1f6feb?style=flat-square)](docs/standards/development/java-25-language-and-runtime-guidelines.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f?style=flat-square)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169e1?style=flat-square)](https://www.postgresql.org/)

API contract: [docs/project/api.md](docs/project/api.md) · Docs: [docs/README.md](docs/README.md) · [中文](README.md)

The backend counterpart of [Standard Admin Frontend](https://github.com/runlume/admin-design): a standard starting
point wired for the **minimal** integration path of `platform-integration-sdk-java`, shipping with **local user
management and sign-in** so a product team only has to plug in its own business domain. Interface language and
visual rules come from admin-design; API contract, sessions, permissions, tenant isolation and platform
integration come from this one copy.

Java 25 + Spring Boot 4.1 + Spring Security + PostgreSQL 18 + Flyway + jOOQ + Spring Modulith, built with the
Gradle Wrapper only, passwords hashed with BCrypt.

## Capabilities

| Capability | Notes |
| --- | --- |
| Local accounts | Register, sign in, sign out, current user, roles and permission codes, BCrypt passwords, disabling an account revokes its sessions immediately |
| Server-side sessions | Spring Session JDBC with an opaque cookie and an absolute expiry; sign-in and platform Launch share one establishment path |
| CSRF | Cookie + request-header double submit; fetch the token from `GET /api/v1/csrf` |
| Account and role management | Paged listing, create, rename, change roles, enable/disable (`user:*`, `role:*` codes) |
| Minimal platform integration | Launch code exchange, the four lifecycle commands (idempotent), the connectivity probe, workspace mapping |
| Platform boundary | Lifecycle endpoints are authorised per endpoint by service identity and scope; runtime tokens are verified by the SDK |
| Tenant isolation | Every business table carries a workspace dimension, and the workspace id only ever comes from an authenticated session |
| Observability | Server-side correlation id, non-sensitive audit trail, stable `application/problem+json` error codes |
| Sample domain | `notice` demonstrates the boundary between a business domain and the `access` module |

Not included: resource quotas, platform AI, capabilities, events, unified notifications, the organisation
directory, and the private-deployment Deployment License (these belong to the **full** integration path — see
[Phased onboarding](#phased-onboarding)).

## Quick start

Prerequisites: Java 25, PostgreSQL 18, and a working container runtime (needed for build-time jOOQ code
generation and the integration tests).

```bash
# 1. Start a local PostgreSQL (example); with Docker, replace podman with docker
podman run -d --name admin-db -p 5432:5432 \
  -e POSTGRES_DB=admin -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin \
  postgres:18.4

# 2. Start the app: Flyway creates the schema, the landing route is an empty admin console
ADMIN_DB_PASSWORD=admin ADMIN_SESSION_COOKIE_SECURE=false \
  ./gradlew bootRun

# 3. Register the first account: with no account yet, the first registration gets the admin role
curl -c jar -X GET http://localhost:8080/api/v1/csrf
curl -b jar -c jar -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(curl -b jar -s http://localhost:8080/api/v1/csrf | sed 's/.*"token":"\([^"]*\)".*/\1/')" \
  -d '{"email":"admin@runlume.local","displayName":"Administrator","password":"runlume-password"}'
```

`ADMIN_SESSION_COOKIE_SECURE=false` is for plaintext HTTP development only; production must keep the default
`true`.

## Build and verification

```bash
./gradlew build        # compile + Checkstyle + all tests
./gradlew test         # unit and integration tests (Testcontainers starts a temporary PostgreSQL 18)
./gradlew check        # full gate
./gradlew generateJooq # regenerate jOOQ types from the Flyway migrations only
./gradlew bootRun      # run locally
```

The integration tests and jOOQ code generation need a container runtime. Docker Desktop and a
distribution's docker package work out of the box; the setups below cover the cases that need an
explicit socket, such as Podman. `TESTCONTAINERS_RYUK_DISABLED=true` skips the Ryuk container, which
must mount the host container socket and often cannot under Podman or rootless Docker.

**docker vs podman**: both run these tests. Testcontainers only needs a container runtime exposing a
Docker-compatible socket, so the differences that matter are three:

| | docker | podman |
| --- | --- | --- |
| Process model | Persistent daemon; on macOS / Windows it runs inside the Docker Desktop VM | Daemonless; on macOS / Windows start the VM with `podman machine start`, on Linux it runs as local processes |
| Socket | The default location works | `DOCKER_HOST` must be set explicitly — a unix socket on macOS, a named pipe on Windows |
| Ryuk container cleanup | Works | Ryuk must mount the host socket and often cannot, so set `TESTCONTAINERS_RYUK_DISABLED=true`; to keep Ryuk, also set `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` |

On Linux, podman is rootless by default and docker runs as root; these tests only start a plain
PostgreSQL 18 container, so that difference does not change the outcome.

**macOS**: Docker Desktop needs no extra configuration; with Podman, start the VM and point `DOCKER_HOST`
at its socket:

```bash
podman machine start
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
export TESTCONTAINERS_RYUK_DISABLED=true
./gradlew check
```

**Windows**: Docker Desktop needs no extra configuration — just run `.\gradlew.bat check`. With Podman, set
these in PowerShell (replace `podman-machine-default` if you renamed the machine):

```powershell
podman machine start
$env:DOCKER_HOST = 'npipe:////./pipe/podman-machine-default'
$env:TESTCONTAINERS_RYUK_DISABLED = 'true'
.\gradlew.bat check
```

**Linux**: docker connects to `/var/run/docker.sock` directly and needs no variables; for rootless Podman
enable the socket first:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST="unix://$XDG_RUNTIME_DIR/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./gradlew check
```

Inside WSL, follow the Linux setup.

The database schema is built **only** from the Flyway migrations under `src/main/resources/db/migration`. Every
jOOQ generation starts a fresh temporary PostgreSQL 18 and migrates it completely; generating from a developer's
existing database is not allowed. Changing a table means changing both the migration and the code that uses it,
and `./gradlew build` re-generates and verifies the table inventory.

## Dependency source

`platform-integration-sdk-java` is served by Runlume Nexus and declared in `settings.gradle` as
`https://nexus.runlume.app/repository/maven-public/` (scoped to `app.runlume.*`). Supply credentials through
Gradle properties when the repository requires authentication, and never commit them:

```bash
./gradlew build -PrunlumeNexusUsername=... -PrunlumeNexusPassword=...
```

## API surface

| Method and path | Permission | Notes |
| --- | --- | --- |
| `GET /api/v1/csrf` | anonymous | Fetch the CSRF token and header name |
| `POST /api/v1/auth/register` | anonymous | Register and establish a session |
| `POST /api/v1/auth/login` | anonymous | Email and password sign-in |
| `POST /api/v1/auth/logout` | session | Sign out and invalidate the server-side session |
| `GET /api/v1/me` | session | Current user, roles and permission codes |
| `GET /api/v1/permissions` | session | Built-in permission catalogue |
| `GET /api/v1/users` | `user:view` | Paged account listing |
| `POST /api/v1/users` | `user:create` | Create an account |
| `GET /api/v1/users/{id}` | `user:view` | Account detail |
| `PATCH /api/v1/users/{id}` | `user:update` | Rename and change roles |
| `POST /api/v1/users/{id}/status` | `user:disable` | Enable/disable and revoke sessions |
| `GET /api/v1/roles` | `role:view` | Roles and permissions |
| `GET /api/v1/platform-connection` | anonymous | Platform connectivity, boolean only |
| `POST /launch` | anonymous (CSRF-exempt by contract) | Exchange a platform Launch code and establish a session |
| `POST /integration/v1/app-instances` | `instance:provision` | Provision an instance |
| `GET /integration/v1/operations/{id}` | `instance:operation:read` | Query an operation |
| `POST /integration/v1/app-instances/{id}/suspend` | `instance:suspend` | Suspend an instance |
| `POST /integration/v1/app-instances/{id}/resume` | `instance:resume` | Resume an instance |
| `DELETE /integration/v1/app-instances/{id}` | `instance:deprovision` | Deprovision an instance |
| `GET POST PATCH /api/v1/notices` | `notice:view` / `notice:manage` | Sample domain: notices |

Sign-in, Launch and error responses all return stable error codes (`EMAIL_ALREADY_REGISTERED`,
`INVALID_CREDENTIALS`, `IDEMPOTENCY_CONFLICT`, …) as `application/problem+json` with a `code` field, so the
frontend can map them to copy.

## Key conventions

- **Permission codes**: `*` for everything, `module:*` for a whole module, exact match otherwise — the same
  convention as the admin-design frontend. The server expands wildcards into concrete codes before
  authorising, so nothing is ever visible in the UI but rejected by the API.
- **The session holds derived identity only**: `AdminSessionPrincipal` carries no platform token, Launch code,
  client secret or full claims, and a local session never expires later than the platform context token's `exp`.
- **Tenancy comes from the session only**: account, instance and workspace claims in request bodies, query
  strings or custom headers are never trusted.
- **Platform entry points fail closed**: with `admin.platform.enabled=false` the Launch and lifecycle endpoints
  reject everything, and the console still runs standalone.
- **The audit trail only appends terminal facts**: tokens, secrets, passwords, full payloads and raw requests
  never reach it.
- **No abstraction without a consumer**: integration capabilities are enabled one at a time against real use
  cases, never reserved "just in case".

## Project structure

```text
src/main/java/app/runlume/admin/
├── AdminJavaApplication.java
├── access/                       Platform-integration owning module
│   ├── WorkspaceView.java        Platform instance ↔ local workspace mapping
│   ├── WorkspaceDirectory.java   Read-only port
│   ├── WorkspaceLifecycle.java   Idempotent lifecycle command port
│   ├── PlatformIntegrationProperties.java
│   ├── identity/                 Named Interface "identity"
│   │   ├── AdminSessionPrincipal.java
│   │   ├── AdminIdentity.java
│   │   ├── PermissionCatalog.java
│   │   └── infrastructure/
│   │       ├── SdkPlatformLaunchGateway.java    platform-integration-sdk-java adapter
│   │       ├── ModuleServiceTokenProvider.java
│   │       ├── PlatformConnectionProbe.java
│   │       ├── security/                        security chain, session establishment and revocation
│   │       └── web/                             session and account management API
│   ├── observability/            Named Interface "observability"
│   └── infrastructure/           jOOQ implementations, lifecycle ingress, problem handling
└── notice/                       Sample domain: reaches access only through Named Interfaces

src/main/resources/db/migration/  Flyway baseline
src/jooqCodegen/                  jOOQ code generation launcher (build classpath only)
docs/                             Project docs and bundled standards
```

Architecture and boundaries: [docs/project/architecture.md](docs/project/architecture.md).
API and error codes: [docs/project/api.md](docs/project/api.md).

## Using this as a template

1. **Copy the whole directory**, then change `rootProject.name` in `settings.gradle`, `group`/`description` in
   `build.gradle`, and `spring.application.name` in `application.yml`.
2. **Rename the package**: change `app.runlume.admin` to your own root package and update the generated package
   names and table inventory in `JooqCodegen`.
3. **Swap the business domain**: remove or rewrite the `notice` package and add packages beside it; reach
   platform integration facts only through the `access.identity` and `access.observability` Named Interfaces.
4. **Swap the database**: change `ADMIN_DB_*` and `src/main/resources/db/migration/V001__baseline.sql`, keeping
   the workspace dimension on business tables, then run `./gradlew generateJooq`.
5. **Swap permissions**: change `PermissionCatalog` and `V002__built_in_roles.sql`, keeping the
   `module:action` code format so the admin-design frontend wildcard matching keeps working unchanged.
6. **Connect the platform**: fill in the module id, issuer, JWKS and audiences from
   [Platform integration configuration](#platform-integration-configuration), turn on
   `admin.platform.enabled`, and register the module and client credentials on the platform side.
7. **Rebrand**: `banner.txt`, the READMEs and `docs/`.

## Platform integration configuration

Off by default. Once enabled, every platform address comes from configuration and cannot be overridden by the
browser or request parameters; the client secret comes only from the environment or a secret manager.

| Property | Environment variable | Notes |
| --- | --- | --- |
| `admin.platform.enabled` | `ADMIN_PLATFORM_ENABLED` | Enable platform integration |
| `admin.platform.module-id` | `ADMIN_PLATFORM_MODULE_ID` | Stable module id registered with the platform |
| `admin.platform.base-uri` | `ADMIN_PLATFORM_BASE_URI` | Platform control-plane address |
| `admin.platform.runtime-issuer` / `runtime-jwks-uri` | `ADMIN_PLATFORM_RUNTIME_*` | Platform runtime issuer and public-key location |
| `admin.platform.runtime-audience` | `ADMIN_PLATFORM_RUNTIME_AUDIENCE` | Runtime audience the platform signs for this system |
| `admin.platform.launch-path` | `ADMIN_PLATFORM_LAUNCH_PATH` | Path the browser posts the Launch code to |
| `admin.platform.service-issuer` / `service-jwks-uri` | `ADMIN_PLATFORM_SERVICE_*` | Platform service identity issuer and public-key location |
| `admin.platform.service-audience` | `ADMIN_PLATFORM_SERVICE_AUDIENCE` | Audience of the module service token |
| `admin.platform.lifecycle-audience` | `ADMIN_PLATFORM_LIFECYCLE_AUDIENCE` | Audience the platform uses when calling lifecycle endpoints |
| `admin.platform.service-token-uri` / `service-client-id` / `service-client-secret` | `ADMIN_PLATFORM_SERVICE_*` | Module service identity token endpoint and credentials |
| `admin.local.registration-enabled` | `ADMIN_REGISTRATION_ENABLED` | Allow self-service registration |
| `admin.local.session-ttl` | `ADMIN_SESSION_TTL` | Absolute local session lifetime |

### Phased onboarding

This template implements the **minimal** integration: unified provisioning and sign-in. When resource, AI,
capability or cross-SaaS data requirements appear, check the boundary against the
[platform integration contract](docs/project/platform-integration.md) and enable capabilities one at a time
from the platform's full integration standard — never build empty implementations up front.

The Spring Boot Deployment License auto-configuration is explicitly excluded in this template: REMOTE onboarding
does not require a business SaaS to hold or verify a Deployment License. When you ship a private image that
customers run themselves, remove the `spring.autoconfigure.exclude` entry in `application.yml` and supply the
license configuration it requires.

## Documentation

- [docs/README.md](docs/README.md): documentation index and this project's fixed values
- [docs/project/architecture.md](docs/project/architecture.md): module boundaries, sessions and tenancy
- [docs/project/api.md](docs/project/api.md): API contract, error codes and state semantics
- [docs/project/template.md](docs/project/template.md): checklist for copying the template
- [docs/project/platform-integration.md](docs/project/platform-integration.md): the minimal integration contract this repository implements
- [docs/project/platform-capabilities.md](docs/project/platform-capabilities.md): platform open capabilities and the SDK entry points
- [docs/standards/](docs/standards/): bundled generic engineering standards

## License

[MIT](LICENSE), Copyright (c) 2026 Runlume. Copy it into commercial products freely — just keep the copyright
and license notice. The marks under `docs/brand/` are brand assets, so replace them with your own.
Details: [docs/guide/license.md](docs/guide/license.md).
