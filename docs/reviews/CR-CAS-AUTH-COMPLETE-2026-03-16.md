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

### Residual Risks
1. There are still compile-time warnings from existing unrelated code paths (`APIGOperator`, `TalkSearchAbilityServiceGoogleImpl`, annotation-processing/module-path warnings). They are not introduced by this branch and were not changed in this review slice.
2. Local Docker image rebuild remains slow because the backend Dockerfile installs Arthas during image build. This is a productivity issue, not a functional correctness issue.

## Verification
- `mvn -pl himarket-server -am -Dtest=CasServiceDefinitionServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
- `mvn -pl himarket-bootstrap -am package -DskipTests`
- `docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build himarket-server` in `deploy/docker`
- `docker logs --since 2m himarket-server`
- `SKIP_BUILD=1 SKIP_DOCKER_UP=1 CAS_READY_TIMEOUT=420 ./deploy/docker/scripts/auth-harness.sh`
- direct admin export check for CAS service definition confirmed restored nested map fields

## CONTRIBUTING Alignment
- commit messages kept in conventional format
- Java formatting enforced by Spotless before verification
- frontend build verified after UI-side changes earlier in branch history
- no incompatible hidden fallback path introduced; failures stay explicit
