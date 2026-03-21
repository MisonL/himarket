const http = require("node:http");
const { randomUUID } = require("node:crypto");

const port = Number.parseInt(process.env.PORT || "3000", 10);
const allowedServers = new Set(
  (process.env.MCP_SERVER_NAMES || "hm-mcp-key,hm-mcp-hmac,hm-mcp-jwt")
    .split(",")
    .map((name) => name.trim())
    .filter(Boolean),
);
const sessions = new Map();

function buildTools(serverName) {
  return [
    {
      name: "server_info",
      description: "Return the current mock MCP server metadata.",
      inputSchema: {
        type: "object",
        properties: {},
        additionalProperties: false,
      },
      _handler: () => ({
        serverName,
        transport: "streamable_http",
        status: "ok",
      }),
    },
    {
      name: "echo",
      description: "Echo the provided message back to the caller.",
      inputSchema: {
        type: "object",
        properties: {
          message: {
            type: "string",
            description: "Message to echo back.",
          },
        },
        required: ["message"],
        additionalProperties: false,
      },
      _handler: (args) => `[${serverName}] ${args.message}`,
    },
    {
      name: "sum",
      description: "Add two numbers together.",
      inputSchema: {
        type: "object",
        properties: {
          left: {
            type: "number",
            description: "Left operand.",
          },
          right: {
            type: "number",
            description: "Right operand.",
          },
        },
        required: ["left", "right"],
        additionalProperties: false,
      },
      _handler: (args) => args.left + args.right,
    },
  ];
}

function jsonRpcResponse(id, result) {
  return {
    jsonrpc: "2.0",
    id: id ?? null,
    result,
  };
}

function jsonRpcError(id, code, message) {
  return {
    jsonrpc: "2.0",
    id: id ?? null,
    error: {
      code,
      message,
    },
  };
}

function hasMessageId(message) {
  return Object.prototype.hasOwnProperty.call(message || {}, "id") && message.id != null;
}

function writeJson(res, statusCode, payload, extraHeaders) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    "Content-Type": "application/json",
    "Content-Length": Buffer.byteLength(body),
    ...extraHeaders,
  });
  res.end(body);
}

function writeNoContent(res, statusCode) {
  res.writeHead(statusCode, {
    "Content-Length": "0",
  });
  res.end();
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
    });
    req.on("end", () => {
      try {
        resolve(body ? JSON.parse(body) : null);
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

function getSessionId(req) {
  const sessionId = req.headers["mcp-session-id"];
  return Array.isArray(sessionId) ? sessionId[0] : sessionId;
}

function getSession(sessionId) {
  if (!sessionId) {
    return null;
  }
  return sessions.get(sessionId) || null;
}

function closeSessionStream(session) {
  if (session?.streamRes && !session.streamRes.writableEnded) {
    session.streamRes.end();
  }
  if (session) {
    session.streamRes = null;
  }
}

function handleInitialize(serverName, req, res, message) {
  const sessionId = randomUUID();
  sessions.set(sessionId, { serverName, streamRes: null });

  const protocolVersion =
    message?.params?.protocolVersion ||
    req.headers["mcp-protocol-version"] ||
    "2025-11-25";

  writeJson(
    res,
    200,
    jsonRpcResponse(message.id, {
      protocolVersion,
      capabilities: {
        tools: {
          listChanged: false,
        },
      },
      serverInfo: {
        name: `${serverName}-mock`,
        version: "1.0.0",
      },
    }),
    {
      "Mcp-Session-Id": sessionId,
    },
  );
}

function handleToolsList(serverName, res, message) {
  const tools = buildTools(serverName).map((tool) => ({
    name: tool.name,
    description: tool.description,
    inputSchema: tool.inputSchema,
  }));
  writeJson(res, 200, jsonRpcResponse(message.id, { tools }));
}

function handleToolsCall(serverName, res, message) {
  const toolName = message?.params?.name;
  const tool = buildTools(serverName).find((item) => item.name === toolName);
  if (!tool) {
    writeJson(res, 200, jsonRpcError(message.id, -32601, `Tool not found: ${toolName}`));
    return;
  }

  const value = tool._handler(message?.params?.arguments || {});
  writeJson(
    res,
    200,
    jsonRpcResponse(message.id, {
      content: [
        {
          type: "text",
          text: typeof value === "string" ? value : JSON.stringify(value, null, 2),
        },
      ],
    }),
  );
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  console.log(`${new Date().toISOString()} ${req.method} ${url.pathname}`);

  if (req.method === "GET" && url.pathname === "/health") {
    writeJson(res, 200, {
      status: "ok",
      servers: Array.from(allowedServers),
    });
    return;
  }

  const serverName = url.pathname.replace(/^\/+/, "");
  if (!allowedServers.has(serverName)) {
    writeJson(res, 404, jsonRpcError(null, -32000, `Unknown MCP server: ${serverName}`));
    return;
  }

  if (req.method === "GET") {
    const sessionId = getSessionId(req);
    const session = getSession(sessionId);
    if (!session || session.serverName !== serverName) {
      writeJson(res, 404, jsonRpcError(null, -32001, "Unknown or expired session"));
      return;
    }

    closeSessionStream(session);
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
      "Mcp-Session-Id": sessionId,
    });
    res.write(": connected\n\n");
    session.streamRes = res;
    req.on("close", () => {
      if (session.streamRes === res) {
        session.streamRes = null;
      }
    });
    return;
  }

  if (req.method === "DELETE") {
    const sessionId = getSessionId(req);
    const session = getSession(sessionId);
    if (session) {
      closeSessionStream(session);
      sessions.delete(sessionId);
    }
    writeNoContent(res, 204);
    return;
  }

  if (req.method !== "POST") {
    writeNoContent(res, 405);
    return;
  }

  let message;
  try {
    message = await readJsonBody(req);
  } catch (_error) {
    writeJson(res, 400, jsonRpcError(null, -32700, "Invalid JSON request body"));
    return;
  }

  if (!message || Array.isArray(message)) {
    writeJson(res, 400, jsonRpcError(null, -32600, "Batch requests are not supported"));
    return;
  }

  console.log(
    `${new Date().toISOString()} rpc method=${message.method || "<unknown>"} id=${
      hasMessageId(message) ? message.id : "<none>"
    }`,
  );

  const sessionId = getSessionId(req);
  if (message.method === "initialize") {
    handleInitialize(serverName, req, res, message);
    return;
  }

  const session = getSession(sessionId);
  if (!session || session.serverName !== serverName) {
    writeJson(res, 404, jsonRpcError(message.id, -32001, "Unknown or expired session"));
    return;
  }

  if (message.method === "notifications/initialized") {
    writeNoContent(res, 204);
    return;
  }

  if (message.method === "ping") {
    if (!hasMessageId(message)) {
      writeNoContent(res, 202);
      return;
    }
    writeJson(res, 200, jsonRpcResponse(message.id, {}));
    return;
  }

  if (message.method === "tools/list") {
    handleToolsList(serverName, res, message);
    return;
  }

  if (message.method === "tools/call") {
    handleToolsCall(serverName, res, message);
    return;
  }

  writeJson(res, 200, jsonRpcError(message.id, -32601, `Method not found: ${message.method}`));
});

server.listen(port, "0.0.0.0", () => {
  console.log(
    `himarket-mcp-mock listening on ${port} for ${Array.from(allowedServers).join(", ")}`,
  );
});
