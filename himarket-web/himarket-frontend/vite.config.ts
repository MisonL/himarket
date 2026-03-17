import path from "path"
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import monacoEditorModule from 'vite-plugin-monaco-editor'

const monacoEditor = (monacoEditorModule as any).default || monacoEditorModule

// https://vite.dev/config/
const env = loadEnv(process.env.NODE_ENV || 'development', process.cwd(), '')
const apiPrefix = env.VITE_API_BASE_URL
const tempApiUrl = env.VITE_TEMP_API_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react({
    babel: {
      plugins: ['babel-plugin-react-compiler']
    }
  }), monacoEditor({})],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    host: '0.0.0.0',
    allowedHosts: true,
    port: 5173,
    proxy: {
      '/ws/acp': {
        target: tempApiUrl,
        ws: true,
        changeOrigin: true,
      },
      '/ws/terminal': {
        target: tempApiUrl,
        ws: true,
        changeOrigin: true,
      },
      [apiPrefix]: {
        target: tempApiUrl,
        changeOrigin: true,
        rewrite: (p) => p.replace(new RegExp(`^${apiPrefix}`), ''),
      },
    },
  },
  optimizeDeps: {
  },
  build: {
    chunkSizeWarningLimit: 1500,
    minify: 'terser',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('swagger-ui-react')) {
              if (id.includes('swagger-ui-es-bundle-core')) return 'vendor-swagger-core'
              return 'vendor-swagger-ui'
            }
            if (id.includes('@ant-design/icons')) return 'vendor-antd-icons'
            if (id.includes('antd')) return 'vendor-antd'
            if (id.includes('@xterm/xterm') || id.includes('@xterm/addon-fit')) return 'vendor-xterm'
            if (
              id.includes('react-markdown') ||
              id.includes('remark-gfm') ||
              id.includes('rehype-highlight') ||
              id.includes('highlight.js')
            ) {
              return 'vendor-markdown'
            }
            if (id.includes('react-router-dom')) return 'vendor-react-router'
            if (id.includes('react-dom')) return 'vendor-react-dom'
            if (id.includes('/react/')) return 'vendor-react'
            if (id.includes('lodash')) return 'vendor-lodash'
            if (id.includes('echarts')) return 'vendor-echarts'
            if (id.includes('zrender')) return 'vendor-zrender'
          }
        }
      }
    }
  },
  define: {
    'process.env': {}
  },
})
