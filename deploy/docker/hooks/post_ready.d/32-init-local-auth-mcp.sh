#!/usr/bin/env bash

set -euo pipefail

HIGRESS_PASSWORD="${HIGRESS_PASSWORD:-admin}"
HIGRESS_HOST="${HIGRESS_HOST:-localhost:8001}"
LOCAL_MCP_SERVICE_NAME="${LOCAL_MCP_SERVICE_NAME:-hm-mcp-svc-local}"
LOCAL_MCP_SERVICE_DOMAIN="${LOCAL_MCP_SERVICE_DOMAIN:-host.docker.internal}"
LOCAL_MCP_SERVICE_PORT="${LOCAL_MCP_SERVICE_PORT:-8093}"

SESSION_COOKIE=""
API_RESPONSE=""
API_HTTP_CODE=""

log() { echo "[init-local-auth-mcp $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

call_higress_api() {
  local method="$1"
  local path="$2"
  local data="${3:-}"

  local result
  if [[ -n "$data" ]]; then
    result=$(curl -sS -w "\nHTTP_CODE:%{http_code}" -X "$method" "http://${HIGRESS_HOST}${path}" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json, text/plain, */*" \
      -b "$SESSION_COOKIE" \
      --data "$data")
  else
    result=$(curl -sS -w "\nHTTP_CODE:%{http_code}" -X "$method" "http://${HIGRESS_HOST}${path}" \
      -H "Accept: application/json, text/plain, */*" \
      -b "$SESSION_COOKIE")
  fi

  API_HTTP_CODE=$(printf "%s" "$result" | sed -n 's/.*HTTP_CODE:\([0-9][0-9][0-9]\).*/\1/p' | tail -1)
  API_RESPONSE=$(printf "%s" "$result" | sed '/HTTP_CODE:/d')
}

login_higress() {
  local response
  response=$(curl -sS -i -X POST "http://${HIGRESS_HOST}/session/login" \
    -H "Content-Type: application/json" \
    --data "{\"username\":\"admin\",\"password\":\"${HIGRESS_PASSWORD}\"}")

  SESSION_COOKIE=$(printf "%s" "$response" | awk -F'_hi_sess=|;' '/Set-Cookie:/ {print $2; exit}')
  if [[ -z "$SESSION_COOKIE" ]]; then
    err "failed to login Higress"
    exit 1
  fi
  SESSION_COOKIE="_hi_sess=${SESSION_COOKIE}"
}

ensure_service_source() {
  local desired
  desired=$(jq -n \
    --arg name "$LOCAL_MCP_SERVICE_NAME" \
    --arg domain "$LOCAL_MCP_SERVICE_DOMAIN" \
    --arg protocol "http" \
    --arg port "$LOCAL_MCP_SERVICE_PORT" \
    '{
      type: "dns",
      name: $name,
      port: ($port | tonumber),
      protocol: $protocol,
      domain: $domain
    }')

  call_higress_api "GET" "/v1/service-sources/${LOCAL_MCP_SERVICE_NAME}"
  if [[ "$API_HTTP_CODE" != "200" ]]; then
    call_higress_api "POST" "/v1/service-sources" "$desired"
  fi

  if [[ ! "$API_HTTP_CODE" =~ ^2[0-9]{2}$ ]]; then
    err "failed to ensure service source ${LOCAL_MCP_SERVICE_NAME}: HTTP ${API_HTTP_CODE}"
    err "$API_RESPONSE"
    exit 1
  fi
}

ensure_mcp_server() {
  local name="$1"
  local description="$2"
  local auth_enabled="$3"
  local auth_type="$4"
  local desired

  desired=$(jq -n \
    --arg name "$name" \
    --arg description "$description" \
    --arg service "${LOCAL_MCP_SERVICE_NAME}.dns" \
    --arg path "/${name}" \
    --arg authEnabled "$auth_enabled" \
    --arg authType "$auth_type" \
    --arg port "$LOCAL_MCP_SERVICE_PORT" \
    '{
      id: null,
      name: $name,
      description: $description,
      domains: [],
      services: [
        {
          name: $service,
          port: ($port | tonumber),
          version: null,
          weight: 100
        }
      ],
      type: "DIRECT_ROUTE",
      consumerAuthInfo: (
        if $authType == ""
        then null
        else {
          type: $authType,
          enable: ($authEnabled == "true"),
          allowedConsumers: []
        }
        end
      ),
      rawConfigurations: null,
      dbConfig: null,
      dbType: null,
      directRouteConfig: {
        path: $path,
        transportType: "streamable"
      },
      mcpServerName: $name
    }')

  call_higress_api "GET" "/v1/mcpServer/${name}"
  if [[ "$API_HTTP_CODE" == "200" ]]; then
    local current update
    current="$API_RESPONSE"
    update=$(jq -n --argjson current "$current" --argjson desired "$desired" '
      ($current.data // {}) as $c |
      (($desired.consumerAuthInfo // $c.consumerAuthInfo // {
        type: "key-auth",
        enable: false,
        allowedConsumers: []
      })) as $auth |
      ($c.consumerAuthInfo // {}) as $cAuth |
      $desired + {
        consumerAuthInfo: {
          type: ($auth.type // "key-auth"),
          enable: ($auth.enable // false),
          allowedConsumers: ($cAuth.allowedConsumers // $auth.allowedConsumers // [])
        }
      }')
  else
    update="$desired"
  fi

  call_higress_api "PUT" "/v1/mcpServer" "$update"
  if [[ ! "$API_HTTP_CODE" =~ ^2[0-9]{2}$ ]]; then
    err "failed to ensure mcp server ${name}: HTTP ${API_HTTP_CODE}"
    err "$API_RESPONSE"
    exit 1
  fi
}

main() {
  log "sync local auth MCP fixtures"
  login_higress
  ensure_service_source
  ensure_mcp_server "hm-mcp-key" "hm mcp key auth" "true" "key-auth"
  ensure_mcp_server "hm-mcp-hmac" "hm mcp hmac auth" "false" ""
  ensure_mcp_server "hm-mcp-jwt" "hm mcp jwt auth" "false" ""
  log "local auth MCP fixtures ready"
}

main "$@"
