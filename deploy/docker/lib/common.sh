#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKER_DIR="${SCRIPT_DIR}"
REPO_DIR="$(cd "${DOCKER_DIR}/../.." && pwd)"
DATA_DIR="${DOCKER_DIR}/data"
ENV_FILE="${DATA_DIR}/.env"
CAS_MODULES_DIR="${DOCKER_DIR}/auth/cas/modules"
CAS_MODULES_LIB_DIR="${CAS_MODULES_DIR}/lib"

ALL_PHASES=(
  bootstrap
  cas-core
  cas-header
  cas-mfa
  cas-delegated
  ldap
  jwt-bearer
)

SELECTED_PHASES=()
MOCK_OIDC_PID=""

log() { echo "[auth-harness $(date +'%H:%M:%S')] $*"; }
err() { echo "[auth-harness][ERROR] $*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { err "missing command: $1"; exit 1; }
}

COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/common" && pwd)"

# shellcheck disable=SC1091
source "${COMMON_DIR}/http.sh"
# shellcheck disable=SC1091
source "${COMMON_DIR}/mock-oidc.sh"
# shellcheck disable=SC1091
source "${COMMON_DIR}/phases.sh"
# shellcheck disable=SC1091
source "${COMMON_DIR}/runtime.sh"
