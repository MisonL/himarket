#!/usr/bin/env bash

phase_registered() {
  local phase="$1"
  local registered
  for registered in "${ALL_PHASES[@]}"; do
    if [[ "${registered}" == "${phase}" ]]; then
      return 0
    fi
  done
  return 1
}

phase_selected() {
  local target="$1"
  local selected
  for selected in "${SELECTED_PHASES[@]}"; do
    if [[ "${selected}" == "${target}" ]]; then
      return 0
    fi
  done
  return 1
}

init_phase_selection() {
  local requested_raw="${AUTH_HARNESS_PHASES:-all}"
  local dedup=()
  local requested=()
  local phase
  if [[ "${requested_raw}" == "all" ]]; then
    SELECTED_PHASES=("${ALL_PHASES[@]}")
    return 0
  fi
  IFS=',' read -r -a requested <<<"${requested_raw}"
  for phase in "${requested[@]}"; do
    phase="${phase//[[:space:]]/}"
    [[ -z "${phase}" ]] && continue
    if ! phase_registered "${phase}"; then
      err "unknown harness phase: ${phase}"
      exit 1
    fi
    if [[ " ${dedup[*]-} " != *" ${phase} "* ]]; then
      dedup+=("${phase}")
    fi
  done
  if (( ${#dedup[@]} == 0 )); then
    err "AUTH_HARNESS_PHASES resolved to empty set"
    exit 1
  fi
  if [[ " ${dedup[*]} " != *" bootstrap "* ]]; then
    SELECTED_PHASES=(bootstrap "${dedup[@]}")
  else
    SELECTED_PHASES=("${dedup[@]}")
  fi
}

run_phase() {
  local phase="$1"
  local fn="phase_${phase//-/_}"
  if ! declare -f "${fn}" >/dev/null 2>&1; then
    err "missing phase implementation: ${phase}"
    exit 1
  fi
  log "run phase: ${phase}"
  "${fn}"
}
