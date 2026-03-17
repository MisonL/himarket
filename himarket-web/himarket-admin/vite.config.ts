import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const env = loadEnv(process.env.NODE_ENV || "development", process.cwd(), "");
const apiPrefix = env.VITE_API_BASE_URL || "/api/v1";

function manualChunks(id: string) {
  if (!id.includes("node_modules")) {
    return undefined;
  }

  if (id.includes("swagger-ui-react")) {
    return "vendor-swagger-ui";
  }

  if (id.includes("@ant-design/icons")) {
    return "vendor-antd-icons";
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

  if (id.includes("echarts")) {
    return "vendor-echarts";
  }

  if (id.includes("rc-table") || id.includes("rc-pagination")) {
    return "vendor-antd-table";
  }

  if (id.includes("rc-picker") || id.includes("dayjs")) {
    return "vendor-antd-date";
  }

  if (
    id.includes("rc-select") ||
    id.includes("rc-tree-select") ||
    id.includes("rc-cascader")
  ) {
    return "vendor-antd-select";
  }

  if (id.includes("rc-field-form") || id.includes("async-validator")) {
    return "vendor-antd-form";
  }

  if (id.includes("antd")) {
    return "vendor-antd-core";
  }

  return undefined;
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5174,
    proxy: {
      [apiPrefix]: {
        target: "http://localhost:8080",
        changeOrigin: true,
        rewrite: path => path.replace(new RegExp(`^${apiPrefix}`), ""),
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
        entryFileNames: "index.js",
        chunkFileNames: "chunk-[name].js",
        assetFileNames: "assets/[name].[ext]",
        manualChunks,
      },
    },
  },
  define: {
    "process.env": {},
  },
});
