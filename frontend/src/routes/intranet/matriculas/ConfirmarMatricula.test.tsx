import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { DetalleSolicitud, LineaCosto } from '../../../modulos/matricula/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION_EVALUADOR = sesionDe('ADMINISTRADOR', ['MATRICULA_EVALUAR', 'MATRICULA_REGISTRAR'], { cargo: 'Secretaría académica' })

const LINEAS: LineaCosto[] = [
  { concepto: 'MATRICULA', periodo: '2027', monto: '350.00', fechaVencimiento: '2027-02-28' },
  ...Array.from({ length: 10 }, (_, i) => ({
    concepto: 'PENSION',
    periodo: `2027-${String(i + 3).padStart(2, '0')}`,
    monto: '350.00',
    fechaVencimiento: `2027-${String(i + 3).padStart(2, '0')}-05`,
  })),
]

function detalle(sobrescribir: Partial<DetalleSolicitud> = {}): DetalleSolicitud {
  return {
    codigo: 'MAT-2027-00482',
    anioEscolar: '2027',
    grado: '3°',
    fechaSolicitud: '2027-01-18',
    tipoEstudiante: 'RECURRENTE',
    estadoAdmision: 'NO_APLICA',
    estadoDocumentacion: 'COMPLETA',
    estado: 'PENDIENTE_REVISION',
    motivoRechazo: null,
    estadoVisible: 'Pendiente de revisión',
    observacion: null,
    alumno: { codigo: '2026-0404', nombre: 'Valeria Torres Gil', documento: '77100004', fechaNacimiento: '2012-09-09' },
    apoderado: { nombre: 'Marcos Torres Lima', documento: '40100004', correo: 'familia.torres@demo.gesedu.pe', telefono: '977100004', parentesco: 'PADRE' },
    documentos: [{ id: 'd-1', tipo: 'CERTIFICADO_ESTUDIOS', nombreArchivo: 'certificado-estudios.pdf', contentType: 'application/pdf', tamanoBytes: 1024 }],
    validaciones: [
      { regla: 'DOCUMENTACION', aprobada: true, mensaje: 'Documentación completa', motivoSugerido: null },
      { regla: 'ADMISION', aprobada: true, mensaje: 'No aplica (alumno recurrente)', motivoSugerido: null },
      { regla: 'MATRICULA_ACTIVA', aprobada: true, mensaje: 'Sin matrícula activa en el año', motivoSugerido: null },
      { regla: 'DEUDAS', aprobada: true, mensaje: 'Sin deudas vencidas de años anteriores', motivoSugerido: null },
    ],
    puedeConfirmar: true,
    secciones: [
      { id: 's-3a', gradoId: 'g-3', nombre: '3° A', capacidad: 30, vacantes: 4 },
      { id: 's-3b', gradoId: 'g-3', nombre: '3° B', capacidad: 30, vacantes: 0 },
    ],
    costos: { lineas: LINEAS, total: '3850.00', porcentajePenalidad: 5 },
    version: 3,
    ...sobrescribir,
  }
}

interface Escritura {
  ruta: string
  ifMatch: string | null
  cuerpo: unknown
}

function simular(d: DetalleSolicitud, escrituras: Escritura[] = []) {
  servidor.use(
    http.get('*/api/v1/solicitudes-matricula/MAT-2027-00482', () => HttpResponse.json(d)),
    http.post('*/api/v1/solicitudes-matricula/MAT-2027-00482/confirmacion', async ({ request }) => {
      escrituras.push({ ruta: 'confirmacion', ifMatch: request.headers.get('If-Match'), cuerpo: await request.json() })
      return HttpResponse.json(
        { matriculaId: 'm-1', codigoSolicitud: 'MAT-2027-00482', seccionId: 's-3a', seccion: '3° A', vacantesRestantes: 3, compromisosGenerados: 11, total: '3850.00' },
        { status: 201 },
      )
    }),
    http.post('*/api/v1/solicitudes-matricula/MAT-2027-00482/rechazo', async ({ request }) => {
      const cuerpo = (await request.json()) as { motivo: string }
      escrituras.push({ ruta: 'rechazo', ifMatch: request.headers.get('If-Match'), cuerpo })
      return HttpResponse.json({
        solicitudId: 'sol-1',
        codigoSolicitud: 'MAT-2027-00482',
        motivo: cuerpo.motivo,
        estadoVisible: 'Rechazada por Deudas',
      })
    }),
  )
}

const RUTA = '/intranet/matriculas/MAT-2027-00482'

describe('Registrar matrícula (UC-02)', () => {
  beforeEach(() => guardarSesion(SESION_EVALUADOR))

  it('la bandeja lista las solicitudes pendientes y manda los filtros', async () => {
    const urls: string[] = []
    servidor.use(
      http.get('*/api/v1/solicitudes-matricula', ({ request }) => {
        urls.push(request.url)
        return HttpResponse.json({
          contenido: [
            { codigo: 'MAT-2027-00482', alumno: 'Valeria Torres Gil', grado: '3°', fechaSolicitud: '2027-01-18', tipoEstudiante: 'RECURRENTE', estado: 'PENDIENTE_REVISION', estadoDocumentacion: 'COMPLETA', motivoRechazo: null, estadoVisible: 'Pendiente de revisión' },
            { codigo: 'MAT-2027-00485', alumno: 'Joaquín Méndez Paz', grado: '5°', fechaSolicitud: '2027-01-19', tipoEstudiante: 'RECURRENTE', estado: 'PENDIENTE_REVISION', estadoDocumentacion: 'INCOMPLETA', motivoRechazo: null, estadoVisible: 'Pendiente de revisión' },
          ],
          pagina: 0, tamano: 15, totalElementos: 2, totalPaginas: 1,
        })
      }),
    )
    await renderizarRuta('/intranet/matriculas')

    const tabla = await screen.findByRole('table', { name: 'Solicitudes de matrícula' })
    expect(within(tabla).getByText('Valeria Torres Gil')).toBeInTheDocument()
    expect(within(tabla).getByText('Documentos incompletos')).toBeInTheDocument()
    expect(new URL(urls[0]!).searchParams.get('estado')).toBe('PENDIENTE_REVISION')

    await userEvent.type(screen.getByLabelText('Buscar'), 'Valeria')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'Todas')
    await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }))
    await waitFor(() => expect(urls.at(-1)).toContain('q=Valeria'))
    expect(new URL(urls.at(-1)!).searchParams.has('estado')).toBe(false)
  })

  it('el detalle muestra las validaciones, las vacantes por sección y el resumen de costos', async () => {
    simular(detalle())
    await renderizarRuta(RUTA)

    expect(await screen.findByRole('heading', { name: /MAT-2027-00482/ })).toBeInTheDocument()
    expect(screen.getAllByText('Cumple')).toHaveLength(4)
    expect(screen.getByLabelText(/3° A/)).toBeEnabled()
    expect(screen.getByText('4 de 30 vacantes')).toBeInTheDocument()
    expect(screen.getByLabelText(/3° B/)).toBeDisabled()
    const costos = screen.getByRole('table', { name: 'Resumen de costos' })
    expect(within(costos).getAllByRole('row')).toHaveLength(1 + 11 + 1)
    expect(within(costos).getByText('S/ 3850.00')).toBeInTheDocument()
  })

  it('confirma con la sección elegida, la aceptación de costos y la versión en If-Match', async () => {
    const escrituras: Escritura[] = []
    simular(detalle(), escrituras)
    await renderizarRuta(RUTA)
    await screen.findByRole('heading', { name: /MAT-2027-00482/ })

    // Sin elegir sección ni aceptar los costos, el formulario no envía nada
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar matrícula' }))
    expect(await screen.findByText('Elige la sección en la que se matricula.')).toBeInTheDocument()
    expect(screen.getByText('Marca que el apoderado aceptó los costos.')).toBeInTheDocument()
    expect(escrituras).toHaveLength(0)

    await userEvent.click(screen.getByLabelText(/3° A/))
    await userEvent.click(screen.getByLabelText('El apoderado aceptó los costos'))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar matrícula' }))

    expect(await screen.findByRole('status')).toHaveTextContent('3° A pasa a 3 vacantes y se generan 11 compromisos de pago por S/ 3850.00')
    expect(escrituras).toEqual([
      { ruta: 'confirmacion', ifMatch: '"3"', cuerpo: { seccionId: 's-3a', costosAceptados: true } },
    ])
  })

  it('con una validación fallida no se puede confirmar y el rechazo propone el motivo (FA-3)', async () => {
    const escrituras: Escritura[] = []
    simular(
      detalle({
        puedeConfirmar: false,
        validaciones: [
          { regla: 'DOCUMENTACION', aprobada: true, mensaje: 'Documentación completa', motivoSugerido: null },
          { regla: 'DEUDAS', aprobada: false, mensaje: 'Tiene deudas vencidas de años anteriores por S/ 700.00', motivoSugerido: 'DEUDAS' },
        ],
      }),
      escrituras,
    )
    await renderizarRuta(RUTA)
    await screen.findByRole('heading', { name: /MAT-2027-00482/ })

    expect(screen.getByText('No cumple')).toBeInTheDocument()
    expect(screen.getByText(/deudas vencidas de años anteriores por S\/ 700\.00/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar matrícula' })).toBeDisabled()
    expect(screen.getByLabelText('Motivo')).toHaveValue('DEUDAS')

    await userEvent.type(screen.getByLabelText(/Observación/), 'Regularizar el pago')
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar solicitud' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Solicitud rechazada: Rechazada por Deudas')
    expect(escrituras).toEqual([
      { ruta: 'rechazo', ifMatch: '"3"', cuerpo: { motivo: 'DEUDAS', observacion: 'Regularizar el pago' } },
    ])
  })

  it('si la sección se llenó mientras tanto muestra el mensaje de sin vacante (FA-4)', async () => {
    simular(detalle())
    servidor.use(
      http.post('*/api/v1/solicitudes-matricula/MAT-2027-00482/confirmacion', () =>
        problema(409, 'SIN_VACANTE', 'La sección no tiene vacantes disponibles'),
      ),
    )
    await renderizarRuta(RUTA)
    await screen.findByRole('heading', { name: /MAT-2027-00482/ })

    await userEvent.click(screen.getByLabelText(/3° A/))
    await userEvent.click(screen.getByLabelText('El apoderado aceptó los costos'))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar matrícula' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La sección elegida ya no tiene vacantes')
  })

  it('si otra persona evaluó la solicitud avisa y ofrece recargar', async () => {
    simular(detalle())
    servidor.use(
      http.post('*/api/v1/solicitudes-matricula/MAT-2027-00482/confirmacion', () =>
        problema(409, 'CONFLICTO_CONCURRENCIA', 'La solicitud ya fue evaluada'),
      ),
    )
    await renderizarRuta(RUTA)
    await screen.findByRole('heading', { name: /MAT-2027-00482/ })

    await userEvent.click(screen.getByLabelText(/3° A/))
    await userEvent.click(screen.getByLabelText('El apoderado aceptó los costos'))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar matrícula' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('La solicitud cambió mientras la revisabas')
    expect(within(alerta).getByRole('button', { name: 'Recargar' })).toBeInTheDocument()
  })

  it('una solicitud ya rechazada se ve sin formularios y con su motivo', async () => {
    simular(detalle({ estado: 'RECHAZADA', motivoRechazo: 'NO_ADMITIDO', estadoVisible: 'Rechazada por No Admitido', observacion: 'Sin vacante en admisión', puedeConfirmar: false }))
    await renderizarRuta(RUTA)

    expect(await screen.findByText('Rechazada por No Admitido')).toBeInTheDocument()
    expect(screen.getByText('Observación: Sin vacante en admisión')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirmar matrícula' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Rechazar solicitud' })).not.toBeInTheDocument()
  })

  it('quien solo puede evaluar revisa la solicitud pero no ve confirmar ni rechazar', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['MATRICULA_EVALUAR']))
    simular(detalle())
    await renderizarRuta(RUTA)

    expect(await screen.findByRole('heading', { name: /MAT-2027-00482/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirmar matrícula' })).not.toBeInTheDocument()
    expect(screen.getByText(/no confirmarla ni rechazarla/)).toBeInTheDocument()
  })

  it('un apoderado no entra a la bandeja', async () => {
    guardarSesion(sesionDe('APODERADO', ['MATRICULA_SOLICITAR']))
    const { router } = await renderizarRuta('/intranet/matriculas')

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
  })
})
