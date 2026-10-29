import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { OpcionesSolicitud, SolicitudPropia } from '../../../modulos/matricula/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION_APODERADO = sesionDe('APODERADO', ['MATRICULA_SOLICITAR', 'MATRICULA_CONSULTAR_PROPIA'], { nombres: 'María Rojas' })

const OPCIONES: OpcionesSolicitud = {
  anio: 2027,
  apoderado: { nombre: 'María Rojas Salazar', documento: '41236587', correo: 'maria.rojas@correo.com', telefono: null },
  grados: [
    { id: 'g-1', nombre: '1°' },
    { id: 'g-3', nombre: '3°' },
  ],
  documentosRequeridos: { NUEVO_INGRESO: ['CERTIFICADO_ESTUDIOS'], RECURRENTE: ['FICHA_MATRICULA_ANTERIOR'] },
  costosConfigurados: true,
}

interface Simulacion {
  solicitudes: unknown[]
  subidas: { nombre: string; contentType: string }[]
}

function simular(opciones: OpcionesSolicitud = OPCIONES): Simulacion {
  const s: Simulacion = { solicitudes: [], subidas: [] }
  servidor.use(
    http.get('*/api/v1/solicitudes-matricula/opciones', () => HttpResponse.json(opciones)),
    http.post('*/api/v1/solicitudes-matricula/archivos', async ({ request }) => {
      const cuerpo = (await request.json()) as { nombreArchivo: string; contentType: string }
      s.subidas.push({ nombre: cuerpo.nombreArchivo, contentType: cuerpo.contentType })
      return HttpResponse.json({
        claveObjeto: `solicitudes/ap-1/${cuerpo.nombreArchivo}`,
        subida: { url: '/api/v1/archivos-locales/token-1', metodo: 'PUT', expiraEn: '2027-01-10T10:05:00Z', cabeceras: { 'Content-Type': cuerpo.contentType } },
      })
    }),
    http.put('*/api/v1/archivos-locales/token-1', () => new HttpResponse(null, { status: 200 })),
    http.post('*/api/v1/solicitudes-matricula', async ({ request }) => {
      s.solicitudes.push(await request.json())
      return HttpResponse.json(
        {
          codigo: 'MAT-2027-00501',
          estado: 'PENDIENTE_REVISION',
          estadoVisible: 'Pendiente de revisión',
          fechaSolicitud: '2027-01-10',
          anio: 2027,
          alumno: 'Diego Alonso García Rojas',
          grado: '1°',
          mensaje: 'Recibimos la solicitud. Pasará a revisión administrativa; con el código MAT-2027-00501 puedes consultar su estado.',
        },
        { status: 201 },
      )
    }),
  )
  return s
}

const PDF = () => new File(['%PDF-1.4 certificado'], 'certificado.pdf', { type: 'application/pdf' })

async function llenarDatos() {
  await userEvent.type(screen.getByLabelText(/^Nombres \*/), 'Diego Alonso')
  await userEvent.type(screen.getByLabelText(/^Apellidos \*/), 'García Rojas')
  await userEvent.type(screen.getByLabelText(/^DNI \*/), '78451236')
  await userEvent.type(screen.getByLabelText(/^Fecha de nacimiento \*/), '2013-05-14')
  await userEvent.selectOptions(screen.getByLabelText(/^Parentesco \*/), 'MADRE')
  await userEvent.type(screen.getByLabelText(/^Celular \*/), '987 654 321')
  await userEvent.selectOptions(screen.getByLabelText(/^Grado al que postula \*/), 'g-1')
}

describe('Solicitar matrícula (UC-01)', () => {
  beforeEach(() => guardarSesion(SESION_APODERADO))

  it('flujo básico: sube el certificado, envía la solicitud y muestra el código de seguimiento', async () => {
    const s = simular()
    await renderizarRuta('/intranet/matricula/solicitud')

    expect(await screen.findByRole('heading', { name: 'Solicitar matrícula 2027' })).toBeInTheDocument()
    expect(screen.getByText('María Rojas Salazar')).toBeInTheDocument()
    await llenarDatos()
    await userEvent.upload(screen.getByLabelText('Certificado de estudios *'), PDF())
    expect(await screen.findByText('Adjuntado: certificado.pdf')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText(/Declaro que la información/))
    await userEvent.click(screen.getByRole('button', { name: 'Enviar solicitud' }))

    expect(await screen.findByText('MAT-2027-00501')).toBeInTheDocument()
    expect(screen.getByText('Pendiente de revisión')).toBeInTheDocument()
    expect(s.subidas).toEqual([{ nombre: 'certificado.pdf', contentType: 'application/pdf' }])
    expect(s.solicitudes).toEqual([
      {
        anio: 2027,
        tipoEstudiante: 'NUEVO_INGRESO',
        gradoId: 'g-1',
        alumno: { nombres: 'Diego Alonso', apellidos: 'García Rojas', documento: '78451236', fechaNacimiento: '2013-05-14', parentesco: 'MADRE' },
        telefonoApoderado: '987 654 321',
        documentos: [
          { tipo: 'CERTIFICADO_ESTUDIOS', claveObjeto: 'solicitudes/ap-1/certificado.pdf', nombreArchivo: 'certificado.pdf', contentType: 'application/pdf', tamanoBytes: 20 },
        ],
      },
    ])
  })

  it('FA-1: con campos o documentos faltantes marca los campos y bloquea el envío', async () => {
    const s = simular()
    await renderizarRuta('/intranet/matricula/solicitud')

    await userEvent.click(await screen.findByRole('button', { name: 'Enviar solicitud' }))

    expect(await screen.findByText('Ingresa los nombres del estudiante.')).toBeInTheDocument()
    expect(screen.getByText('El DNI debe tener 8 dígitos.')).toBeInTheDocument()
    expect(screen.getByText('Elige el grado al que postula.')).toBeInTheDocument()
    expect(screen.getByRole('alert', { name: '' })).toHaveTextContent(/No se pudo enviar la solicitud\. Corrige los \d+ campos marcados en rojo\./)

    // Con los datos completos pero sin el certificado, tampoco se envía
    await llenarDatos()
    await userEvent.click(screen.getByLabelText(/Declaro que la información/))
    await userEvent.click(screen.getByRole('button', { name: 'Enviar solicitud' }))
    expect(await screen.findByText('Adjunta certificado de estudios.')).toBeInTheDocument()
    expect(s.solicitudes).toHaveLength(0)
  })

  it('al elegir «Recurrente» pide la ficha de matrícula anterior en vez del certificado', async () => {
    simular()
    await renderizarRuta('/intranet/matricula/solicitud')

    await screen.findByLabelText('Certificado de estudios *')
    await userEvent.click(screen.getByLabelText('Recurrente'))

    expect(screen.getByLabelText('Ficha de matrícula anterior *')).toBeInTheDocument()
    expect(screen.queryByLabelText('Certificado de estudios *')).not.toBeInTheDocument()
  })

  it('rechaza en el navegador un archivo que no es PDF ni imagen o que pesa más de 5 MB', async () => {
    const s = simular()
    await renderizarRuta('/intranet/matricula/solicitud')
    const entrada = await screen.findByLabelText('Certificado de estudios *')

    await userEvent.upload(entrada, new File(['MZ'], 'virus.exe', { type: 'application/x-msdownload' }), { applyAccept: false })
    expect(await screen.findByText('Solo se admiten archivos PDF, JPG o PNG.')).toBeInTheDocument()

    const grande = new File(['x'], 'grande.pdf', { type: 'application/pdf' })
    Object.defineProperty(grande, 'size', { value: 6 * 1024 * 1024 })
    await userEvent.upload(entrada, grande)
    expect(await screen.findByText('El archivo debe pesar hasta 5 MB.')).toBeInTheDocument()
    expect(s.subidas).toHaveLength(0)
  })

  it('FA-2: el DNI con una solicitud en trámite se advierte bajo el campo', async () => {
    simular()
    servidor.use(
      http.post('*/api/v1/solicitudes-matricula', () =>
        problema(409, 'SOLICITUD_EN_TRAMITE', 'Ya existe una solicitud en trámite con este DNI para 2027'),
      ),
    )
    await renderizarRuta('/intranet/matricula/solicitud')

    await screen.findByLabelText('DNI *')
    await llenarDatos()
    await userEvent.upload(screen.getByLabelText('Certificado de estudios *'), PDF())
    await screen.findByText('Adjuntado: certificado.pdf')
    await userEvent.click(screen.getByLabelText(/Declaro que la información/))
    await userEvent.click(screen.getByRole('button', { name: 'Enviar solicitud' }))

    expect(await screen.findByText('Ya existe una solicitud en trámite con este DNI para 2027')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText(/^DNI \*/)).toHaveAttribute('aria-invalid', 'true'))
  })

  it('si los costos del año no están configurados avisa y no deja enviar', async () => {
    simular({ ...OPCIONES, costosConfigurados: false })
    await renderizarRuta('/intranet/matricula/solicitud')

    expect(await screen.findByText(/aún no están configurados/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar solicitud' })).toBeDisabled()
  })

  it('sin el permiso MATRICULA_SOLICITAR la ruta no se muestra', async () => {
    guardarSesion(sesionDe('DOCENTE', ['HORARIO_CONSULTAR']))
    simular()
    await renderizarRuta('/intranet/matricula/solicitud')

    expect(screen.queryByRole('heading', { name: 'Solicitar matrícula 2027' })).not.toBeInTheDocument()
  })
})

describe('Mis solicitudes (UC-01, seguimiento)', () => {
  beforeEach(() => guardarSesion(SESION_APODERADO))

  const FILAS: SolicitudPropia[] = [
    { codigo: 'MAT-2027-00501', alumno: 'Diego Alonso García Rojas', grado: '1°', anio: 2027, fechaSolicitud: '2027-01-10', tipoEstudiante: 'NUEVO_INGRESO', estado: 'PENDIENTE_REVISION', estadoVisible: 'Pendiente de revisión', observacion: null },
    { codigo: 'MAT-2027-00490', alumno: 'Camila García Rojas', grado: '4°', anio: 2027, fechaSolicitud: '2027-01-05', tipoEstudiante: 'RECURRENTE', estado: 'RECHAZADA', estadoVisible: 'Rechazada por Deudas', observacion: 'Regulariza tu deuda' },
  ]

  it('lista las solicitudes con su estado y la observación del rechazo', async () => {
    servidor.use(http.get('*/api/v1/solicitudes-matricula/mias', () => HttpResponse.json(FILAS)))
    await renderizarRuta('/intranet/matricula/solicitudes')

    const tabla = await screen.findByRole('table', { name: 'Mis solicitudes' })
    expect(tabla).toHaveTextContent('MAT-2027-00501')
    expect(tabla).toHaveTextContent('Pendiente de revisión')
    expect(tabla).toHaveTextContent('Rechazada por Deudas')
    expect(tabla).toHaveTextContent('Regulariza tu deuda')
    expect(screen.getByRole('link', { name: 'Nueva solicitud' })).toBeInTheDocument()
  })

  it('sin solicitudes lo dice', async () => {
    servidor.use(http.get('*/api/v1/solicitudes-matricula/mias', () => HttpResponse.json([])))
    await renderizarRuta('/intranet/matricula/solicitudes')

    expect(await screen.findByText('Aún no presentaste ninguna solicitud de matrícula.')).toBeInTheDocument()
  })
})
