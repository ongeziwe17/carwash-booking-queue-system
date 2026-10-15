# HlambaQ naming and repository transition

HlambaQ is the product name. The backend Maven artifact, JAR, local image, Compose service, Spring application default, OpenAPI title and OpenTelemetry service name are `hlambaq-api`. The current GitHub repository remains `ongeziwe17/carwash-booking-queue-system`; `hlambaq` is the proposed name after the organization decision in [#196](https://github.com/ongeziwe17/carwash-booking-queue-system/issues/196). `hlambaq.co.za` is a planned public domain, not a configured or purchased dependency.

## Compatibility boundaries

| Identifier | Decision | Reason |
| --- | --- | --- |
| Java `com.carwash` packages and Maven `com.carwash` group | Keep | They identify existing code/artifact consumers; a package/group migration would be a separate breaking change with no product benefit here. |
| `carwash.*` Spring keys and `CARWASH_*` environment variables | Keep | Existing configuration contracts and deployed environments use them. Inventory and stage any future rename with aliases, telemetry checks and a removal window. |
| `SECURE_JWT_ISSUER` values, including the local example | Keep | Changing the issuer would reject issued tokens. Existing JWT keys and authentication contracts are untouched. |
| PostgreSQL `carwash` database/user, `carwash-postgres-data` volume, tables and V1–V8 Flyway history | Keep | Renaming persistence requires an explicit data migration; no schema or data change is part of branding. |
| Docker Hub/GHCR `carwash-booking-queue-system` paths and published tags | Keep | Existing consumers can still pull images. A later registry cutover needs dual publication or a documented consumer migration and security review. Local image `hlambaq-api:local` is independent. |
| Prometheus `job="carwash"`, `job="carwash-readiness"`, `carwash_*` metrics and `carwash.*` span names | Keep | Dashboards, alert rules, probes and external queries depend on them. Local scrape targets point to the renamed Compose service. |
| `tests/bruno/carwash-api` and `tests/bruno/carwash-management` directories | Keep | CI and developer collection paths remain stable; the displayed collection titles now say HlambaQ. A directory move offers no runtime benefit. |
| Current GitHub URLs in POM, docs and alert runbooks | Keep | The repository has not moved. Historical decisions and links remain valid. |

The current `.env.example` uses `SPRING_APP_NAME=hlambaq-api`; the Spring configuration also defaults to `hlambaq-api` when the variable is absent. A private pre-existing `.env` that sets the previous name still overrides the default until its operator changes it. No new required setting is introduced.

The local Compose API service changes from `carwash-api` to `hlambaq-api`, with Prometheus and the optional observability override updated together. Before restarting an existing local stack, `docker compose down --remove-orphans` stops/removes its containers and the old API orphan while **retaining** the `carwash-postgres-data` volume; `docker compose up --build -d` then builds/starts the new local API and PostgreSQL containers. These commands change local container state; neither uses `--volumes`. The OpenTelemetry `service.name` changes to `hlambaq-api`; external trace queries saved under the old service name need updating. Prometheus job labels and metric names remain stable.

## Future repository move checklist (#196)

1. Decide the HlambaQ organization name, owners, recovery access and the destination `hlambaq` repository name.
2. Inventory branches/tags, PRs/issues/releases, collaborators, branch rules and required check names, Actions environments/secrets/variables, package registries, webhooks, integration credentials, badges and links.
3. Choose a reviewed transfer window and confirm GitHub's current transfer behavior and any namespace/name collisions. Back up repository settings and record current integrations.
4. After separate approval, transfer/rename once, validate commit history and references, re-establish permissions and required checks, test CI/Bruno/image publishing, and update local remotes and documentation.
5. Decide the registry-path cutover independently, keeping existing published images usable during consumer migration. Verify webhooks and external links rather than relying only on redirects.

This application PR neither renames nor transfers the remote repository. The future [pipeline repository plan #197](https://github.com/ongeziwe17/carwash-booking-queue-system/issues/197) and [VM infrastructure plan #198](https://github.com/ongeziwe17/carwash-booking-queue-system/issues/198) are separate. Application metrics, dashboards and application-specific alerts stay with the code.
