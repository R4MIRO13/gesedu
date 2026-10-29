import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { FichaAlumno, FichaApoderado } from '../../../modulos/seguridad/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION_ADMIN = sesionDe('ADMINISTRADOR', ['USUARIO_GESTIONAR', 'USUARIO_CONSULTAR'], { cargo: 'Secretaría académica' })

const ALUMNO: FichaAlumno = {
  id: 'a-1',
  codigo: '2026-0315',
  nombres: 'Lucía',
  apellidos: 'García Rojas',
  documento: '76543218',
  correo: null,
  fechaNacimiento: '2012-05-14',
  parentesco: 'MADRE',
  apoderadoId: 'ap-1',
  apoderado: 'María Rojas Salazar',
  cuentaActiva: false,
  version: 3,
}
const APODERADO: FichaApoderado = {
  id: 'ap-1',
  nombres: 'María',
  apellidos: 'Rojas Salazar',
  documento: '41236587',
  correo: 'maria.rojas@correo.com',
  telefono: '987654321',
  cuentaActiva: true,
  version: 7,
}

interface Escritura {
  ruta: string
  ifMatch: string | null
  cuerpo: Record<string, unknown>
}

function simular(escrituras: Escritura[] = []) {
  let alumno = ALUMNO
  let apoderado = APODERADO
  servidor.use(
    http.get('*/api/v1/alumnos/busqueda', ({ request }) => {
      const q = new URL(request.url).searchParams.get('q')
      return HttpResponse.json(q === 'lucia' ? [{ id: 'a-1', codigo: '2026-0315', nombre: 'Lucía García Rojas', documento: '76543218' }] : [])
    }),
    http.get('*/api/v1/alumnos/a-1', () => HttpResponse.json(alumno)),
    http.get('*/api/v1/apoderados/ap-1', () => HttpResponse.json(apoderado)),
    http.put('*/api/v1/alumnos/a-1', async ({ request }) => {
      const cuerpo = (await request.json()) as Record<string, unknown>
      escrituras.push({ ruta: 'alumno', ifMatch: request.headers.get('If-Match'), cuerpo })
      alumno = { ...alumno, ...(cuerpo as Partial<FichaAlumno>), version: alumno.version + 1 }
      return HttpResponse.json(alumno)
    }),
    http.put('*/api/v1/apoderados/ap-1', async ({ request }) => {
      const cuerpo = (await request.json()) as Record<string, unknown>
      escrituras.push({ ruta: 'apoderado', ifMatch: request.headers.get('If-Match'), cuerpo })
      apoderado = { ...apoderado, ...(cuerpo as Partial<FichaApoderado>), version: apoderado.version + 1 }
      return HttpResponse.json(apoderado)
    }),
  )
  return escrituras
}

describe('Datos de estudiantes y apoderados (RF-02)', () => {
  beforeEach(() => guardarSesion(SESION_ADMIN))

  it('busca al estudiante y abre su ficha', async () => {
    simular()
    await renderizarRuta('/intranet/estudiantes')

    await userEvent.type(await screen.findByLabelText('Buscar estudiante'), 'lucia')
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    await userEvent.click(await screen.findByRole('link', { name: 'Lucía García Rojas' }))

    expect(await screen.findByRole('heading', { name: 'Lucía García Rojas' })).toBeInTheDocument()
    expect(screen.getByLabelText(/^Nombres \*/)).toHaveValue('Lucía')
  })

  it('muestra los datos del estudiante y del apoderado en dos formularios', async () => {
    simular()
    await renderizarRuta('/intranet/estudiantes/a-1')

    expect(await screen.findByLabelText('DNI *')).toHaveValue('76543218')
    expect(screen.getByLabelText(/^Parentesco del apoderado/)).toHaveValue('MADRE')
    expect(await screen.findByLabelText(/^DNI del apoderado/)).toHaveValue('41236587')
    expect(screen.getByLabelText(/^Correo del apoderado/)).toHaveValue('maria.rojas@correo.com')
    expect(screen.getByText('Código 2026-0315. Los cambios quedan registrados en la auditoría.')).toBeInTheDocument()
  })

  it('guarda el estudiante con If-Match (la versión de la ficha) y confirma', async () => {
    const escrituras = simular()
    await renderizarRuta('/intranet/estudiantes/a-1')

    const nombres = await screen.findByLabelText(/^Nombres \*/)
    await userEvent.clear(nombres)
    await userEvent.type(nombres, 'Lucía Beatriz')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar datos del estudiante' }))

    expect(await screen.findByText('Se actualizaron los datos del estudiante.')).toBeInTheDocument()
    expect(escrituras).toHaveLength(1)
    expect(escrituras[0]!.ifMatch).toBe('"3"')
    expect(escrituras[0]!.cuerpo).toMatchObject({ nombres: 'Lucía Beatriz', documento: '76543218', correo: null, parentesco: 'MADRE' })
  })

  it('guarda el apoderado con su propia versión y manda el celular vacío como nulo', async () => {
    const escrituras = simular()
    await renderizarRuta('/intranet/estudiantes/a-1')

    const celular = await screen.findByLabelText(/^Celular/)
    await userEvent.clear(celular)
    await userEvent.click(screen.getByRole('button', { name: 'Guardar datos del apoderado' }))

    expect(await screen.findByText('Se actualizaron los datos del apoderado.')).toBeInTheDocument()
    expect(escrituras[0]!.ruta).toBe('apoderado')
    expect(escrituras[0]!.ifMatch).toBe('"7"')
    expect(escrituras[0]!.cuerpo).toMatchObject({ telefono: null, correo: 'maria.rojas@correo.com' })
  })

  it('valida los campos antes de enviar', async () => {
    const escrituras = simular()
    await renderizarRuta('/intranet/estudiantes/a-1')

    const nombres = await screen.findByLabelText(/^Nombres \*/)
    await userEvent.clear(nombres)
    await userEvent.type(screen.getByLabelText('Correo'), 'no-es-un-correo')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar datos del estudiante' }))

    expect(await screen.findByText('Ingresa los nombres.')).toBeInTheDocument()
    expect(screen.getByText('Ingresa un correo válido.')).toBeInTheDocument()
    expect(escrituras).toHaveLength(0)
  })

  it('un documento de otra cuenta se advierte bajo el campo', async () => {
    simular()
    servidor.use(
      http.put('*/api/v1/alumnos/a-1', () =>
        problema(409, 'DOCUMENTO_DUPLICADO', 'Ya existe una cuenta registrada con el documento 11112222'),
      ),
    )
    await renderizarRuta('/intranet/estudiantes/a-1')

    const dni = await screen.findByLabelText('DNI *')
    await userEvent.clear(dni)
    await userEvent.type(dni, '11112222')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar datos del estudiante' }))

    expect(await screen.findByText('Ya existe una cuenta registrada con el documento 11112222')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText(/^DNI \*/)).toHaveAttribute('aria-invalid', 'true'))
  })

  it('un correo ya usado se advierte bajo el campo del apoderado', async () => {
    simular()
    servidor.use(
      http.put('*/api/v1/apoderados/ap-1', () =>
        problema(409, 'CORREO_DUPLICADO', 'Ya existe una cuenta registrada con el correo otra@correo.com'),
      ),
    )
    await renderizarRuta('/intranet/estudiantes/a-1')

    const correo = await screen.findByLabelText(/^Correo del apoderado/)
    await userEvent.clear(correo)
    await userEvent.type(correo, 'otra@correo.com')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar datos del apoderado' }))

    expect(await screen.findByText('Ya existe una cuenta registrada con el correo otra@correo.com')).toBeInTheDocument()
  })

  it('si otra persona editó primero (409) pide recargar', async () => {
    simular()
    servidor.use(
      http.put('*/api/v1/alumnos/a-1', () => problema(409, 'CONFLICTO_CONCURRENCIA', 'El recurso cambió; recarga e inténtalo de nuevo')),
    )
    await renderizarRuta('/intranet/estudiantes/a-1')

    await userEvent.click(await screen.findByRole('button', { name: 'Guardar datos del estudiante' }))

    expect(await screen.findByText(/cambiaron mientras los editabas/)).toBeInTheDocument()
  })

  it('sin el permiso USUARIO_GESTIONAR la ruta no se muestra', async () => {
    guardarSesion(sesionDe('DOCENTE', ['HORARIO_CONSULTAR']))
    simular()
    await renderizarRuta('/intranet/estudiantes/a-1')

    expect(screen.queryByText('Datos del estudiante')).not.toBeInTheDocument()
  })
})
