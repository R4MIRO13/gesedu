import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { guardarSesion } from '../../compartido/auth/sesion'
import { problema, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

const ADMIN = sesionDe('ADMINISTRADOR', ['ACADEMICO_CONFIGURAR', 'ACADEMICO_CONSULTAR', 'COBRO_CONFIGURAR'])

describe('Año de trabajo = año activo de la API (C-47)', () => {
  it('«activo» en la URL se reemplaza por el año que informa el servidor', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/activo', () =>
        HttpResponse.json({
          id: 'a',
          anio: 2031,
          fechaInicio: '2031-03-01',
          fechaFin: '2031-12-20',
          estado: 'ACTIVO',
        }),
      ),
      http.get('*/api/v1/anios-escolares/2031/cursos', () =>
        HttpResponse.json({ anio: 2031, estado: 'ACTIVO', totalHoras: 0, cursos: [] }),
      ),
      http.get('*/api/v1/anios-escolares/2031/cursos/copia-anterior', () =>
        HttpResponse.json({ anioOrigen: null, cursos: [] }),
      ),
    )
    guardarSesion(ADMIN)
    const { router } = await renderizarRuta('/intranet/academico/activo/cursos')

    expect(await screen.findByText('0 cursos · 0 h semanales')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/intranet/academico/2031/cursos')
  })

  it('las pantallas sin año en la URL usan el año activo', async () => {
    let pedido = ''
    servidor.use(
      http.get('*/api/v1/anios-escolares/activo', () =>
        HttpResponse.json({
          id: 'a',
          anio: 2031,
          fechaInicio: '2031-03-01',
          fechaFin: '2031-12-20',
          estado: 'ACTIVO',
        }),
      ),
      http.get('*/api/v1/anios-escolares/:anio/cobros', ({ params }) => {
        pedido = String(params.anio)
        return problema(404, 'RECURSO_NO_ENCONTRADO', 'Sin cobros')
      }),
    )
    guardarSesion(ADMIN)
    await renderizarRuta('/intranet/pagos/configuracion')

    await screen.findByRole('alert')
    expect(pedido).toBe('2031')
  })

  it('sin año activo la pantalla explica qué falta', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/activo', () =>
        problema(404, 'RECURSO_NO_ENCONTRADO', 'No hay un año escolar activo'),
      ),
    )
    guardarSesion(ADMIN)
    await renderizarRuta('/intranet/pagos/configuracion')

    expect(await screen.findByText(/No hay un año escolar activo/)).toBeInTheDocument()
  })
})
