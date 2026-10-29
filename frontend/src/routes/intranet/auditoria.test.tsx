import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion } from '../../compartido/auth/sesion'
import { problema, sesionDe } from '../../test/msw/handlers'
import { SESION_GESTOR } from '../../test/msw/horario'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

const REGISTROS = {
  contenido: [
    {
      id: 'r-1',
      fechaHora: '2027-03-01T10:30:00-05:00',
      usuarioId: 'u-1',
      usuario: 'Carmen Salas Paredes',
      accion: 'CREAR_ASIGNACION_HORARIO',
      modulo: 'HORARIOS',
      tipoEntidad: 'AsignacionHorario',
      idEntidadAfectada: 'a-1',
      descripcion: 'Crear asignación horario',
    },
    {
      id: 'r-2',
      fechaHora: '2027-03-01T09:00:00-05:00',
      usuarioId: null,
      usuario: null,
      accion: 'BLOQUEAR_CUENTA',
      modulo: 'SEGURIDAD',
      tipoEntidad: 'Usuario',
      idEntidadAfectada: 'u-9',
      descripcion: 'Bloqueo temporal automático',
    },
  ],
  pagina: 0,
  tamano: 15,
  totalElementos: 2,
  totalPaginas: 1,
}

const CATALOGOS = {
  modulos: [
    { modulo: 'HORARIOS', acciones: ['CREAR_ASIGNACION_HORARIO', 'ELIMINAR_ASIGNACION_HORARIO'] },
    { modulo: 'SEGURIDAD', acciones: ['BLOQUEAR_CUENTA'] },
  ],
  usuarios: [{ id: 'u-1', nombre: 'Carmen Salas Paredes' }],
}

/** Guarda las URL consultadas para revisar qué filtros se enviaron. */
function simular(urls: string[] = []) {
  servidor.use(
    http.get('*/api/v1/auditoria/registros', ({ request }) => {
      urls.push(request.url)
      return HttpResponse.json(REGISTROS)
    }),
    http.get('*/api/v1/auditoria/catalogos', () => HttpResponse.json(CATALOGOS)),
    http.get('*/api/v1/auditoria/registros/r-1', () =>
      HttpResponse.json({
        registro: REGISTROS.contenido[0],
        cambios: [
          { campo: 'docente', valorAnterior: 'Rosa Quispe Mendoza', valorNuevo: 'Fernando Medina Soto' },
          { campo: 'curso', valorAnterior: null, valorNuevo: 'Matemática' },
        ],
      }),
    ),
  )
}

describe('Auditoría (UC-25)', () => {
  beforeEach(() => guardarSesion(SESION_GESTOR))

  it('lista los registros con usuario, módulo, acción y fecha; el actor «Sistema» se distingue', async () => {
    simular()
    await renderizarRuta('/intranet/auditoria')

    const tabla = await screen.findByRole('table', { name: 'Registros de auditoría' })
    const filas = within(tabla).getAllByRole('row')
    expect(within(filas[1]!).getByText('Carmen Salas Paredes')).toBeInTheDocument()
    expect(within(filas[1]!).getByText('CREAR_ASIGNACION_HORARIO')).toBeInTheDocument()
    expect(within(filas[2]!).getByText('Sistema')).toBeInTheDocument()
    expect(screen.getByText(/no se pueden editar ni eliminar/)).toBeInTheDocument()
    expect(screen.getByText(/2 registros/)).toBeInTheDocument()
  })

  it('filtra por usuario, módulo, acción y fechas; las acciones dependen del módulo', async () => {
    const urls: string[] = []
    simular(urls)
    await renderizarRuta('/intranet/auditoria')
    await screen.findByRole('table', { name: 'Registros de auditoría' })
    await screen.findByRole('option', { name: 'Carmen Salas Paredes' })

    const accion = screen.getByLabelText('Acción')
    expect(accion).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText('Usuario'), 'u-1')
    await userEvent.selectOptions(screen.getByLabelText('Módulo'), 'HORARIOS')
    expect(accion).toBeEnabled()
    expect(within(accion).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Todas',
      'CREAR_ASIGNACION_HORARIO',
      'ELIMINAR_ASIGNACION_HORARIO',
    ])
    await userEvent.selectOptions(accion, 'CREAR_ASIGNACION_HORARIO')
    await userEvent.type(screen.getByLabelText('Desde'), '2027-03-01')
    await userEvent.type(screen.getByLabelText('Hasta'), '2027-03-31')
    await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }))

    await waitFor(() => expect(urls.at(-1)).toContain('usuarioId=u-1'))
    const ultima = new URL(urls.at(-1)!).searchParams
    expect(Object.fromEntries(ultima)).toMatchObject({
      usuarioId: 'u-1',
      modulo: 'HORARIOS',
      accion: 'CREAR_ASIGNACION_HORARIO',
      desde: '2027-03-01',
      hasta: '2027-03-31',
      page: '0',
    })

    // Cambiar el módulo limpia la acción elegida
    await userEvent.selectOptions(screen.getByLabelText('Módulo'), 'SEGURIDAD')
    expect(screen.getByLabelText('Acción')).toHaveValue('')
  })

  it('«Ver cambios» muestra el valor anterior y el nuevo de cada campo (RF-49)', async () => {
    simular()
    await renderizarRuta('/intranet/auditoria')
    const tabla = await screen.findByRole('table', { name: 'Registros de auditoría' })

    await userEvent.click(within(tabla).getAllByRole('button', { name: 'Ver cambios' })[0]!)

    const cambios = await screen.findByRole('table', { name: 'Cambios del registro' })
    expect(within(cambios).getByText('docente')).toBeInTheDocument()
    expect(within(cambios).getByText('Rosa Quispe Mendoza')).toBeInTheDocument()
    expect(within(cambios).getByText('Fernando Medina Soto')).toBeInTheDocument()
    expect(within(cambios).getByText('Matemática')).toBeInTheDocument()
    await userEvent.click(within(tabla).getByRole('button', { name: 'Ocultar' }))
    expect(screen.queryByRole('table', { name: 'Cambios del registro' })).not.toBeInTheDocument()
  })

  it('exporta a Excel y a CSV con los filtros aplicados y descarga el archivo', async () => {
    simular()
    const urlsExportacion: string[] = []
    servidor.use(
      http.get('*/api/v1/auditoria/registros/exportacion', ({ request }) => {
        urlsExportacion.push(request.url)
        const csv = new URL(request.url).searchParams.get('formato') === 'csv'
        return new HttpResponse(csv ? 'a,b' : 'xlsx', {
          headers: {
            'Content-Type': csv ? 'text/csv' : 'application/octet-stream',
            'Content-Disposition': `attachment; filename="auditoria-20270301-1030.${csv ? 'csv' : 'xlsx'}"`,
          },
        })
      }),
    )
    const descargas: string[] = []
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:prueba', revokeObjectURL: () => undefined }))
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      descargas.push(this.download)
    })
    await renderizarRuta('/intranet/auditoria')
    await screen.findByRole('table', { name: 'Registros de auditoría' })
    await userEvent.selectOptions(screen.getByLabelText('Módulo'), 'HORARIOS')
    await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }))

    await userEvent.click(screen.getByText('Exportar ▾'))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Excel (.xlsx)' }))
    await waitFor(() => expect(descargas).toEqual(['auditoria-20270301-1030.xlsx']))
    await userEvent.click(screen.getByText('Exportar ▾'))
    await userEvent.click(screen.getByRole('menuitem', { name: 'CSV' }))
    await waitFor(() => expect(descargas).toEqual(['auditoria-20270301-1030.xlsx', 'auditoria-20270301-1030.csv']))

    expect(urlsExportacion.map((u) => new URL(u).searchParams.get('formato'))).toEqual(['xlsx', 'csv'])
    expect(new URL(urlsExportacion[0]!).searchParams.get('modulo')).toBe('HORARIOS')
    vi.unstubAllGlobals()
  })

  it('si la exportación falla muestra el motivo que da el servidor', async () => {
    simular()
    servidor.use(
      http.get('*/api/v1/auditoria/registros/exportacion', () =>
        problema(400, 'VALIDACION_INVALIDA', 'La exportación admite hasta 20000 registros. Acota las fechas.'),
      ),
    )
    await renderizarRuta('/intranet/auditoria')
    await screen.findByRole('table', { name: 'Registros de auditoría' })

    await userEvent.click(screen.getByText('Exportar ▾'))
    await userEvent.click(screen.getByRole('menuitem', { name: 'CSV' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La exportación admite hasta 20000 registros')
  })

  it('quien puede consultar pero no exportar no ve el botón Exportar', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['AUDITORIA_CONSULTAR']))
    simular()
    await renderizarRuta('/intranet/auditoria')
    await screen.findByRole('table', { name: 'Registros de auditoría' })

    expect(screen.queryByText('Exportar ▾')).not.toBeInTheDocument()
  })

  it('un alumno no entra a la auditoría', async () => {
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR']))
    const { router } = await renderizarRuta('/intranet/auditoria')

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
  })
})
