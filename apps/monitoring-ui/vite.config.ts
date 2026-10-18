import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/api/telemetry': {
        target: process.env.VITE_TELEMETRY_API_PROXY_TARGET ?? 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api': {
        target: process.env.VITE_GRAPH_API_PROXY_TARGET ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
