import { expect, test, type Page } from '@playwright/test'
import { CUENTAS, iniciarSesion, irA } from './apoyo'

// RNF-02: las pantallas de cada rol en 375, 768 y 1280 px sin desbordamiento horizontal de la página (las tablas y la
// matriz de horario se desplazan dentro de su propio contenedor) y el menú plegable bajo 768 px.
const ANCHOS = [375, 768, 1280] as const

const RUTAS = {
  admin: [
    '/intranet/inicio',
    '/intranet/horario',
    '/intranet/auditoria',
    '/intranet/usuarios',
    '/intranet/roles',
    '/intranet/academico/anios',
    '/intranet/academico/anio-escolar/nuevo',
    '/intranet/academico/consolidar-notas',
    '/intranet/academico/2027/cursos',
    '/intranet/academico/2027/secciones',
    '/intranet/asistencia/reportes',
    '/intranet/asistencia/docentes/2027-03-15',
    '/intranet/horarios/bloques',
    '/intranet/horarios/2027/configuracion',
    '/intranet/horarios/2027/elaborar',
    '/intranet/horarios/2027/publicar',
    '/intranet/matriculas',
    '/intranet/pagos/configuracion',
    '/intranet/pagos/validar',
    '/intranet/pagos/historial',
    '/intranet/estudiantes',
  ],
  docente: [
    '/intranet/inicio',
    '/intranet/horario',
    '/intranet/disponibilidad/2027',
    '/intranet/mi-asistencia',
    '/intranet/asistencia/3A/registrar',
    '/intranet/asistencia/3A/registro-del-dia',
    '/intranet/asistencia/reportes',
    '/intranet/notas',
    '/intranet/notas/matematica/3B/bimestre-1',
    '/intranet/cursos/matematica-3B/configuracion',
    '/intranet/notas/matematica/3B/finales',
  ],
  apoderado: ['/intranet/inicio', '/intranet/horario', '/intranet/notas', '/intranet/pagos'],
} as const

async function desbordes(page: Page) {
  return page.evaluate(() => {
    const ancho = document.documentElement.clientWidth
    return {
      exceso: document.documentElement.scrollWidth - ancho,
      culpables: [...document.querySelectorAll('main *')]
        .filter(
          (e) =>
            e.getBoundingClientRect().right > ancho + 1 && !e.closest('.tabla-scroll, [class*="matriz"]'),
        )
        .slice(0, 3)
        .map((e) => `${e.tagName.toLowerCase()}.${String(e.className).slice(0, 40)}`),
    }
  })
}

test.describe('Pantallas responsive (RNF-02)', () => {
  for (const [rol, rutas] of Object.entries(RUTAS) as [keyof typeof RUTAS, readonly string[]][]) {
    test(`${rol}: sin desbordamiento horizontal en ${ANCHOS.join(', ')} px`, async ({ page }) => {
      test.setTimeout(180_000)
      await iniciarSesion(page, CUENTAS[rol])
      const problemas: string[] = []
      for (const ancho of ANCHOS) {
        await page.setViewportSize({ width: ancho, height: 900 })
        for (const ruta of rutas) {
          await irA(page, ruta)
          await expect(page.getByRole('main')).not.toContainText('Cargando…')
          const { exceso, culpables } = await desbordes(page)
          if (exceso > 0 || culpables.length > 0)
            problemas.push(`${ancho} px ${ruta}: ${exceso} px de más ${culpables.join(' ')}`)
        }
      }
      expect(problemas).toEqual([])
    })
  }

  test('bajo 768 px el menú se pliega tras el botón ☰ y se cierra al navegar', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await iniciarSesion(page, CUENTAS.docente)
    const boton = page.getByRole('button', { name: 'Menú' })
    const menu = page.getByRole('navigation', { name: 'Principal' })

    await expect(boton).toBeVisible()
    await expect(boton).toHaveAttribute('aria-expanded', 'false')
    await expect(menu).toBeHidden()
    const alto = (await boton.boundingBox())?.height ?? 0
    expect(alto, 'objetivo táctil de al menos 44 px').toBeGreaterThanOrEqual(44)

    await boton.click()
    await expect(menu).toBeVisible()
    await expect(menu.getByRole('link', { name: 'Inicio', exact: true })).toBeFocused()
    await menu.getByRole('link', { name: 'Mi asistencia', exact: true }).click()

    await expect(page).toHaveURL(/\/intranet\/mi-asistencia$/)
    await expect(menu).toBeHidden()
    await expect(boton).toHaveAttribute('aria-expanded', 'false')

    await boton.click()
    await page.keyboard.press('Escape')
    await expect(menu).toBeHidden()
    await expect(boton).toBeFocused()
  })

  test('en 1280 px el menú lateral está siempre visible y no hay botón ☰', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await iniciarSesion(page, CUENTAS.docente)

    await expect(page.getByRole('navigation', { name: 'Principal' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Menú' })).toBeHidden()
  })
})
