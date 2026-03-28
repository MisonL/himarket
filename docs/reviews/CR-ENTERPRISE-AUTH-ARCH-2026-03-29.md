# Enterprise Auth Architecture Study

## Context

- Research branch: `research/enterprise-auth-elegant`
- Study date: `2026-03-29`
- Reference issue: `higress-group/himarket#156`
- Reference PRs:
  - `QuantumNous/new-api#3447` JWT Direct SSO phase 1
  - `QuantumNous/new-api#3467` Trusted Header Enterprise SSO phase 2

## Control Contract

- Primary Setpoint:
  - Define a cleaner enterprise auth direction for HiMarket on top of current `main`, with phase 1 at least covering the JWT Direct capability set from `new-api#3447`.
- Acceptance:
  - A research branch exists from current `main`.
  - A written architecture study captures current-state inventory, external reference capabilities, gap analysis, and a staged implementation proposal.
- Guardrails:
  - Do not break current `main`, `feature/cas-auth-support`, or `feature/cas-auth-complete`.
  - Do not silently weaken already working CAS and LDAP paths.
  - Prefer additive design over another round of protocol-specific branching inside the existing CAS model.
  - Any later Java implementation on top of this study must conform to `.claude/skills/java-coding-standards/SKILL.md`, especially clarity-first naming, short focused methods, immutable-by-default data flow, and avoiding broad catch blocks.
- Boundary:
  - This branch only adds research documentation.
  - No production Java, frontend, or harness behavior changes in this round.

## Current Main Branch Facts

Current `main` is not a clean pre-auth baseline. The recent merge history shows:

- `313d41dd` `merge(complete): integrate full cas capabilities into main`
- `5f6c5324` `merge(support): integrate cas jwt support into main`

This means `main` already contains both the support path and the full CAS path.

From code and local auth harness configuration, current `main` exposes at least these enterprise auth families:

1. OIDC auth code
   - Config validation exists in [IdpService.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/IdpService.java) and [IdpServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/IdpServiceImpl.java).
   - Runtime flow exists in [OidcServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/OidcServiceImpl.java).

2. OAuth2 JWT Bearer
   - Runtime flow exists in [OAuth2ServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/OAuth2ServiceImpl.java).
   - Current data model is still `OAuth2Config + JwtBearerConfig`, see [OAuth2Config.java](/Volumes/Work/code/himarket/himarket-dal/src/main/java/com/alibaba/himarket/support/portal/OAuth2Config.java) and [JwtBearerConfig.java](/Volumes/Work/code/himarket/himarket-dal/src/main/java/com/alibaba/himarket/support/portal/JwtBearerConfig.java).

3. CAS protocol family
   - CAS config model is already heavy and protocol-rich, see [CasConfig.java](/Volumes/Work/code/himarket/himarket-dal/src/main/java/com/alibaba/himarket/support/portal/CasConfig.java).
   - Runtime flows exist in [CasServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/CasServiceImpl.java) and [AdminCasServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/AdminCasServiceImpl.java).
   - Validation and protocol helpers exist in [CasTicketValidator.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/idp/CasTicketValidator.java), `CasProxyTicketClient`, `CasLogoutRequestParser`, and `CasSamlTicketValidationParser`.

4. LDAP login
   - Config and validation exist in [LdapConfig.java](/Volumes/Work/code/himarket/himarket-dal/src/main/java/com/alibaba/himarket/support/portal/LdapConfig.java) and [IdpServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/IdpServiceImpl.java).
   - Runtime flows exist in [LdapServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/LdapServiceImpl.java) and [AdminLdapServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/AdminLdapServiceImpl.java).

From the local auth harness, current `main` portal auth context declares 9 provider slots in [portal.sh](/Volumes/Work/code/himarket/deploy/docker/lib/context/portal.sh):

- `cas`
- `cas-saml1`
- `cas1`
- `cas2`
- `cas-header`
- `cas-mfa`
- `cas-delegated`
- `ldap`
- `jwt-bearer`

And the harness phase layout covers:

- `cas-core`
- `cas-header`
- `cas-mfa`
- `cas-delegated`
- `ldap`
- `jwt-bearer`

See [cas-core.sh](/Volumes/Work/code/himarket/deploy/docker/phases/cas-core.sh), [cas-header.sh](/Volumes/Work/code/himarket/deploy/docker/phases/cas-header.sh), [cas-mfa.sh](/Volumes/Work/code/himarket/deploy/docker/phases/cas-mfa.sh), [cas-delegated.sh](/Volumes/Work/code/himarket/deploy/docker/phases/cas-delegated.sh), [ldap.sh](/Volumes/Work/code/himarket/deploy/docker/phases/ldap.sh), and [jwt-bearer.sh](/Volumes/Work/code/himarket/deploy/docker/phases/jwt-bearer.sh).

## Support Branch Facts

`feature/cas-auth-support` is not a fresh minimal architecture branch anymore. Relative to current `main`, it still differs in 32 files and carries significant auth-common changes.

Observed diff stats:

- `32` files changed
- `1549` insertions
- `625` deletions

The branch still touches:

- portal resolving
- JWT filter and token utility
- CAS session storage
- CAS services
- LDAP authenticator
- local auth harness
- admin frontend swagger wrapper files

That means `support` is no longer just "three small features". It is already coupled to the current CAS-oriented architecture.

From actual local joint verification already completed before this study, the support branch has at least these verified functional paths:

1. LDAP login
   - Developer and admin LDAP login passed local harness verification.

2. CAS Header SSO
   - Developer and admin header-based CAS response flow passed local harness verification.

3. JWT Bearer token exchange
   - Developer-side JWT bearer token exchange passed local harness verification.

However, the branch structure still carries broader CAS implementation weight because it shares the same protocol-oriented model family.

## Reference PR Capability Summary

### PR 3447: JWT Direct SSO Phase 1

Source facts from the PR body in `/tmp/newapi-pr-3447.html`:

- Adds backend and frontend flow for `JWT Direct SSO`
- Supports browser login and direct JWT login
- Extends two ticket-to-JWT acquisition modes:
  - `ticket_exchange`
  - `ticket_validate`
- Supports two identity extraction modes:
  - `claims`
  - `userinfo`
- Tightens callback security and bind-flow safety checks

This PR is architecturally important because it treats enterprise auth as a provider kind plus acquisition/mapping modes, instead of exploding protocol-specific provider names into application core.

### PR 3467: Trusted Header Phase 2

Source facts from the PR body in `/tmp/newapi-pr-3467.html`:

- Adds `Trusted Header Enterprise SSO` login and bind flow
- Builds on top of an `external identity pipeline`
- Adds trusted-proxy verification
- Adds header-based identity extraction
- Adds group/role mapping and sync
- Expands tests and session/bind error handling

This PR shows a second-stage extension pattern:

- phase 1: JWT Direct
- phase 2: Trusted Header

The important architectural trait is that both modes share one external identity path instead of creating separate protocol silos.

## Architectural Comparison

### Current HiMarket Main

Strengths:

- Already supports many real enterprise protocols and modes.
- Has working local auth harness and broad validation surface.
- Developer-side external identity creation already exists in [DeveloperServiceImpl.java](/Volumes/Work/code/himarket/himarket-server/src/main/java/com/alibaba/himarket/service/impl/DeveloperServiceImpl.java).

Problems:

- Auth abstraction is protocol-first, especially CAS-first.
- One `CasConfig` mixes login flags, validation protocol, proxy, service definition, access strategy, attribute release, MFA, auth policy, expiration policy, and contacts.
- "Lightweight support" and "full CAS capability" are not structurally isolated.
- Application logic, identity orchestration, and CAS service-definition export all leak into the same feature surface.

### Reference PR Direction

Strengths:

- Auth abstraction is identity-ingress-first.
- Complexity is staged:
  - phase 1 JWT Direct
  - phase 2 Trusted Header
- External identity pipeline is reused.
- Application core consumes normalized identity results instead of owning every enterprise protocol detail.

Problems relative to issue `#156`:

- JWT Direct and Trusted Header do not equal native CAS protocol compatibility.
- They are better as platform architecture, but not as a direct answer to "please connect to my raw CAS server without an adapter layer".

## Key Decision

For a cleaner long-term enterprise auth architecture in HiMarket, the better path is:

- do not continue reshaping `feature/cas-auth-support`
- start from current `main`
- introduce a new enterprise-auth abstraction layer
- phase 1 must at least include the `JWT Direct` capability set

Reason:

`support` is already heavily coupled to the CAS protocol model. Refactoring it into JWT Direct and Trusted Header would be a directional rewrite, not a small extension.

## Proposed Elegant Direction

### Phase 0: Abstract the ingress model

Introduce a new enterprise auth abstraction centered on "how the application receives a trusted identity":

1. `OIDC_AUTH_CODE`
2. `JWT_DIRECT`
3. `TRUSTED_HEADER`
4. `LDAP_PASSWORD`
5. `CAS_PROTOCOL`

This should separate:

- identity ingress
- token acquisition
- identity mapping
- local account binding
- session issuance

from protocol-specific details.

### Phase 1: Minimum capability set to include

At minimum, the new branch should include the phase 1 capability set inspired by PR `#3447`:

1. Browser login entry for JWT Direct
2. Direct JWT login entry
3. `ticket_exchange` acquisition mode
4. `ticket_validate` acquisition mode
5. `claims` mapping mode
6. `userinfo` mapping mode
7. callback allowlist / safe callback construction
8. bind/login flow error hardening

This phase should target the developer portal first because HiMarket already has a working developer external identity pipeline.

### Phase 2: Trusted Header

After JWT Direct is stable:

1. add trusted proxy CIDR / host allowlist
2. add header-based identity extraction
3. add optional group / role mapping and sync
4. add developer-side bind/login flow reuse

### Phase 3: Admin external identity unification

Current admin auth still relies on a separate built-in administrator model. For JWT Direct and Trusted Header to become first-class admin features, HiMarket likely needs either:

- an `AdminExternalIdentity` model, or
- a more generic principal external identity abstraction

without mixing that work into phase 1.

## Recommended Branch Strategy

Recommended new implementation branch:

- `feature/enterprise-auth-jwt-direct`

Reason:

- It states the first actual deliverable.
- It avoids promising phase 2 before phase 1 is stable.
- It keeps the branch goal smaller than "rewrite all enterprise auth".

Current research branch:

- `research/enterprise-auth-elegant`

This branch should stay documentation-first until the concrete phase 1 schema and controller contract are frozen.

## Minimal File Impact Prediction

If phase 1 starts from current `main`, the likely smallest write set is:

- `himarket-dal`
  - enterprise auth config model extensions or new provider-kind model

- `himarket-server`
  - new developer-side JWT Direct controller/service
  - reuse of external developer binding
  - config validation and callback guardrails

- `himarket-web/himarket-frontend`
  - provider discovery and JWT Direct browser callback UX

- `deploy/docker`
  - local JWT Direct harness phase

This is still a meaningful feature, but structurally smaller than trying to untangle the CAS-first shape of `support`.

## Final Recommendation

1. Treat current `main` as the real baseline, because `main` already contains both support and complete auth capability history.
2. Do not attempt to morph `feature/cas-auth-support` into the new elegant architecture.
3. Start the elegant route from current `main`.
4. Make phase 1 explicitly target `JWT Direct`.
5. Keep `Trusted Header` as a second phase after the JWT Direct contract is stable.
6. Keep native CAS compatibility as a separate compatibility track, not the core architecture spine.
