# CAS Auth Complete Review - 2026-03-16

## Scope
- Branch: `feature/cas-auth-complete`
- Baseline: `feature/cas-auth-support`
- Review basis: code diff, targeted tests, local Docker auth harness, `CONTRIBUTING_zh.md`

## Control Contract
- Primary Setpoint: CAS full-integration branch has one consistent truth across portal persisted configs, admin global env configs, backend export/runtime behavior, and local harness gates.
- Acceptance:
  - `mvn -pl himarket-server -am -Dtest=CasServiceDefinitionServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl himarket-bootstrap -am package -DskipTests`
  - `SKIP_BUILD=1 SKIP_DOCKER_UP=1 CAS_READY_TIMEOUT=420 ./deploy/docker/scripts/auth-harness.sh`
  - startup logs no longer emit obsolete compose warning or Spring Data Redis repository scan noise.
- Guardrails:
  - no change to CAS login/callback/proxy/logout semantics
  - no mock success path introduced
  - admin/portal service-definition exports must remain behaviorally aligned
- Boundary:
  - docker compose files, bootstrap application config, CAS service-definition exporter, CAS nested config models, harness assertions

## Findings
### Fixed
1. `admin.auth` env binding lost nested map fields for CAS advanced policies.
   - Affected surfaces: `proxyPolicy.headers`, `accessStrategy.requiredAttributes`, `accessStrategy.rejectedAttributes`, `accessStrategy.requiredHeaders`
   - Root cause: relying on Spring Boot env binding for nested `Map<String, String>` / `Map<String, List<String>>` produced drift between portal JSON config path and admin env config path.
   - Fix: added explicit JSON config fields in CAS model objects and taught service-definition export to resolve primary structured values first, then JSON fallback.
2. Docker base compose emitted obsolete `version` warning on every local gate run.
   - Fix: removed `version` from `deploy/docker/docker-compose.yml`.
3. Spring Data Redis emitted repository assignment noise on every backend boot.
   - Fix: disabled Redis repository auto-detection in `himarket-bootstrap/src/main/resources/application.yml`.
4. Local backend image rebuild paid unnecessary delay for a debug tool path.
   - Root cause: `himarket-bootstrap/Dockerfile` always downloaded Arthas during local image builds and sent the full bootstrap context.
   - Fix: added `himarket-bootstrap/.dockerignore` and made Arthas installation conditional; local compose now builds with `INSTALL_ARTHAS=false`.
5. JWT revocation collided with subsequent admin CAS MFA logins on a clean database.
   - Root cause: tokens were deterministic for the same user within the same second because JWT payloads had no `jti`; revoking an earlier token also revoked an identical later token.
   - Fix: added `jti` to `TokenUtil` and locked it with `TokenUtilTest`.
6. Local runtime and deploy docs targeted MySQL, but the app still used the MariaDB JDBC driver.
   - Root cause: datasource driver/URL and DAL dependency used MariaDB while the builtin environment and docs consistently shipped MySQL, producing driver-specific warnings and extra local setup complexity.
   - Fix: switched DAL/runtime to MySQL Connector/J, updated JDBC URL/driver, and removed the now-unneeded MySQL grant workaround.
7. Startup logs emitted framework-default noise unrelated to business behavior.
   - Fix: disabled `spring.jpa.open-in-view`, disabled Thymeleaf template location checks for this API service, updated MySQL connector coordinates to `com.mysql:mysql-connector-j`, and excluded `commons-logging` from `aliyun-java-sdk-core`.
8. `CAS HEADER response type` existed in config/export enums but was explicitly rejected by backend validation, leaving a false capability surface.
   - Root cause: service-definition model and admin UI could express `HEADER`, but runtime login flow only handled browser redirect and code exchange semantics.
   - Fix: enabled `HEADER` as a supported CAS-aware client mode, propagated `method=HEADER` on authorize, excluded `HEADER` providers from interactive login lists, and added dedicated local CAS registry plus harness coverage for developer/admin runtime flows.
9. `CONTRIBUTING` required frontend `npm run format`, but both frontend packages lacked a `format` script.
   - Root cause: repository contribution contract and frontend package scripts drifted apart.
   - Fix: added `format` scripts to `himarket-web/himarket-admin/package.json` and `himarket-web/himarket-frontend/package.json`, and added Javadoc comments to new CAS service interfaces to better match contribution expectations for public APIs.

### Residual Risks
1. Harness still intentionally emits two `INVALID_PARAMETER` warnings when it verifies rejected CAS proxy target services. These are expected negative-path signals, not regressions.
2. The open-source builtin path is now explicitly aligned to MySQL. If a downstream deployment depends on MariaDB-specific JDBC behavior, that environment needs a separate compatibility validation before adopting this branch.
3. `HEADER` mode is now intentionally scoped to CAS-aware clients. It is exported and runtime-validated, but it is not shown as an interactive browser login button because the SPA callback model still centers on redirect and code exchange.

## Verification
- `mvn -pl himarket-server -am -Dtest=CasServiceDefinitionServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
- `mvn -pl himarket-bootstrap -am package -DskipTests`
- `mvn -pl himarket-server -am -Dtest=TokenUtilTest -Dsurefire.failIfNoSpecifiedTests=false test`
- `mvn -pl himarket-server -am -Dtest=IdpServiceImplTest,CasServiceImplTest,AdminCasServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
- `docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build himarket-server` in `deploy/docker`
- `docker logs --since 2m himarket-server`
- `SKIP_BUILD=1 SKIP_DOCKER_UP=1 CAS_READY_TIMEOUT=420 ./deploy/docker/scripts/auth-harness.sh`
- `SKIP_BUILD=1 CAS_READY_TIMEOUT=420 ./deploy/docker/scripts/auth-harness.sh`
- `npm run build` in `himarket-web/himarket-admin`
- `npm run format -- --check src/components/portal/ThirdPartyAuthManager.tsx` was requested by docs but initially unavailable because the script was missing; package scripts have now been aligned so the command exists.
- direct admin export check for CAS service definition confirmed restored nested map fields
- direct startup-log check confirmed removal of `commons-logging`, `user_variables_by_thread`, `HHH000511`, `HHH90000025`, `open-in-view`, and Thymeleaf template warnings

## CONTRIBUTING Alignment
- commit messages kept in conventional format
- Java formatting enforced by Spotless before verification
- frontend build verified after UI-side changes earlier in branch history
- frontend packages now expose `npm run format` so the documented contribution workflow is executable
- public CAS service interfaces now carry concise Javadoc comments
- no incompatible hidden fallback path introduced; failures stay explicit
