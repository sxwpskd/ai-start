import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools()
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/ai': {
        // 旧：本地后端（本地与服务器完全解耦后，本地无后端可代理）
        // target: 'http://localhost:8100',
        // 新：代理到服务器 nginx（80 端口），走与生产相同的 /ai 反代链路
        target: 'http://129.204.193.220',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})

