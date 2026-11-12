import { expect, test } from '@playwright/test'
import { CUENTAS, iniciarSesion } from './apoyo'

// UC-07 y UC-08 con Rosa (docente@, tutora de 3° A), el lunes 15/03/2027 del reloj de la demo.
// preparar-datos.sql deja sin registrar su marcación y el pase de lista de ese día.
test.describe('Registro de asistencia', () => {
  test.describe.configure({ mode: 'serial' })

  test('la docente marca su asistencia con la hora del servidor (UC-07)', async ({ page }) => {
    await iniciarSesion(page, CUENTAS.docente)
    await page
      .getByRole('navigation', { name: 'Principal' })
      .getByRole('link', { name: 'Mi asistencia', exact: true })
      .click()
    await expect(page.getByText('Lunes 15/03/2027')).toBeVisible()

    await page.getByRole('button', { name: 'Registrar asistencia' }).click()

    await expect(page.getByRole('status')).toContainText(
      /Asistencia registrada a las .+: (Puntual|Tardanza)\./,
    )
    await expect(page.getByRole('button', { name: 'Registrar asistencia' })).toBeDisabled()
  })

  test('la tutora pasa lista a 3° A con Puntual, Tardanza y Falta (UC-08)', async ({ page }) => {
    await iniciarSesion(page, CUENTAS.docente)
    await page
      .getByRole('navigation', { name: 'Principal' })
      .getByRole('link', { name: 'Asistencia de mi sección', exact: true })
      .click()
    await expect(page).toHaveURL(/\/intranet\/asistencia\/3A\/registrar$/)
    const lista = page.getByRole('table', { name: 'Alumnos de la sección' })
    await expect(lista.getByRole('group')).toHaveCount(6)

    await page.getByRole('button', { name: 'Marcar todos como Puntual' }).click()
    await lista
      .getByRole('group', { name: /Castro Soto/ })
      .getByRole('radio', { name: 'Tardanza' })
      .check()
    await lista
      .getByRole('group', { name: /Mendoza Ruiz/ })
      .getByRole('radio', { name: 'Falta' })
      .check()
    await page.getByRole('button', { name: 'Guardar asistencia' }).click()

    await expect(page.getByRole('status')).toContainText(
      'Asistencia registrada: 4 puntuales, 1 tardanza y 1 falta.',
    )
    await page.getByRole('link', { name: 'Ver el registro del día' }).click()
    await expect(page.getByText('Jornada abierta')).toBeVisible()
  })
})
