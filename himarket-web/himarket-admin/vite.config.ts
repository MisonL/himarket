import react from "@vitejs/plugin-react";
import path from "path";
import { fileURLToPath } from "url";
import { defineConfig, loadEnv } from "vite";
import monacoEditorModule from "vite-plugin-monaco-editor";

const monacoEditor = (monacoEditorModule as any).default || monacoEditorModule;
const __dirname = path.dirname(fileURLToPath(import.meta.url));

function sanitizeChunkName(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, "-");
}

function getAntdComponentName(id: string) {
  const match = id.match(/node_modules\/antd\/(?:es|lib)\/([^/]+)/);
  if (!match) {
    return "shared";
  }
  const componentName = match[1];
  if (
    componentName === "index" ||
    componentName === "index.js" ||
    componentName === "_util" ||
    componentName === "style" ||
    componentName === "theme" ||
    componentName === "locale" ||
    componentName === "config-provider"
  ) {
    return "shared";
  }
  return componentName;
}

function manualChunks(id: string) {
  if (!id.includes("node_modules")) {
    return undefined;
  }

  if (
    id.includes("monaco-editor") ||
    id.includes("react-monaco-editor") ||
    id.includes("react-markdown-editor-lite")
  ) {
    return "vendor-monaco";
  }
  if (
    id.includes("react-markdown") ||
    id.includes("remark-gfm") ||
    id.includes("js-yaml")
  ) {
    return "vendor-markdown";
  }

  if (id.includes("swagger-ui-react/swagger-ui-es-bundle-core.js")) {
    return "vendor-swagger-core";
  }

  if (
    id.includes("swagger-ui-react/index.mjs") ||
    id.includes("swagger-ui-react/index.cjs")
  ) {
    return "vendor-swagger-react";
  }

  if (
    id.includes("swagger-client") ||
    id.includes("@swagger-api/apidom") ||
    id.includes("@swaggerexpert")
  ) {
    return "vendor-swagger-support";
  }

  if (
    id.includes("immutable") ||
    id.includes("react-redux") ||
    id.includes("redux")
  ) {
    return "vendor-swagger-state";
  }

  if (id.includes("echarts")) {
    return "vendor-echarts";
  }

  if (id.includes("zrender")) {
    return "vendor-zrender";
  }
  if (id.includes("antd")) {
    return `vendor-antd-${sanitizeChunkName(getAntdComponentName(id))}`;
  }

  if (id.includes("@ant-design/icons")) {
    return "vendor-antd-icons";
  }

  if (id.includes("react-dom")) {
    return "vendor-react-dom";
  }

  if (id.includes("@remix-run/router") || id.includes("react-router")) {
    return "vendor-react-router";
  }

  if (id.includes("axios")) {
    return "vendor-axios";
  }

  if (id.includes("dayjs")) {
    return "vendor-dayjs";
  }

  return undefined;
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiPrefix = env.VITE_API_BASE_URL || "/api/v1";

  return {
    plugins: [react(), monacoEditor({})],
    server: {
      host: "0.0.0.0",
      port: 5174,
      proxy: {
        [apiPrefix]: {
          target: "http://localhost:8080",
          changeOrigin: true,
          rewrite: pathValue =>
            pathValue.replace(new RegExp(`^${apiPrefix}`), ""),
        },
      },
    },
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    build: {
      rollupOptions: {
        output: {
          entryFileNames: "assets/[name]-[hash].js",
          chunkFileNames: "assets/chunk-[name]-[hash].js",
          assetFileNames: "assets/[name]-[hash].[ext]",
          manualChunks,
        },
      },
    },
    define: {
      "process.env": {},
    },
  };
});
