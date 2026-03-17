# Current State Review - 2026-03-16

## Scope
- Branch: `feature/cas-auth-complete`
- Baseline: `feature/cas-auth-support`
- Review basis:
  - `CONTRIBUTING.md`
  - `CONTRIBUTING_zh.md`
  - current branch code and local auth harness

## Control Contract
- Primary Setpoint:
  - branch behavior, contribution contract, and local gate evidence must describe the same system truth.
- Acceptance:
  - `mvn spotless:check`
  - `mvn -pl himarket-server -am -Dtest=IdpServiceImplTest,CasServiceImplTest,AdminCasServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `npm run build` in `himarket-web/himarket-admin`
  - `SKIP_BUILD=1 SKIP_DOCKER_UP=1 CAS_READY_TIMEOUT=420 ./deploy/docker/scripts/auth-harness.sh`
- Guardrails:
  - no hidden fallback path
  - no change to existing CAS redirect and code exchange semantics for browser providers
  - no new dual-truth between portal JSON config, admin env config, service-definition export, and runtime gate
- Known Delay:
  - local `apereo/cas` cold start can exceed 200 seconds on the current arm64/qemu path

## State Estimate
- Remote sync truth as of `2026-03-16`:
  - `origin/main` is an ancestor of `origin/feature/cas-auth-complete`
  - `origin/feature/cas-auth-support` is an ancestor of `origin/feature/cas-auth-complete`
  - therefore syncing the other two remote branches into `complete` is currently a no-op and should not create an empty merge commit
- The branch has already closed the main CAS integration set:
  - CAS1
  - CAS2
  - CAS3 JSON
  - SAML1
  - gateway
  - renew
  - warn
  - rememberMe
  - proxy ticket
  - front-channel logout
  - back-channel logout
  - MFA
  - delegated authentication
  - service-definition export
- The latest branch delta also closes `HEADER response type`, but only for CAS-aware clients. This is not an interactive browser login mode.

## Findings
### Fixed
1. `CONTRIBUTING` and frontend package scripts drifted.
   - Fact:
     - docs required `npm run format`
     - both frontend packages lacked a `format` script
   - Correction:
     - added `format` to both frontend package manifests
2. New CAS service interfaces lacked contribution-level API documentation.
   - Fact:
     - `CasService` and `AdminCasService` exposed public methods without concise Javadoc
   - Correction:
     - added short Javadoc comments for the public CAS service surface
3. `HEADER response type` had a false capability surface.
   - Fact:
     - config and export model exposed `HEADER`
     - backend validation rejected it
   - Correction:
     - runtime now supports it as a non-interactive CAS-aware client mode
     - browser provider lists filter it out
     - local CAS registry and harness both cover it

### Active Design Risks
1. The auth harness is now the primary gate, but it is concentrated in one large shell script.
   - Control-plane view:
     - orchestration, state setup, assertions, and recovery are coupled in one file
   - Risk:
     - MTTR rises when one protocol path breaks because the failing point is embedded inside a long sequence
2. CAS service-definition export is the branch's shared truth source, but the runtime login surfaces still infer some behavior indirectly.
   - Example:
     - interactive provider filtering depends on response type semantics rather than an explicit provider capability contract
   - Risk:
     - future response modes or non-browser clients may reintroduce dual-truth behavior
3. Local CAS runtime has high cold-start delay and repeated service-registry reloads.
   - Risk:
     - first-run failures can still be caused by upstream readiness windows rather than business regressions

## Top-level Optimization
### Control Topology
- Control plane:
  - contribution rules
  - config validation
  - service-definition export
  - local harness orchestration
- Data plane:
  - `/authorize`
  - `/callback`
  - `/exchange`
  - `/proxy-ticket`
  - logout flows
- State plane:
  - `AuthSessionStore`
  - CAS session bindings
  - login codes
  - token revocation state

### Complexity Transfer Ledger
- Original complexity location:
  - browser callback logic and ad hoc environment assumptions
- New location:
  - backend callback and exchange flow
  - explicit session store
  - heavier local harness
- Benefit:
  - runtime truth is centralized in backend and local gate
- New cost:
  - harness maintenance cost is higher
  - local CAS startup delay is now a visible delivery constraint
- New failure mode:
  - gate noise from upstream CAS readiness can look like app regressions

### Recommended Next Optimization
1. Split `deploy/docker/scripts/auth-harness.sh` into protocol-focused phases.
   - Target:
     - `cas-core`
     - `cas-saml1`
     - `cas-header`
     - `cas-mfa`
     - `cas-delegated`
     - `ldap`
     - `jwt-bearer`
   - Benefit:
     - lower MTTR
     - lower review cost
     - easier local reruns
2. Introduce an explicit provider capability contract in `IdpResult`.
   - Target:
     - separate `interactiveBrowserLogin` from `sloEnabled`
     - avoid inferring UI behavior from runtime implementation details
3. Add a small readiness hysteresis for local CAS gate.
   - Target:
     - require both `Ready to process requests` and stable loaded service count before starting the first login path
   - Benefit:
     - reduce false negatives caused by upstream service-registry reload timing

## Verification Notes
- This review does not claim that every possible upstream CAS plugin is covered.
- It does claim that the branch's declared integration surface and local gate are now materially aligned.
