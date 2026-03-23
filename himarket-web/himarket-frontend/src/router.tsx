import { Suspense, lazy, type ReactNode, useEffect } from "react";
import {
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { RequireAuth } from "./components/RequireAuth";
import { usePortalConfig } from "./context/PortalConfigContext";

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
const Coding = lazy(() => import("./pages/Coding"));
const SkillDetail = lazy(() => import("./pages/SkillDetail"));

function withSuspense(node: ReactNode) {
  return <Suspense fallback={null}>{node}</Suspense>;
}

function DynamicHome() {
  const { firstVisiblePath } = usePortalConfig();
  return <Navigate to={firstVisiblePath} replace />;
}

function MenuRedirectGuard() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isMenuVisible, firstVisiblePath, loading } = usePortalConfig();

  useEffect(() => {
    if (loading) return;

    const pathToKeyMap: Record<string, string> = {
      "/chat": "chat",
      "/coding": "coding",
      "/agents": "agents",
      "/mcp": "mcp",
      "/models": "models",
      "/apis": "apis",
      "/skills": "skills",
    };

    const menuKey = pathToKeyMap[location.pathname];
    if (menuKey && !isMenuVisible(menuKey)) {
      navigate(firstVisiblePath, { replace: true });
    }
  }, [firstVisiblePath, isMenuVisible, loading, location.pathname, navigate]);

  return null;
}

export function Router() {
  return (
    <>
      <MenuRedirectGuard />
      <Routes>
        <Route path="/" element={<DynamicHome />} />
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
        <Route
          path="/skills"
          element={withSuspense(<Square activeType="AGENT_SKILL" />)}
        />
        <Route
          path="/skills/:skillProductId"
          element={withSuspense(<SkillDetail />)}
        />
        <Route path="/chat" element={withSuspense(<Chat />)} />
        <Route path="/quest" element={<Navigate to="/coding" />} />
        <Route path="/coding" element={withSuspense(<Coding />)} />
        <Route
          path="/getting-started"
          element={withSuspense(<GettingStarted />)}
        />
        <Route
          path="/apis/:apiProductId"
          element={withSuspense(<ApiDetail />)}
        />
        <Route
          path="/consumers/:consumerId"
          element={withSuspense(
            <RequireAuth>
              <ConsumerDetail />
            </RequireAuth>
          )}
        />
        <Route
          path="/consumers"
          element={withSuspense(
            <RequireAuth>
              <Consumers />
            </RequireAuth>
          )}
        />
        <Route
          path="/mcp/:mcpProductId"
          element={withSuspense(<McpDetail />)}
        />
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
        <Route
          path="/profile"
          element={withSuspense(
            <RequireAuth>
              <Profile />
            </RequireAuth>
          )}
        />
        <Route path="/callback" element={withSuspense(<Callback />)} />
        <Route path="/cas/callback" element={withSuspense(<CasCallback />)} />
        <Route
          path="/oidc/callback"
          element={withSuspense(<OidcCallback />)}
        />

        {/* 其他页面可继续添加 */}
      </Routes>
    </>
  );
}
