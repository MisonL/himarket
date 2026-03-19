#!/bin/bash

resolve_java17_home() {
  local candidates=()
  local candidate=""

  if [[ -n "${JAVA_HOME:-}" ]]; then
    candidates+=("${JAVA_HOME}")
  fi

  if [[ -x "/usr/libexec/java_home" ]]; then
    candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "${candidate}" ]]; then
      candidates+=("${candidate}")
    fi
  fi

  candidates+=(
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  )

  for candidate in "${candidates[@]}"; do
    if [[ ! -x "${candidate}/bin/java" ]]; then
      continue
    fi

    local java_version_output
    local java_major
    java_version_output=$("${candidate}/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2)
    java_major=$(echo "${java_version_output}" | cut -d'.' -f1)
    if [[ "${java_major}" == "1" ]]; then
      java_major=$(echo "${java_version_output}" | cut -d'.' -f2)
    fi

    if [[ "${java_major}" == "17" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done

  return 1
}

ensure_java17() {
  local java_home
  java_home="$(resolve_java17_home)" || {
    echo "Error: JDK 17 is required. Install openjdk@17 or point JAVA_HOME to a JDK 17 home." >&2
    return 1
  }

  export JAVA_HOME="${java_home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
}

ensure_maven_wrapper() {
  if [[ ! -x "./mvnw" ]]; then
    echo "Error: ./mvnw is required and must be executable." >&2
    return 1
  fi
}

ensure_node18() {
  if ! command -v node >/dev/null 2>&1; then
    echo "Error: node is required." >&2
    return 1
  fi

  local node_version_output
  local node_major
  node_version_output="$(node -v 2>/dev/null)"
  node_major="${node_version_output#v}"
  node_major="${node_major%%.*}"
  if [[ -z "${node_major}" || "${node_major}" -lt 18 ]]; then
    echo "Error: Node.js 18 or newer is required, but found ${node_version_output}." >&2
    return 1
  fi
}
