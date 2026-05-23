import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const currentDir = dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  root: currentDir,
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(currentDir, 'src'),
    },
  },
  build: {
    rollupOptions: {
      input: 'index.html',
    },
  },
  server: {
    proxy: {
      '/apex-api': {
        target: 'http://localhost:18083',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/apex-api/, '/api'),
      },
    },
  },
})
