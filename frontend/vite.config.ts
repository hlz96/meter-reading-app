/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'node:path'

// 同源部署(前端 TRD §3):dev 用 proxy 打到后端 8080。
// 生产构建默认输出到本地 dist/(路径 B:交给 nginx 托管,后端零改动,推荐现网)。
// 路径 A(打进后端 jar 的 static 目录,需后端改 SecurityConfig + WebMvcConfigurer):
//   设 BUILD_TARGET=backend 时才写入 ../backend/src/main/resources/static。
const toBackendStatic = process.env.BUILD_TARGET === 'backend'

export default defineConfig({
  base: '/',
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'icons/apple-touch-icon.png'],
      manifest: {
        name: '水电表抄表管理',
        short_name: '抄表',
        description: '水电表抄表管理 App',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        theme_color: '#2563eb',
        background_color: '#ffffff',
        icons: [
          { src: 'icons/pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/pwa-512.png', sizes: '512x512', type: 'image/png' },
          {
            src: 'icons/pwa-512-maskable.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // 导航请求 SPA fallback 到 index.html,但排除 /api(前端 TRD §8)
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api/],
        runtimeCaching: [
          {
            // /api/** → NetworkOnly,保证数据实时正确
            urlPattern: /^\/api\//,
            handler: 'NetworkOnly',
          },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
    },
  },
  build: {
    // 默认 dist/(路径 B,nginx 托管);BUILD_TARGET=backend 时打进后端 static(路径 A)
    outDir: toBackendStatic ? '../backend/src/main/resources/static' : 'dist',
    emptyOutDir: true,
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    clearMocks: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})
