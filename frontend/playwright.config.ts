import { defineConfig, devices } from '@playwright/test'

/**
 * Smoke tests de compatibilidad entre navegadores (RNF-04; docs/pruebas/compatibilidad.md). Corren contra la SPA en
 * http://localhost:5173 y el backend local con los perfiles local,demo, que se arrancan aparte (no entran en CI).
 *
 * Cada navegador tiene su proyecto de preparación (`preparar-<navegador>`), que ejecuta e2e/preparar-datos.sql sobre la
 * base de desarrollo: así los tres navegadores parten de los mismos datos. `npm run e2e` (e2e/ejecutar.mjs) corre los
 * navegadores uno detrás de otro, en serie y con un solo worker, para que no se pisen.
 */
const NAVEGADORES = [
  { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  { name: 'msedge', use: { ...devices['Desktop Edge'], channel: 'msedge' } },
] as const

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  outputDir: 'e2e/resultados/artefactos',
  reporter: [['list'], ['json', { outputFile: process.env.E2E_JSON ?? 'e2e/resultados/ultimo.json' }]],
  use: {
    baseURL: 'http://localhost:5173',
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: NAVEGADORES.flatMap((n) => [
    { name: `preparar-${n.name}`, testMatch: /preparar\.setup\.ts/, use: n.use },
    { name: n.name, testMatch: /.*\.spec\.ts/, use: n.use, dependencies: [`preparar-${n.name}`] },
  ]),
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
