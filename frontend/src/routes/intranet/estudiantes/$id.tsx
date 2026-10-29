import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm, type FieldPath } from 'react-hook-form'
import { ApiError } from '../../../compartido/api/errores'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { useCambiarSeccion, useMatriculaGestionable, type MatriculaGestionable, type ResultadoCambioSeccion } from '../../../modulos/matricula/api'
import { mensajeDeCambioSeccion } from '../../../modulos/matricula/mensajes'
import {
  useActualizarAlumno,
  useActualizarApoderado,
  useFichaAlumno,
  useFichaApoderado,
  type FichaAlumno,
  type FichaApoderado,
} from '../../../modulos/seguridad/api'
import { esquemaDatosAlumno, esquemaDatosApoderado, type DatosAlumno, type DatosApoderado } from '../../../modulos/seguridad/esquemas'

export const Route = createFileRoute('/intranet/estudiantes/$id')({
  beforeLoad: () => exigirPermiso('USUARIO_GESTIONAR'),
  component: EditarDatos,
})

const PARENTESCOS = ['MADRE', 'PADRE', 'TUTOR', 'OTRO']

/** Muestra el error de la API donde corresponde: bajo el campo (duplicados, validación) o como aviso general. */
function aplicarError<T extends Record<string, unknown>>(
  error: unknown,
  setError: (campo: FieldPath<T>, error: { message: string }) => void,
  general: (mensaje: string) => void,
) {
  if (!(error instanceof ApiError)) return general('No se pudieron guardar los datos. Inténtalo de nuevo.')
  if (error.codigo === 'DOCUMENTO_DUPLICADO') return setError('documento' as FieldPath<T>, { message: error.message })
  if (error.codigo === 'CORREO_DUPLICADO') return setError('correo' as FieldPath<T>, { message: error.message })
  if (error.codigo === 'CONFLICTO_CONCURRENCIA') {
    return general('Estos datos cambiaron mientras los editabas. Recarga la página para ver la versión actual.')
  }
  if (error.codigo === 'VALIDACION_INVALIDA' && error.errores.length > 0) {
    for (const e of error.errores) setError(e.campo as FieldPath<T>, { message: e.mensaje })
    return
  }
  general(error.message)
}

function EditarDatos() {
  const { id } = Route.useParams()
  const alumno = useFichaAlumno(id)

  if (alumno.isPending) return <p className="muted">Cargando…</p>
  if (alumno.isError) {
    return (
      <>
        <p className="alerta alerta-error" role="alert">
          {alumno.error.message}
        </p>
        <Link to="/intranet/estudiantes">← Buscar otro estudiante</Link>
      </>
    )
  }
  return (
    <>
      <p>
        <Link to="/intranet/estudiantes">← Buscar otro estudiante</Link>
      </p>
      <div className="encabezado">
        <div>
          <h1>
            {alumno.data.nombres} {alumno.data.apellidos}
          </h1>
          <p className="muted">Código {alumno.data.codigo}. Los cambios quedan registrados en la auditoría.</p>
        </div>
      </div>
      <FormularioAlumno key={alumno.data.id} ficha={alumno.data} />
      <SeccionApoderado apoderadoId={alumno.data.apoderadoId} />
      <SeccionMatricula alumnoId={alumno.data.id} />
    </>
  )
}

function FormularioAlumno({ ficha }: { ficha: FichaAlumno }) {
  const guardar = useActualizarAlumno(ficha.id)
  const [guardado, setGuardado] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const form = useForm<DatosAlumno>({
    resolver: zodResolver(esquemaDatosAlumno),
    defaultValues: {
      nombres: ficha.nombres,
      apellidos: ficha.apellidos,
      documento: ficha.documento,
      correo: ficha.correo ?? '',
      fechaNacimiento: ficha.fechaNacimiento,
      parentesco: ficha.parentesco,
    },
  })
  const e = form.formState.errors

  return (
    <section className="tarjeta" aria-labelledby="titulo-alumno">
      <h2 id="titulo-alumno">Datos del estudiante</h2>
      {guardado && (
        <p className="alerta alerta-ok" role="status">
          Se actualizaron los datos del estudiante.
        </p>
      )}
      {aviso && (
        <p className="alerta alerta-error" role="alert">
          {aviso}
        </p>
      )}
      <form
        className="formulario"
        aria-label="Datos del estudiante"
        noValidate
        onSubmit={form.handleSubmit((d) => {
          setGuardado(false)
          setAviso(null)
          guardar.mutate(
            { version: ficha.version, cambios: { ...d, nombres: d.nombres.trim(), apellidos: d.apellidos.trim(), documento: d.documento.trim(), correo: d.correo.trim() || null } },
            { onSuccess: () => setGuardado(true), onError: (err) => aplicarError<DatosAlumno>(err, form.setError, setAviso) },
          )
        })}
      >
        <div className="fila">
          <label className="campo">
            Nombres *
            <input {...form.register('nombres')} aria-invalid={!!e.nombres} />
            {e.nombres && <span className="error-campo">{e.nombres.message}</span>}
          </label>
          <label className="campo">
            Apellidos *
            <input {...form.register('apellidos')} aria-invalid={!!e.apellidos} />
            {e.apellidos && <span className="error-campo">{e.apellidos.message}</span>}
          </label>
        </div>
        <div className="fila">
          <label className="campo">
            DNI *
            <input inputMode="numeric" {...form.register('documento')} aria-invalid={!!e.documento} />
            {e.documento && <span className="error-campo">{e.documento.message}</span>}
          </label>
          <label className="campo">
            Fecha de nacimiento *
            <input type="date" {...form.register('fechaNacimiento')} aria-invalid={!!e.fechaNacimiento} />
            {e.fechaNacimiento && <span className="error-campo">{e.fechaNacimiento.message}</span>}
          </label>
        </div>
        <div className="fila">
          <label className="campo">
            Correo
            <input type="email" {...form.register('correo')} aria-invalid={!!e.correo} />
            {e.correo && <span className="error-campo">{e.correo.message}</span>}
            {ficha.cuentaActiva && <span className="muted">La cuenta está activa: necesita un correo para iniciar sesión.</span>}
          </label>
          <label className="campo">
            Parentesco del apoderado *
            <select {...form.register('parentesco')} aria-invalid={!!e.parentesco}>
              {[...new Set([ficha.parentesco, ...PARENTESCOS])].map((p) => (
                <option key={p} value={p}>
                  {p.charAt(0) + p.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
            {e.parentesco && <span className="error-campo">{e.parentesco.message}</span>}
          </label>
        </div>
        <div className="acciones">
          <button className="btn btn-primario" type="submit" disabled={guardar.isPending}>
            {guardar.isPending ? 'Guardando…' : 'Guardar datos del estudiante'}
          </button>
        </div>
      </form>
    </section>
  )
}

function SeccionApoderado({ apoderadoId }: { apoderadoId: string }) {
  const ficha = useFichaApoderado(apoderadoId)
  if (ficha.isPending) return <p className="muted">Cargando apoderado…</p>
  if (ficha.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {ficha.error.message}
      </p>
    )
  }
  return <FormularioApoderado key={ficha.data.id} ficha={ficha.data} />
}

function FormularioApoderado({ ficha }: { ficha: FichaApoderado }) {
  const guardar = useActualizarApoderado(ficha.id)
  const [guardado, setGuardado] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const form = useForm<DatosApoderado>({
    resolver: zodResolver(esquemaDatosApoderado),
    defaultValues: {
      nombres: ficha.nombres,
      apellidos: ficha.apellidos,
      documento: ficha.documento,
      correo: ficha.correo ?? '',
      telefono: ficha.telefono ?? '',
    },
  })
  const e = form.formState.errors

  return (
    <section className="tarjeta" aria-labelledby="titulo-apoderado">
      <h2 id="titulo-apoderado">Datos del apoderado</h2>
      {guardado && (
        <p className="alerta alerta-ok" role="status">
          Se actualizaron los datos del apoderado.
        </p>
      )}
      {aviso && (
        <p className="alerta alerta-error" role="alert">
          {aviso}
        </p>
      )}
      <form
        className="formulario"
        aria-label="Datos del apoderado"
        noValidate
        onSubmit={form.handleSubmit((d) => {
          setGuardado(false)
          setAviso(null)
          guardar.mutate(
            {
              version: ficha.version,
              cambios: { ...d, nombres: d.nombres.trim(), apellidos: d.apellidos.trim(), documento: d.documento.trim(), correo: d.correo.trim() || null, telefono: d.telefono.trim() || null },
            },
            { onSuccess: () => setGuardado(true), onError: (err) => aplicarError<DatosApoderado>(err, form.setError, setAviso) },
          )
        })}
      >
        <div className="fila">
          <label className="campo">
            Nombres del apoderado *
            <input {...form.register('nombres')} aria-invalid={!!e.nombres} />
            {e.nombres && <span className="error-campo">{e.nombres.message}</span>}
          </label>
          <label className="campo">
            Apellidos del apoderado *
            <input {...form.register('apellidos')} aria-invalid={!!e.apellidos} />
            {e.apellidos && <span className="error-campo">{e.apellidos.message}</span>}
          </label>
        </div>
        <div className="fila">
          <label className="campo">
            DNI del apoderado *
            <input inputMode="numeric" {...form.register('documento')} aria-invalid={!!e.documento} />
            {e.documento && <span className="error-campo">{e.documento.message}</span>}
          </label>
          <label className="campo">
            Celular
            <input inputMode="tel" {...form.register('telefono')} aria-invalid={!!e.telefono} />
            {e.telefono && <span className="error-campo">{e.telefono.message}</span>}
          </label>
        </div>
        <label className="campo">
          Correo del apoderado
          <input type="email" {...form.register('correo')} aria-invalid={!!e.correo} />
          {e.correo && <span className="error-campo">{e.correo.message}</span>}
          {ficha.cuentaActiva && <span className="muted">La cuenta está activa: necesita un correo para iniciar sesión.</span>}
        </label>
        <div className="acciones">
          <button className="btn btn-primario" type="submit" disabled={guardar.isPending}>
            {guardar.isPending ? 'Guardando…' : 'Guardar datos del apoderado'}
          </button>
        </div>
      </form>
    </section>
  )
}

function SeccionMatricula({ alumnoId }: { alumnoId: string }) {
  const matricula = useMatriculaGestionable(alumnoId)
  if (matricula.isPending) return <p className="muted">Cargando matrícula…</p>
  if (matricula.isError) {
    return (
      <section className="tarjeta" aria-labelledby="titulo-matricula">
        <h2 id="titulo-matricula">Matrícula</h2>
        <p className="muted">El estudiante no tiene una matrícula vigente: no hay sección que cambiar.</p>
      </section>
    )
  }
  return <CambioSeccion m={matricula.data} />
}

function CambioSeccion({ m }: { m: MatriculaGestionable }) {
  const cambiar = useCambiarSeccion(m.alumnoId, m.matriculaId)
  const [destino, setDestino] = useState('')
  const [resultado, setResultado] = useState<ResultadoCambioSeccion | null>(null)

  return (
    <section className="tarjeta" aria-labelledby="titulo-matricula">
      <h2 id="titulo-matricula">Matrícula {m.anio}</h2>
      <dl className="datos">
        <dt>Estado</dt>
        <dd>
          <span className="etiqueta etiqueta-si">{m.estado}</span>
        </dd>
        <dt>Grado y sección actual</dt>
        <dd>
          {m.grado}, sección {m.seccion.split(' ')[1]}
        </dd>
      </dl>
      {resultado && (
        <p className="alerta alerta-ok" role="status">
          {resultado.sinCambios
            ? 'La matrícula ya estaba en esa sección: no hubo cambios.'
            : `Se cambió a ${resultado.seccionNueva}. ${resultado.seccionAnterior} queda con ${resultado.vacantesOrigen} vacantes y ${resultado.seccionNueva} con ${resultado.vacantesDestino}.`}
        </p>
      )}
      {cambiar.error && (
        <p className="alerta alerta-error" role="alert">
          {mensajeDeCambioSeccion(cambiar.error)}
        </p>
      )}
      <form
        className="formulario"
        aria-label="Cambiar sección"
        onSubmit={(e) => {
          e.preventDefault()
          setResultado(null)
          cambiar.mutate(destino, {
            onSuccess: (r) => {
              setResultado(r)
              setDestino('')
            },
          })
        }}
      >
        <label className="campo">
          Nueva sección
          <select value={destino} onChange={(e) => setDestino(e.target.value)}>
            <option value="">Elige…</option>
            {m.secciones
              .filter((s) => s.id !== m.seccionId)
              .map((s) => (
                <option key={s.id} value={s.id} disabled={s.vacantes === 0}>
                  {s.nombre} — {s.vacantes === 0 ? 'sin vacantes' : `${s.vacantes} vacantes`}
                </option>
              ))}
          </select>
        </label>
        <div className="acciones">
          <button className="btn btn-primario" type="submit" disabled={destino === '' || cambiar.isPending}>
            {cambiar.isPending ? 'Cambiando…' : 'Cambiar sección'}
          </button>
        </div>
      </form>
    </section>
  )
}
