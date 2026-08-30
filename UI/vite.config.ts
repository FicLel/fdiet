import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// The Spring backend serves on 5000 (SERVER_PORT). Proxying keeps the browser
// on one origin, so no CORS configuration is needed on the Java side. Point
// FDIET_API elsewhere to develop against a backend on another port.
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    proxy: {
      '/api': {
        target: process.env.FDIET_API ?? 'http://localhost:5000',
        changeOrigin: true,
      },
    },
  },
})
