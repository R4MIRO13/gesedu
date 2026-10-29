import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { ApiError } from '../../../compartido/api/errores'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { ANIO_TRABAJO } from '../../../compartido/menu'
import {
  NOMBRE_DOCUMENTO,
  subirDocumento,
  TAMANO_MAXIMO_ARCHIVO,
  TIPOS_ARCHIVO_PERMITIDOS,
  useOpcionesSolicitud,
  useRegistrarSolicitud,
  type DocumentoSubido,
  type OpcionesSolicitud,
  type SolicitudRegistrada,
  type TipoDocumento,
  type TipoEstudiante,
} from '../../../modulos/matricula/api'
import { esquemaSolicitud, type DatosSolicitud } from '../../../modulos/matricula/esquemas'
import { campoDeSolicitud } from '../../../modulos/matricula/mensajes'

export const Route = createFileRoute('/intranet/matricula/solicitud')({
  beforeLoad: () => exigirPermiso('MATRICULA_SOLICITAR'),
  component: NuevaSolicitud,
})

const PARENTESCOS = [
  { valor: 'MADRE', texto: 'Madre' },
  { valor: 'PADRE', texto: 'Padre' },
  { valor: 'TUTOR', texto: 'Tutor legal' },
  { valor: 'OTRO', texto: 'Otro' },
]

const TIPOS: Record<TipoEstudiante, string> = { NUEVO_INGRESO: 'Nuevo ingreso', RECURRENTE: 'Recurrente' }

function NuevaSolicitud() {
  const opciones = useOpcionesSolicitud(ANIO_TRABAJO)
  const [registrada, setRegistrada] = useState<SolicitudRegistrada | null>(null)

  if (registrada) return <Constancia solicitud={registrada} />
  if (opciones.isPending) return <p className="muted">Cargando…</p>
  if (opciones.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {opciones.error.message}
      </p>
    )
  }
  return <Formulario opciones={opciones.data} alRegistrar={setRegistrada} />
}

function Constancia({ solicitud }: { solicitud: SolicitudRegistrada }) {
  return (
    <section className="tarjeta" aria-labelledby="titulo-constancia">
      <h1 id="titulo-constancia">Solicitud recibida</h1>
      <p className="alerta alerta-ok" role="status">
        {solicitud.mensaje}
      </p>
      <dl className="datos">
        <dt>Código de seguimiento</dt>
        <dd>
          <strong>{solicitud.codigo}</strong>
        </dd>
        <dt>Estudiante</dt>
        <dd>{solicitud.alumno}</dd>
        <dt>Grado</dt>
        <dd>
          {solicitud.grado} de secundaria · año escolar {solicitud.anio}
        </dd>
        <dt>Estado</dt>
        <dd>
          <span className="etiqueta">{solicitud.estadoVisible}</span>
        </dd>
      </dl>
      <div className="acciones">
        <Link to="/intranet/matricula/solicitudes" className="btn btn-primario">
          Ver mis solicitudes
        </Link>
      </div>
    </section>
  )
}

function Formulario({ opciones, alRegistrar }: { opciones: OpcionesSolicitud; alRegistrar: (s: SolicitudRegistrada) => void }) {
  const registrar = useRegistrarSolicitud()
  const [telefonoInicial] = useState(opciones.apoderado.telefono ?? '')
  const [documentos, setDocumentos] = useState<Partial<Record<TipoDocumento, DocumentoSubido>>>({})
  const [subiendo, setSubiendo] = useState<TipoDocumento | null>(null)
  const [erroresDocumento, setErroresDocumento] = useState<Partial<Record<TipoDocumento, string>>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)
  const form = useForm<DatosSolicitud>({
    resolver: zodResolver(esquemaSolicitud),
    defaultValues: {
      nombres: '',
      apellidos: '',
      documento: '',
      fechaNacimiento: '',
      parentesco: '',
      telefono: telefonoInicial,
      tipoEstudiante: 'NUEVO_INGRESO',
      gradoId: '',
      declaracion: false,
    },
  })
  const errores = form.formState.errors
  const tipo = useWatch({ control: form.control, name: 'tipoEstudiante' })
  const requeridos = opciones.documentosRequeridos[tipo]
  const camposConError = Object.keys(errores).length + Object.keys(erroresDocumento).length

  async function adjuntar(tipoDocumento: TipoDocumento, archivo: File | undefined) {
    if (!archivo) return
    setErroresDocumento((e) => ({ ...e, [tipoDocumento]: undefined }))
    if (!TIPOS_ARCHIVO_PERMITIDOS.includes(archivo.type)) {
      setErroresDocumento((e) => ({ ...e, [tipoDocumento]: 'Solo se admiten archivos PDF, JPG o PNG.' }))
      return
    }
    if (archivo.size > TAMANO_MAXIMO_ARCHIVO) {
      setErroresDocumento((e) => ({ ...e, [tipoDocumento]: 'El archivo debe pesar hasta 5 MB.' }))
      return
    }
    setSubiendo(tipoDocumento)
    try {
      const subido = await subirDocumento(tipoDocumento, archivo)
      setDocumentos((d) => ({ ...d, [tipoDocumento]: subido }))
    } catch (error) {
      setErroresDocumento((e) => ({ ...e, [tipoDocumento]: error instanceof ApiError ? error.message : 'No se pudo subir el archivo.' }))
    } finally {
      setSubiendo(null)
    }
  }

  function enviar(datos: DatosSolicitud) {
    setErrorEnvio(null)
    // FA-1: sin los documentos que exige el tipo de estudiante, el envío queda bloqueado
    const faltantes = requeridos.filter((t) => !documentos[t])
    if (faltantes.length > 0) {
      setErroresDocumento(Object.fromEntries(faltantes.map((t) => [t, `Adjunta ${NOMBRE_DOCUMENTO[t].toLowerCase()}.`])))
      return
    }
    setErroresDocumento({})
    registrar.mutate(
      {
        anio: opciones.anio,
        tipoEstudiante: datos.tipoEstudiante,
        gradoId: datos.gradoId,
        alumno: {
          nombres: datos.nombres.trim(),
          apellidos: datos.apellidos.trim(),
          documento: datos.documento.trim(),
          fechaNacimiento: datos.fechaNacimiento,
          parentesco: datos.parentesco,
        },
        telefonoApoderado: datos.telefono.trim(),
        documentos: Object.values(documentos).filter((d): d is DocumentoSubido => !!d && (requeridos.includes(d.tipo) || d.tipo === 'OTRO')),
      },
      {
        onSuccess: alRegistrar,
        onError: (error) => {
          if (!(error instanceof ApiError)) return setErrorEnvio('No se pudo enviar la solicitud. Inténtalo de nuevo.')
          // FA-2: el documento duplicado o en trámite se muestra bajo el campo DNI
          if (error.codigo === 'SOLICITUD_EN_TRAMITE' || error.codigo === 'DOCUMENTO_DUPLICADO') {
            form.setError('documento', { message: error.message })
          } else if (error.codigo === 'VALIDACION_INVALIDA' && error.errores.length > 0) {
            for (const e of error.errores) {
              if (e.campo.startsWith('documentos.')) {
                const t = e.campo.slice('documentos.'.length) as TipoDocumento
                setErroresDocumento((previos) => ({ ...previos, [t]: e.mensaje }))
              } else {
                form.setError(campoDeSolicitud(e.campo) as keyof DatosSolicitud, { message: e.mensaje })
              }
            }
          } else {
            setErrorEnvio(error.message)
          }
        },
      },
    )
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Solicitar matrícula {opciones.anio}</h1>
          <p className="muted">Los campos con * son obligatorios.</p>
        </div>
      </div>

      {!opciones.costosConfigurados && (
        <p className="alerta alerta-error" role="alert">
          Los costos del año {opciones.anio} aún no están configurados: no se pueden recibir solicitudes.
        </p>
      )}
      {camposConError > 0 && (
        <p className="alerta alerta-error" role="alert">
          No se pudo enviar la solicitud. Corrige {camposConError === 1 ? 'el campo marcado' : `los ${camposConError} campos marcados`} en rojo.
        </p>
      )}
      {errorEnvio && (
        <p className="alerta alerta-error" role="alert">
          {errorEnvio}
        </p>
      )}

      <form className="tarjeta formulario" onSubmit={form.handleSubmit(enviar, () => undefined)} noValidate aria-label="Solicitud de matrícula">
        <h2>1. Datos del estudiante</h2>
        <div className="fila">
          <Campo etiqueta="Nombres *" error={errores.nombres?.message}>
            <input autoComplete="off" {...form.register('nombres')} aria-invalid={!!errores.nombres} />
          </Campo>
          <Campo etiqueta="Apellidos *" error={errores.apellidos?.message}>
            <input autoComplete="off" {...form.register('apellidos')} aria-invalid={!!errores.apellidos} />
          </Campo>
        </div>
        <div className="fila">
          <Campo etiqueta="DNI *" error={errores.documento?.message}>
            <input inputMode="numeric" maxLength={8} autoComplete="off" {...form.register('documento')} aria-invalid={!!errores.documento} />
          </Campo>
          <Campo etiqueta="Fecha de nacimiento *" error={errores.fechaNacimiento?.message}>
            <input type="date" {...form.register('fechaNacimiento')} aria-invalid={!!errores.fechaNacimiento} />
          </Campo>
        </div>

        <h2>Datos del apoderado</h2>
        <dl className="datos">
          <dt>Nombres y apellidos</dt>
          <dd>{opciones.apoderado.nombre}</dd>
          <dt>DNI</dt>
          <dd>{opciones.apoderado.documento}</dd>
          <dt>Correo</dt>
          <dd>{opciones.apoderado.correo ?? '—'}</dd>
        </dl>
        <div className="fila">
          <Campo etiqueta="Parentesco *" error={errores.parentesco?.message}>
            <select {...form.register('parentesco')} aria-invalid={!!errores.parentesco}>
              <option value="">Elige…</option>
              {PARENTESCOS.map((p) => (
                <option key={p.valor} value={p.valor}>
                  {p.texto}
                </option>
              ))}
            </select>
          </Campo>
          <Campo etiqueta="Celular *" error={errores.telefono?.message}>
            <input inputMode="tel" placeholder="Ej. 987 654 321" autoComplete="off" {...form.register('telefono')} aria-invalid={!!errores.telefono} />
          </Campo>
        </div>

        <h2>2. Tipo de estudiante y documentos</h2>
        <fieldset className="grupo-opciones">
          <legend>Tipo de estudiante</legend>
          {(Object.keys(TIPOS) as TipoEstudiante[]).map((t) => (
            <label key={t} className="opcion-radio">
              <input type="radio" value={t} {...form.register('tipoEstudiante')} />
              {TIPOS[t]}
            </label>
          ))}
        </fieldset>
        {requeridos.map((t) => (
          <div key={t} className="campo">
            <label htmlFor={`archivo-${t}`}>{NOMBRE_DOCUMENTO[t]} *</label>
            <input
              id={`archivo-${t}`}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
              disabled={subiendo !== null}
              onChange={(e) => void adjuntar(t, e.target.files?.[0])}
              aria-invalid={!!erroresDocumento[t]}
            />
            <span className="muted">PDF o imagen de hasta 5 MB.</span>
            {subiendo === t && <span role="status">Subiendo…</span>}
            {documentos[t] && <span className="etiqueta etiqueta-si">Adjuntado: {documentos[t].nombreArchivo}</span>}
            {erroresDocumento[t] && <span className="error-campo">{erroresDocumento[t]}</span>}
          </div>
        ))}
        <p className="muted">
          {tipo === 'NUEVO_INGRESO'
            ? 'Si eliges «Recurrente», se pedirá la ficha de matrícula anterior.'
            : 'Si eliges «Nuevo ingreso», se pedirá el certificado de estudios.'}
        </p>

        <h2>3. Grado</h2>
        <Campo etiqueta="Grado al que postula *" error={errores.gradoId?.message}>
          <select {...form.register('gradoId')} aria-invalid={!!errores.gradoId}>
            <option value="">Elige…</option>
            {opciones.grados.map((g) => (
              <option key={g.id} value={g.id}>
                {g.nombre} de secundaria
              </option>
            ))}
          </select>
        </Campo>

        <h2>4. Confirmar</h2>
        <label className="opcion-radio">
          <input type="checkbox" {...form.register('declaracion')} />
          Declaro que la información y los documentos enviados son verídicos.
        </label>
        {errores.declaracion && <span className="error-campo">{errores.declaracion.message}</span>}
        <p className="muted">Tu solicitud quedará como «Pendiente de revisión» hasta que el colegio la evalúe.</p>
        <div className="acciones">
          <button className="btn btn-primario" type="submit" disabled={registrar.isPending || subiendo !== null || !opciones.costosConfigurados}>
            {registrar.isPending ? 'Enviando…' : 'Enviar solicitud'}
          </button>
          <Link to="/intranet/inicio" className="btn">
            Cancelar
          </Link>
        </div>
      </form>
    </>
  )
}

function Campo({ etiqueta, error, children }: { etiqueta: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="campo">
      {etiqueta}
      {children}
      {error && <span className="error-campo">{error}</span>}
    </label>
  )
}
