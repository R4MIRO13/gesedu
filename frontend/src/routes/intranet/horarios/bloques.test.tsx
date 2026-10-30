import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const BLOQUES = [
  { id: 'b1', diaSemana: 'LUNES', horaInicio: '07:45', horaFin: '08:30' },
  { id: 'b2', diaSemana: 'LUNES', horaInicio: '08:30', horaFin: '09:15' },
]

describe('Bloques horarios (RF-46)', () => {
  beforeEach(() => servidor.use(http.get('*/api/v1/anios-escolares/2027/bloques', () => HttpResponse.json(BLOQUES))))

  it('el administrador elimina un bloque que nadie usa', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['HORARIO_CONSULTAR', 'HORARIO_CONFIGURAR']))
    servidor.use(http.delete('*/api/v1/anios-escolares/2027/bloques/b2', () => new HttpResponse(null, { status: 204 })))
    await renderizarRuta('/intranet/horarios/bloques')

    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar el bloque del lunes de 08:30 a 09:15' }))

    expect(await screen.findByText('Se eliminó el bloque del lunes de 08:30 a 09:15.')).toBeInTheDocument()
  })

  it('si el bloque ya se usa muestra el motivo', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['HORARIO_CONSULTAR', 'HORARIO_CONFIGURAR']))
    servidor.use(
      http.delete('*/api/v1/anios-escolares/2027/bloques/b1', () =>
        problema(409, 'BLOQUE_EN_USO', 'El bloque ya tiene clases o disponibilidades registradas y no se puede eliminar'),
      ),
    )
    await renderizarRuta('/intranet/horarios/bloques')

    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar el bloque del lunes de 07:45 a 08:30' }))

    expect(await screen.findByText(/ya tiene clases o disponibilidades registradas/)).toBeInTheDocument()
  })

  it('quien solo consulta no ve el botón de eliminar', async () => {
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR']))
    await renderizarRuta('/intranet/horarios/bloques')

    expect(await screen.findAllByText(/07:45 – 08:30/)).not.toHaveLength(0)
    expect(screen.queryByRole('button', { name: /Eliminar el bloque/ })).not.toBeInTheDocument()
  })
})
