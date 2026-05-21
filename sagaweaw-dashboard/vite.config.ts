import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In embedded mode (served by Spring Boot at /sagaweaw) set VITE_DASHBOARD_BASE=/sagaweaw/.
// In standalone dev mode (Vite port 8484) leave it unset — defaults to '/'.
const base = process.env.VITE_DASHBOARD_BASE ?? '/'

export default defineConfig({
  base,
  plugins: [react()],
  define: { global: 'globalThis' },
  server: {
    port: 8484,
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/sagaweaw-ws': {
        target: process.env.VITE_API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  optimizeDeps: {
    include: ['@stomp/stompjs', 'sockjs-client'],
  },
})
