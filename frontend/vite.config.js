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
  // Vitest 設定。各測試檔仍以 // @vitest-environment jsdom 個別指定環境，
  // 故此處不設全域 environment，只加覆蓋率輸出（lcov 供 SonarQube 讀取）。
  test: {
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: './coverage',
      exclude: [
        'src/main.jsx',
        'src/test/**',
        'src/assets/**',
        '**/*.config.js',
      ],
    },
  },
})
