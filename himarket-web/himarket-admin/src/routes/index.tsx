import { Suspense, lazy, type ReactNode } from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";
import LayoutWrapper from "@/components/LayoutWrapper";

const Portals = lazy(() => import("@/pages/Portals"));
const ProductTypePage = lazy(() => import("@/pages/ProductTypePage"));
const ProductCategories = lazy(() => import("@/pages/ProductCategories"));
const ProductCategoryDetail = lazy(
  () => import("@/pages/ProductCategoryDetail")
);
const GatewayConsoles = lazy(() => import("@/pages/GatewayConsoles"));
const NacosConsoles = lazy(() => import("@/pages/NacosConsoles"));
const PortalDetail = lazy(() => import("@/pages/PortalDetail"));
const ApiProductDetail = lazy(() => import("@/pages/ApiProductDetail"));
const Login = lazy(() => import("@/pages/Login"));
const ModelDashboard = lazy(() => import("@/pages/ModelDashboard"));
const McpMonitor = lazy(() => import("@/pages/McpMonitor"));
const CasCallback = lazy(() => import("@/pages/CasCallback"));

function withSuspense(node: ReactNode) {
  return <Suspense fallback={null}>{node}</Suspense>;
}

export const router = createBrowserRouter([
  {
    path: "/login",
    element: withSuspense(<Login />),
  },
  {
    path: "/cas/callback",
    element: withSuspense(<CasCallback />),
  },
  {
    path: "/",
    element: <LayoutWrapper />,
    children: [
      {
        index: true,
        element: <Navigate to="/portals" replace />,
      },
      {
        path: "portals",
        element: withSuspense(<Portals />),
      },
      {
        path: "portals/:portalId",
        element: withSuspense(<PortalDetail />),
      },
      {
        path: "api-products",
        children: [
          {
            index: true,
            element: <Navigate to="/api-products/model-api" replace />,
          },
          {
            path: "model-api",
            element: withSuspense(<ProductTypePage productType="MODEL_API" />),
          },
          {
            path: "mcp-server",
            element: withSuspense(<ProductTypePage productType="MCP_SERVER" />),
          },
          {
            path: "agent-skill",
            element: withSuspense(<ProductTypePage productType="AGENT_SKILL" />),
          },
          {
            path: "worker",
            element: withSuspense(<ProductTypePage productType="WORKER" />),
          },
          {
            path: "agent-api",
            element: withSuspense(<ProductTypePage productType="AGENT_API" />),
          },
          {
            path: "rest-api",
            element: withSuspense(<ProductTypePage productType="REST_API" />),
          },
          {
            path: ":productId",
            element: withSuspense(<ApiProductDetail />),
          },
        ],
      },
      {
        path: "product-categories",
        element: withSuspense(<ProductCategories />),
      },
      {
        path: "product-categories/:categoryId",
        element: withSuspense(<ProductCategoryDetail />),
      },
      {
        path: "consoles",
        element: <Navigate to="/consoles/gateway" replace />,
      },
      {
        path: "consoles/gateway",
        element: withSuspense(<GatewayConsoles />),
      },
      {
        path: "consoles/nacos",
        element: withSuspense(<NacosConsoles />),
      },
      {
        path: "observability",
        element: <Navigate to="/observability/model-dashboard" replace />,
      },
      {
        path: "observability/model-dashboard",
        element: withSuspense(<ModelDashboard />),
      },
      {
        path: "observability/mcp-monitor",
        element: withSuspense(<McpMonitor />),
      },
      {
        path: "*",
        element: <Navigate to="/portals" replace />,
      },
    ],
  },
]);
