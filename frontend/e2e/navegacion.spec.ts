import { expect, test } from '@playwright/test'
import { CUENTAS, iniciarSesion } from './apoyo'

// RF-50: el menú se arma con los permisos de cada rol (la SPA solo oculta; el backend decide)
const ROLES = [
  {
    rol: 'Administrador',
    correo: CUENTAS.admin,
    saludo: 'Hola, Carmen',
    ve: ['Auditoría', 'Usuarios', 'Consolidar notas', 'Publicar horario'],
    noVe: ['Registro de notas', 'Notas y libreta'],
  },
  {
    rol: 'Docente',
    correo: CUENTAS.docente,
    saludo: 'Hola, Rosa',
    ve: ['Horario', 'Mi asistencia', 'Asistencia de mi sección', 'Registro de notas'],
    noVe: ['Auditoría', 'Usuarios', 'Notas y libreta'],
  },
  {
    rol: 'Apoderado',
    correo: CUENTAS.apoderado,
    saludo: 'Hola, María',
    ve: ['Horario', 'Notas y libreta', 'Matrícula y pagos'],
    noVe: ['Auditoría', 'Registro de notas', 'Asistencia de mi sección'],
  },
] as const

test.describe('Navegación por rol', () => {
  for (const r of ROLES) {
    test(`${r.rol}: inicio y menú según sus permisos`, async ({ page }) => {
      await iniciarSesion(page, r.correo)

      await expect(page.getByRole('heading', { level: 1 })).toContainText(r.saludo)
      const menu = page.getByRole('navigation', { name: 'Principal' })
      await expect(menu.getByRole('link', { name: 'Inicio', exact: true })).toBeVisible()
      for (const opcion of r.ve) {
        await expect(menu.getByRole('link', { name: opcion, exact: true })).toBeVisible()
      }
      for (const opcion of r.noVe) {
        await expect(menu.getByRole('link', { name: opcion, exact: true })).toHaveCount(0)
      }

      // Recorre una opción del menú y vuelve al inicio sin recargar
      await menu.getByRole('link', { name: r.ve[0], exact: true }).click()
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      await menu.getByRole('link', { name: 'Inicio', exact: true }).click()
      await expect(page).toHaveURL(/\/intranet\/inicio$/)
    })
  }
})
