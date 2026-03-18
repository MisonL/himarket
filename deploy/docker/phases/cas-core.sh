#!/usr/bin/env bash

CAS_CORE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/cas-core" && pwd)"

# shellcheck disable=SC1091
source "${CAS_CORE_DIR}/developer.sh"
# shellcheck disable=SC1091
source "${CAS_CORE_DIR}/service-definitions.sh"
# shellcheck disable=SC1091
source "${CAS_CORE_DIR}/admin.sh"

phase_cas_core() {
  verify_cas_authorize_flags developer "http://localhost:8081/developers/cas/authorize?provider=cas&gateway=true&warn=true&rememberMe=true"
  verify_cas_authorize_flags admin "http://localhost:8081/admins/cas/authorize?provider=cas&gateway=true&warn=true&rememberMe=true"

  verify_developer_cas_protocol_flows
  verify_admin_cas_service_definitions
  verify_admin_cas_protocol_flows
}
