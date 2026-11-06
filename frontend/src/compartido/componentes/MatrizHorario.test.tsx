import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { BLOQUES } from '../../test/msw/horario'
import { MatrizHorario } from './MatrizHorario'

describe('MatrizHorario', () => {
  it('arma la tabla con los días como columnas y las franjas como filas', () => {
    render(<MatrizHorario bloques={BLOQUES} contenido={(b) => <span>clase {b.id}</span>} />)

    const tabla = screen.getByRole('table', { name: 'Horario semanal' })
    expect(within(tabla).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Hora', 'Lunes', 'Martes'])
    expect(within(tabla).getByRole('rowheader', { name: '07:45 – 08:30' })).toBeInTheDocument()
    expect(within(tabla).getByRole('rowheader', { name: '09:15 – 10:00' })).toBeInTheDocument()
    expect(within(tabla).getByText('clase b-mar1')).toBeInTheDocument()
    // Lunes no tiene la franja de 9:15: la celda queda vacía
    const fila = within(tabla).getByRole('rowheader', { name: '09:15 – 10:00' }).closest('tr')!
    expect(within(fila).getByText('—')).toBeInTheDocument()
  })

  it('en móvil muestra una lista del día elegido con pestañas (RNF-02)', async () => {
    render(<MatrizHorario bloques={BLOQUES} contenido={(b) => <span>clase {b.id}</span>} />)

    const pestanas = screen.getByRole('tablist', { name: 'Día de la semana' })
    expect(within(pestanas).getAllByRole('tab').map((t) => t.textContent)).toEqual(['Lun', 'Mar'])
    // Empieza en el primer día
    const lista = screen.getByRole('list', { name: 'Clases del Lunes' })
    expect(within(lista).getAllByRole('listitem')).toHaveLength(1)
    expect(within(lista).getByText('clase b-lun')).toBeInTheDocument()

    await userEvent.click(within(pestanas).getByRole('tab', { name: 'Mar' }))

    const martes = screen.getByRole('list', { name: 'Clases del Martes' })
    expect(within(martes).getAllByRole('listitem')).toHaveLength(2)
    expect(within(martes).getAllByText(/^clase b-mar/).map((e) => e.textContent)).toEqual([
      'clase b-mar1',
      'clase b-mar2',
    ])
    expect(within(pestanas).getByRole('tab', { name: 'Mar' })).toHaveAttribute('aria-selected', 'true')
  })

  it('con onSeleccionar las celdas son botones y marcan la elegida', async () => {
    const elegir = vi.fn()
    render(
      <MatrizHorario
        bloques={BLOQUES}
        contenido={() => 'x'}
        seleccionadoId="b-mar1"
        onSeleccionar={elegir}
      />,
    )
    const tabla = screen.getByRole('table', { name: 'Horario semanal' })

    const activa = within(tabla).getByRole('button', { name: /^Martes 09:15 a 10:00/ })
    expect(activa).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(within(tabla).getByRole('button', { name: /^Lunes 07:45 a 08:30/ }))

    expect(elegir).toHaveBeenCalledWith(expect.objectContaining({ id: 'b-lun' }))
  })

  it('sin onSeleccionar es de solo lectura (horario publicado)', () => {
    render(<MatrizHorario bloques={BLOQUES} contenido={() => 'x'} />)

    expect(screen.queryByRole('button', { name: /Lunes/ })).not.toBeInTheDocument()
    expect(screen.getAllByRole('group', { name: 'Lunes 07:45 a 08:30' }).length).toBeGreaterThan(0)
  })

  it('sin bloques avisa que no hay horario configurado', () => {
    render(<MatrizHorario bloques={[]} contenido={() => 'x'} />)

    expect(screen.getByText('No hay bloques horarios configurados para este año.')).toBeInTheDocument()
  })
})
