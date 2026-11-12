import { expect, test } from '@playwright/test'
import { CUENTAS, iniciarSesion } from './apoyo'

// RF-52, UC-24: inicio y cierre de sesión
test.describe('Inicio de sesión', () => {
  test('con credenciales correctas entra a la intranet y Salir vuelve al login', async ({ page }) => {
    await iniciarSesion(page, CUENTAS.admin)
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola, Carmen')

    await page.getByRole('button', { name: 'Salir' }).click()

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
  })

  test('con credenciales incorrectas muestra un mensaje genérico y no entra', async ({ page }) => {
    await page.goto('/login')
    // Una cuenta que no existe y distinta en cada corrida: no suma intentos a ninguna cuenta real, y el backend también
    // «bloquea» los correos inexistentes al quinto intento para no revelar cuáles existen (BloqueoFicticioTest)
    await page.getByRole('textbox', { name: 'Correo' }).fill(`no.existe.${Date.now()}@demo.gesedu.pe`)
    await page.getByLabel('Contraseña').fill('Incorrecta123!')
    await page.getByRole('button', { name: 'Ingresar' }).click()

    await expect(page.getByRole('alert')).toContainText('Correo o contraseña incorrectos.')
    await expect(page).toHaveURL(/\/login$/)
  })
})
