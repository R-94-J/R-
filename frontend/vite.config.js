import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const proxy = {
  '/api': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
}
export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, strictPort: true, proxy },
  preview: { port: 4173, strictPort: true, proxy },
})
