#!/usr/bin/env bash

CONTEXT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/context" && pwd)"

# shellcheck disable=SC1091
source "${CONTEXT_DIR}/admin.sh"
# shellcheck disable=SC1091
source "${CONTEXT_DIR}/portal.sh"
# shellcheck disable=SC1091
source "${CONTEXT_DIR}/service-definitions.sh"

prepare_auth_phase_context() {
  ensure_admin_token
  ensure_localhost_portal
  configure_portal_auth
  verify_portal_cas_service_definitions
}
