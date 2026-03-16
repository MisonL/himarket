import { createHash, createSign, generateKeyPairSync, randomBytes } from "crypto";
import { createServer } from "http";
import { URL } from "url";

const issuer = process.env.OIDC_ISSUER || "http://mock-oidc.local:8092";
const port = Number(process.env.OIDC_PORT || "8092");
const clientId = process.env.OIDC_CLIENT_ID || "cas-delegated-client";
const clientSecret = process.env.OIDC_CLIENT_SECRET || "cas-delegated-secret";

const users = new Map([
  [
    "alice",
    {
      password: process.env.OIDC_USER_ALICE_PASSWORD || "alice",
      sub: "oidc-alice",
      preferred_username: "alice",
      email: "alice@example.org",
      name: "Alice"
    }
  ],
  [
    "admin",
    {
      password: process.env.OIDC_USER_ADMIN_PASSWORD || "admin",
      sub: "oidc-admin",
      preferred_username: "admin",
      email: "admin@example.org",
      name: "Admin"
    }
  ]
]);

const authCodes = new Map();
const accessTokens = new Map();

const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const publicJwk = publicKey.export({ format: "jwk" });
const privateJwk = privateKey.export({ format: "jwk" });
const jwkThumbprint = createHash("sha256").update(JSON.stringify({
  e: publicJwk.e,
  kty: publicJwk.kty,
  n: publicJwk.n
})).digest("base64url");
publicJwk.use = "sig";
publicJwk.alg = "RS256";
publicJwk.kid = jwkThumbprint;
privateJwk.kid = jwkThumbprint;

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    "Content-Type": "application/json",
    "Content-Length": Buffer.byteLength(body)
  });
  res.end(body);
}

function sendHtml(res, statusCode, html) {
  res.writeHead(statusCode, {
    "Content-Type": "text/html; charset=utf-8",
    "Content-Length": Buffer.byteLength(html)
  });
  res.end(html);
}

function redirect(res, location) {
  res.writeHead(302, { Location: location });
  res.end();
}

function parseBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", chunk => {
      data += chunk;
    });
    req.on("end", () => resolve(new URLSearchParams(data)));
    req.on("error", reject);
  });
}

function buildAuthorizePage(url, errorMessage = "") {
  const hiddenFields = ["client_id", "redirect_uri", "response_type", "scope", "state", "nonce"]
    .map(name => `<input type="hidden" name="${name}" value="${escapeHtml(url.searchParams.get(name) || "")}">`)
    .join("");
  const errorBlock = errorMessage ? `<p style="color:#b91c1c">${escapeHtml(errorMessage)}</p>` : "";
  return `<!doctype html>
<html lang="en">
  <body>
    <h1>Mock OIDC Login</h1>
    ${errorBlock}
    <form method="post" action="/authorize">
      ${hiddenFields}
      <label>Username <input type="text" name="username" value=""></label>
      <label>Password <input type="password" name="password" value=""></label>
      <button type="submit">Login</button>
    </form>
  </body>
</html>`;
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function requireClient(params, authorizationHeader) {
  const basic = authorizationHeader?.startsWith("Basic ") ? Buffer.from(authorizationHeader.slice(6), "base64").toString("utf8") : "";
  const [basicId = "", basicSecret = ""] = basic.split(":");
  const providedClientId = params.get("client_id") || basicId;
  const providedClientSecret = params.get("client_secret") || basicSecret;
  return providedClientId === clientId && providedClientSecret === clientSecret;
}

function signJwt(payload) {
  const header = { alg: "RS256", kid: privateJwk.kid, typ: "JWT" };
  const signingInput = `${Buffer.from(JSON.stringify(header)).toString("base64url")}.${Buffer.from(JSON.stringify(payload)).toString("base64url")}`;
  const signer = createSign("RSA-SHA256");
  signer.update(signingInput);
  signer.end();
  const signature = signer.sign(privateKey).toString("base64url");
  return `${signingInput}.${signature}`;
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url || "/", issuer);

  if (req.method === "GET" && url.pathname === "/.well-known/openid-configuration") {
    sendJson(res, 200, {
      issuer,
      authorization_endpoint: `${issuer}/authorize`,
      token_endpoint: `${issuer}/token`,
      userinfo_endpoint: `${issuer}/userinfo`,
      jwks_uri: `${issuer}/jwks`,
      response_types_supported: ["code"],
      subject_types_supported: ["public"],
      id_token_signing_alg_values_supported: ["RS256"],
      scopes_supported: ["openid", "profile", "email"],
      token_endpoint_auth_methods_supported: ["client_secret_basic", "client_secret_post"],
      claims_supported: ["sub", "preferred_username", "email", "name"]
    });
    return;
  }

  if (req.method === "GET" && url.pathname === "/jwks") {
    sendJson(res, 200, { keys: [publicJwk] });
    return;
  }

  if (req.method === "GET" && url.pathname === "/authorize") {
    if (url.searchParams.get("client_id") !== clientId) {
      sendHtml(res, 400, "Unknown client");
      return;
    }
    sendHtml(res, 200, buildAuthorizePage(url));
    return;
  }

  if (req.method === "POST" && url.pathname === "/authorize") {
    const params = await parseBody(req);
    const username = params.get("username") || "";
    const password = params.get("password") || "";
    const user = users.get(username);
    const redirectUri = params.get("redirect_uri") || "";
    const state = params.get("state") || "";
    if (params.get("client_id") !== clientId || !redirectUri) {
      sendHtml(res, 400, "Invalid authorization request");
      return;
    }
    if (!user || user.password !== password) {
      const failedUrl = new URL(`${issuer}/authorize`);
      for (const [key, value] of params.entries()) {
        if (key !== "password") {
          failedUrl.searchParams.set(key, value);
        }
      }
      sendHtml(res, 401, buildAuthorizePage(failedUrl, "Invalid username or password"));
      return;
    }
    const code = randomBytes(24).toString("hex");
    authCodes.set(code, {
      clientId,
      redirectUri,
      nonce: params.get("nonce") || "",
      scope: params.get("scope") || "openid profile email",
      user
    });
    const redirectUrl = new URL(redirectUri);
    redirectUrl.searchParams.set("code", code);
    if (state) {
      redirectUrl.searchParams.set("state", state);
    }
    redirect(res, redirectUrl.toString());
    return;
  }

  if (req.method === "POST" && url.pathname === "/token") {
    const params = await parseBody(req);
    if (!requireClient(params, req.headers.authorization)) {
      sendJson(res, 401, { error: "invalid_client" });
      return;
    }
    if (params.get("grant_type") !== "authorization_code") {
      sendJson(res, 400, { error: "unsupported_grant_type" });
      return;
    }
    const code = params.get("code") || "";
    const redirectUri = params.get("redirect_uri") || "";
    const context = authCodes.get(code);
    if (!context || context.redirectUri !== redirectUri) {
      sendJson(res, 400, { error: "invalid_grant" });
      return;
    }
    authCodes.delete(code);
    const accessToken = randomBytes(24).toString("hex");
    accessTokens.set(accessToken, context.user);
    const now = Math.floor(Date.now() / 1000);
    const idToken = signJwt({
      iss: issuer,
      sub: context.user.sub,
      aud: clientId,
      iat: now,
      exp: now + 300,
      nonce: context.nonce,
      preferred_username: context.user.preferred_username,
      email: context.user.email,
      name: context.user.name
    });
    sendJson(res, 200, {
      access_token: accessToken,
      token_type: "Bearer",
      expires_in: 300,
      scope: context.scope,
      id_token: idToken
    });
    return;
  }

  if (req.method === "GET" && url.pathname === "/userinfo") {
    const authorization = req.headers.authorization || "";
    if (!authorization.startsWith("Bearer ")) {
      sendJson(res, 401, { error: "invalid_token" });
      return;
    }
    const user = accessTokens.get(authorization.slice("Bearer ".length));
    if (!user) {
      sendJson(res, 401, { error: "invalid_token" });
      return;
    }
    sendJson(res, 200, {
      sub: user.sub,
      preferred_username: user.preferred_username,
      email: user.email,
      name: user.name
    });
    return;
  }

  sendJson(res, 404, { error: "not_found" });
});

server.listen(port, "0.0.0.0", () => {
  console.log(`mock oidc listening on ${issuer}`);
});
