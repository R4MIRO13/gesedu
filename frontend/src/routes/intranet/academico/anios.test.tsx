import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { AnioResumen } from '../../../modulos/academico/api'
import { sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const ANIOS: AnioResumen[] = [
  { id: 'a28', anio: 2028, fechaInicio: '2028-02-14', fechaFin: '2028-12-20', estado: 'PLANIFICADO' },
  { id: 'a27', anio: 2027, fechaInicio: '2027-02-15', fechaFin: '2027-12-20', estado: 'ACTIVO' },
  { id: 'a26', anio: 2026, fechaInicio: '2026-03-02', fechaFin: '2026-12-18', estado: 'CERRADO' },
]

describe('Años escolares (RF-03)', () => {
  beforeEach(() => servidor.use(http.get('*/api/v1/anios-escolares', () => HttpResponse.json(ANIOS))))

  it('lista los años con su estado y ofrece crear uno nuevo al directivo', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['ACADEMICO_CONFIGURAR', 'ACADEMICO_CONSULTAR']))
    await renderizarRuta('/intranet/academico/anios')

    const tabla = await screen.findByRole('table', { name: 'Años escolares' })
    expect(tabla).toHaveTextContent('15/02/2027')
    expect(tabla).toHaveTextContent('Activo')
    expect(tabla).toHaveTextContent('Planificado')
    expect(tabla).toHaveTextContent('Cerrado')
    expect(screen.getByRole('link', { name: 'Nuevo año escolar' })).toHaveAttribute('href', '/intranet/academico/anio-escolar/nuevo')
  })

  it('quien solo consulta no ve el botón de crear', async () => {
    guardarSesion(sesionDe('DOCENTE', ['ACADEMICO_CONSULTAR']))
    await renderizarRuta('/intranet/academico/anios')

    await screen.findByRole('table', { name: 'Años escolares' })
    expect(screen.queryByRole('link', { name: 'Nuevo año escolar' })).not.toBeInTheDocument()
  })
})
