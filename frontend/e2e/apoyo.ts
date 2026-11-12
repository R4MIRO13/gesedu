import { expect, type Page } from '@playwright/test'

/** Contraseña de todas las cuentas de la semilla de demo (docs/demo/guion.md). */
export const CONTRASENA = 'Gesedu2027!'

export const CUENTAS = {
  admin: 'admin@demo.gesedu.pe',
  docente: 'docente@demo.gesedu.pe',
  docente2: 'docente2@demo.gesedu.pe',
  alumno: 'alumno@demo.gesedu.pe',
  apoderado: 'apoderado@demo.gesedu.pe',
  familiaTorres: 'familia.torres@demo.gesedu.pe',
} as const

/** Inicia sesión desde /login y espera el inicio de la intranet. */
export async function iniciarSesion(page: Page, correo: string) {
  await page.goto('/login')
  await page.getByRole('textbox', { name: 'Correo' }).fill(correo)
  await page.getByLabel('Contraseña').fill(CONTRASENA)
  await page.getByRole('button', { name: 'Ingresar' }).click()
  await expect(page).toHaveURL(/\/intranet\/inicio$/)
}

/**
 * Navega dentro de la SPA sin recargar la página. Una recarga completa vuelve a pedir el refresh token, y el backend
 * limita las peticiones de autenticación a 30 por minuto por IP: con varias recargas seguidas las pruebas recibirían 429.
 */
export async function irA(page: Page, ruta: string) {
  await page.evaluate((destino) => {
    window.history.pushState({}, '', destino)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, ruta)
  await expect(page).toHaveURL(new RegExp(`${ruta.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`))
}
