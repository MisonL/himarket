import path from "path";
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
const env = loadEnv(process.env.NODE_ENV || "development", process.cwd(), "");
const apiPrefix = env.VITE_API_BASE_URL;
const tempApiUrl = env.VITE_TEMP_API_URL || "http://localhost:8080";

export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: ["babel-plugin-react-compiler"],
      },
    }),
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    host: "0.0.0.0",
    allowedHosts: true,
    port: 5173,
    proxy: {
      "/ws/acp": {
        target: tempApiUrl,
        ws: true,
        changeOrigin: true,
      },
      "/ws/terminal": {
        target: tempApiUrl,
        ws: true,
        changeOrigin: true,
      },
      [apiPrefix]: {
        target: tempApiUrl,
        changeOrigin: true,
        rewrite: p => p.replace(new RegExp(`^${apiPrefix}`), ""),
      },
    },
  },
  optimizeDeps: {},
  build: {
    chunkSizeWarningLimit: 1500,
    minify: "terser",
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) {
            return;
          }

          if (
            id.includes("swagger-ui-react") ||
            id.includes("swagger-client") ||
            id.includes("js-yaml")
          ) {
            return "swagger-vendor";
          }

          if (
            id.includes("react-markdown") ||
            id.includes("remark-gfm") ||
            id.includes("rehype-highlight") ||
            id.includes("highlight.js")
          ) {
            return "markdown-vendor";
          }

          if (
            id.includes("/antd/") ||
            id.includes("@ant-design") ||
            id.includes("rc-")
          ) {
            return "antd-vendor";
          }

          if (
            id.includes("/react/") ||
            id.includes("react-dom") ||
            id.includes("react-router")
          ) {
            return "react-vendor";
          }
        },
      },
    },
  },
});
