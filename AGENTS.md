# Repository Guidelines

## Project Structure & Module Organization
- `himarket-server/`: Spring Boot service code in `src/main/java`, tests in `src/test/java`.
- `himarket-dal/`: persistence and shared data-access models.
- `himarket-bootstrap/`: packaging, runtime config, and container entrypoint logic.
- `himarket-web/himarket-admin/`: admin UI built with Vite + React.
- `himarket-web/himarket-frontend/`: portal UI built with Vite + React.
- `deploy/`: Docker, local auth harness, and environment wiring.
- `docs/reviews/`: branch audit reports and verification records.

## Build, Test, and Development Commands
- Backend package: `mvn -pl himarket-bootstrap -am package -DskipTests`
- Backend targeted tests: `mvn -pl himarket-server -am -Dtest=ClassNameTest -Dsurefire.failIfNoSpecifiedTests=false test`
- Admin UI dev/build: `cd himarket-web/himarket-admin && npm run dev` or `npm run build`
- Frontend dev/build: `cd himarket-web/himarket-frontend && npm run dev` or `npm run build`
- Local auth gate: `SKIP_BUILD=1 SKIP_DOCKER_UP=1 CAS_READY_TIMEOUT=420 ./deploy/docker/scripts/auth-harness.sh`

## Coding Style & Naming Conventions
- Java: keep formatting compatible with Spotless; prefer clear service-oriented names such as `CasServiceImpl`.
- React/TypeScript: components in `PascalCase.tsx`, utilities in `camelCase.ts`, route files under `src/pages/`.
- Use 2 spaces in frontend files when the file already follows that style; keep existing style per module.
- Run frontend formatting with `npm run format`; run lint with `npm run lint`.

## Testing Guidelines
- Java tests live under `src/test/java` and should use `*Test` suffix.
- Frontend changes must at least pass `npm run build`; add focused lint/format checks for touched files.
- For auth and integration work, prefer the Docker harness over mock-only validation.
- Record meaningful regression evidence in `docs/reviews/` when closing large feature branches.

## Commit & Pull Request Guidelines
- Follow the existing conventional pattern: `feat(admin): ...`, `refactor(harness): ...`, `perf(admin): ...`, `docs(review): ...`.
- Keep each atomic change in its own commit.
- PRs should include: scope summary, verification commands, affected modules, and screenshots for UI changes.

## Security & Configuration Tips
- Do not commit secrets; use env vars and local compose overrides.
- Read `CONTRIBUTING.md` and `CONTRIBUTING_zh.md` before broad changes.
- Treat `deploy/docker/scripts/auth-harness.sh` and CAS-related config as production-like gates, not disposable demo code.
