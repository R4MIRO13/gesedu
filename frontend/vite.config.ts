import { tanstackRouter } from '@tanstack/router-plugin/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  // Las pruebas usan routeTree.gen.ts ya generado: el plugin de rutas solo hace falta al desarrollar y compilar
  plugins: [...(process.env.VITEST ? [] : [tanstackRouter({ target: 'react', autoCodeSplitting: true })]), react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/compartido/**', 'src/modulos/**'],
      exclude: ['src/**/*.gen.ts', 'src/test/**'],
    },
  },
})
