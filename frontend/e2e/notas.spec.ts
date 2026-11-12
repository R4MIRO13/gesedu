import { expect, test } from '@playwright/test'
import { CUENTAS, iniciarSesion } from './apoyo'

// UC-15 con Fernando (docente2@), que dicta Matemática en 3° A. preparar-datos.sql deja las dos celdas C3 sin calificar.
test('el docente registra las notas por competencia que faltaban (UC-15)', async ({ page }) => {
  await iniciarSesion(page, CUENTAS.docente2)
  await page
    .getByRole('navigation', { name: 'Principal' })
    .getByRole('link', { name: 'Registro de notas', exact: true })
    .click()
  await page.getByRole('link', { name: 'Registrar notas de Matemática en 3° A' }).click()

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    'Registro de notas · Matemática · 3° A · Bimestre I',
  )
  await expect(page.getByText('22/24 celdas completas')).toBeVisible()
  await expect(page.getByRole('status')).toContainText('Mendoza Ruiz Joaquín (C3) · Paredes Vega Camila (C3)')

  await page.getByRole('combobox', { name: 'Mendoza Ruiz, Joaquín · C3' }).selectOption('A')
  await page.getByRole('combobox', { name: 'Paredes Vega, Camila · C3' }).selectOption('AD')
  await page.getByRole('button', { name: 'Guardar registro' }).click()

  await expect(page.getByRole('status')).toContainText('Se guardó el registro: 2 celdas.')
  await expect(page.getByText('24/24 celdas completas')).toBeVisible()

  // Consulta de la consolidación de su curso: notas finales por competencia
  await page
    .getByRole('navigation', { name: 'Principal' })
    .getByRole('link', { name: 'Registro de notas', exact: true })
    .click()
  await page.getByRole('link', { name: 'Notas finales de Matemática en 3° A' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Notas finales · Matemática · 3° A')
  await expect(page.getByRole('button', { name: 'Calcular notas finales' })).toBeVisible()
})
