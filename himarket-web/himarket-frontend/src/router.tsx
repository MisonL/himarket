import { Suspense, lazy, type ReactNode } from "react";
import { Routes, Route, Navigate } from "react-router-dom";

const ApiDetail = lazy(() => import("./pages/ApiDetail"));
const Consumers = lazy(() => import("./pages/Consumers"));
const ConsumerDetail = lazy(() => import("./pages/ConsumerDetail"));
const GettingStarted = lazy(() => import("./pages/GettingStarted"));
const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const Profile = lazy(() => import("./pages/Profile"));
const McpDetail = lazy(() => import("./pages/McpDetail"));
const Agent = lazy(() => import("./pages/Agent"));
const AgentDetail = lazy(() => import("./pages/AgentDetail"));
const ModelDetail = lazy(() => import("./pages/ModelDetail"));
const Callback = lazy(() => import("./pages/Callback"));
const CasCallback = lazy(() => import("./pages/CasCallback"));
const OidcCallback = lazy(() => import("./pages/OidcCallback"));
const Square = lazy(() => import("./pages/Square"));
const Chat = lazy(() => import("./pages/Chat"));

function withSuspense(node: ReactNode) {
  return <Suspense fallback={null}>{node}</Suspense>;
}

export function Router() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/chat" />} />
      <Route
        path="/models"
        element={withSuspense(<Square activeType="MODEL_API" />)}
      />
      <Route
        path="/mcp"
        element={withSuspense(<Square activeType="MCP_SERVER" />)}
      />
      <Route
        path="/agents"
        element={withSuspense(<Square activeType="AGENT_API" />)}
      />
      <Route
        path="/apis"
        element={withSuspense(<Square activeType="REST_API" />)}
      />
      <Route path="/chat" element={withSuspense(<Chat />)} />
      <Route
        path="/getting-started"
        element={withSuspense(<GettingStarted />)}
      />
      <Route path="/apis/:apiProductId" element={withSuspense(<ApiDetail />)} />
      <Route
        path="/consumers/:consumerId"
        element={withSuspense(<ConsumerDetail />)}
      />
      <Route path="/consumers" element={withSuspense(<Consumers />)} />
      <Route path="/mcp/:mcpProductId" element={withSuspense(<McpDetail />)} />
      <Route path="/agents" element={withSuspense(<Agent />)} />
      <Route
        path="/agents/:agentProductId"
        element={withSuspense(<AgentDetail />)}
      />
      <Route
        path="/models/:modelProductId"
        element={withSuspense(<ModelDetail />)}
      />
      <Route path="/login" element={withSuspense(<Login />)} />
      <Route path="/register" element={withSuspense(<Register />)} />
      <Route path="/profile" element={withSuspense(<Profile />)} />
      <Route path="/callback" element={withSuspense(<Callback />)} />
      <Route path="/cas/callback" element={withSuspense(<CasCallback />)} />
      <Route path="/oidc/callback" element={withSuspense(<OidcCallback />)} />

      {/* 其他页面可继续添加 */}
    </Routes>
  );
}
