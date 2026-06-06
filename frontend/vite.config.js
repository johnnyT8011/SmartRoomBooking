import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxy /api to the Spring Boot backend so the browser talks to a single origin during
// development (this also keeps the SSE connection on the same origin).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
