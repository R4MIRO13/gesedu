import { readFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'
import { CUENTAS, iniciarSesion } from './apoyo'

// UC-19 FA-1: el apoderado de dos hijos elige a Valeria (2° A de 2026, libreta FINAL de la semilla V1005) y la descarga.
test('el apoderado descarga la libreta en PDF (UC-19)', async ({ page }) => {
  await iniciarSesion(page, CUENTAS.familiaTorres)
  await page
    .getByRole('navigation', { name: 'Principal' })
    .getByRole('link', { name: 'Notas y libreta', exact: true })
    .click()

  const estudiante = page.getByRole('combobox', { name: 'Estudiante' })
  await estudiante.selectOption({
    label: await estudiante.locator('option', { hasText: 'Valeria' }).innerText(),
  })
  await expect(page.getByText('Libreta final')).toBeVisible()

  const [descarga] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Descargar libreta' }).click(),
  ])

  expect(descarga.suggestedFilename()).toMatch(/^libreta-2026-0404-2026\.pdf$/)
  const archivo = await descarga.path()
  const contenido = readFileSync(archivo)
  expect(contenido.length).toBeGreaterThan(1000)
  expect(contenido.subarray(0, 5).toString('latin1')).toBe('%PDF-')
})
