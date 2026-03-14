#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/lib/common.sh"
source "${SCRIPT_DIR}/lib/context.sh"
for phase_script in "${SCRIPT_DIR}"/phases/*.sh; do
  # shellcheck disable=SC1090
  source "${phase_script}"
done

has_selected_non_bootstrap_phases() {
  local phase
  for phase in "${SELECTED_PHASES[@]}"; do
    if [[ "${phase}" != "bootstrap" ]]; then
      return 0
    fi
  done
  return 1
}

main() {
  init_phase_selection
  bootstrap_runtime

  admin_user="${ADMIN_USER}"
  admin_pass="${ADMIN_PASS}"
  portal_name="${PORTAL_NAME}"
  frontend_redirect_url="${FRONTEND_REDIRECT_URL}"
  cas_http_port="${CAS_HTTP_PORT}"
  jwk_dir="${JWK_DIR}"
  export CAS_OIDC_DISCOVERY_URL="http://localhost:${CAS_HTTP_PORT}/cas/oidc/.well-known"

  if ! has_selected_non_bootstrap_phases; then
    log "all selected phases passed"
    return 0
  fi

  prepare_auth_phase_context

  local phase
  for phase in "${SELECTED_PHASES[@]}"; do
    if [[ "${phase}" == "bootstrap" ]]; then
      continue
    fi
    run_phase "${phase}"
  done

  log "all selected phases passed"
}

main "$@"
