import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ApiError } from '../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import { diaLargo, hora12, horaDeInstante } from '../../../../compartido/formato'
import {
  NOMBRE_ESTADO,
  useAsistenciaSeccion,
  useRegistrarAsistenciaSeccion,
  type AlumnoAsistencia,
  type AsistenciaSeccion,
  type EstadoAsistencia,
} from '../../../../modulos/asistencia/api'

export const Route = createFileRoute('/intranet/asistencia/$seccion/registrar')({
  beforeLoad: () => exigirPermiso('ASISTENCIA_ALUMNO_REGISTRAR'),
  component: RegistrarAsistencia,
})

const ESTADOS: EstadoAsistencia[] = ['PUNTUAL', 'TARDANZA', 'FALTA']

function RegistrarAsistencia() {
  const { seccion } = Route.useParams()
  const datos = useAsistenciaSeccion(seccion)
  if (datos.isPending) return <p className="muted">Cargando…</p>
  if (datos.isError) {
    return (
      <>
        <h1>Registrar asistencia</h1>
        <p className="alerta alerta-error" role="alert">
          {datos.error.message}
        </p>
      </>
    )
  }
  return <PaseLista codigo={seccion} datos={datos.data} />
}

/** UC-08, Registrar asistencia de alumnos: el tutor pasa lista y confirma todo el lote de una vez. */
function PaseLista({ codigo, datos }: { codigo: string; datos: AsistenciaSeccion }) {
  const registrar = useRegistrarAsistenciaSeccion(codigo)
  const [seleccion, setSeleccion] = useState<Readonly<Record<string, EstadoAsistencia>>>({})
  const [guardado, setGuardado] = useState<AsistenciaSeccion | null>(null)
  const pendientes = useMemo(() => datos.alumnos.filter((a) => a.estado === null), [datos.alumnos])
  const sinElegir = pendientes.filter((a) => seleccion[a.alumnoId] === undefined)
  const [intentado, setIntentado] = useState(false)
  const error = registrar.error instanceof ApiError ? registrar.error : null
  const errorPorAlumno = new Map((error?.errores ?? []).map((e) => [e.campo.replace('asistencias.', ''), e.mensaje]))
  const editable = datos.registroHabilitado && pendientes.length > 0

  function elegir(alumnoId: string, estado: EstadoAsistencia) {
    setSeleccion((actual) => ({ ...actual, [alumnoId]: estado }))
  }

  function marcarTodosPuntual() {
    setSeleccion(Object.fromEntries(pendientes.map((a) => [a.alumnoId, 'PUNTUAL' as const])))
  }

  function guardar() {
    setIntentado(true)
    if (sinElegir.length > 0) return
    registrar.mutate(
      pendientes.map((a) => ({ alumnoId: a.alumnoId, estado: seleccion[a.alumnoId]! })),
      { onSuccess: (resultado) => setGuardado(resultado) },
    )
  }

  const t = datos.totales
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Registrar asistencia · {datos.nombre}</h1>
          <p className="muted">Inicio › Asistencia de {datos.nombre} › Registrar</p>
        </div>
      </div>

      <section className="tarjeta" aria-label="Pase de lista">
        <p>
          <strong>{diaLargo(datos.fecha)}</strong> · {horaDeInstante(datos.horaServidor)} · <span className="muted">hora del servidor</span>
        </p>
        <p className="muted">
          Tutoría: {datos.nombre} de secundaria · {datos.alumnos.length} {datos.alumnos.length === 1 ? 'alumno' : 'alumnos'} · pase de lista al inicio de
          la jornada
        </p>

        {datos.mensaje && (
          <p className="alerta" role="status">
            {datos.mensaje}
          </p>
        )}
        {guardado && (
          <p className="alerta alerta-ok" role="status">
            Asistencia registrada: {guardado.totales.puntual} puntuales, {guardado.totales.tardanza}{' '}
            {guardado.totales.tardanza === 1 ? 'tardanza' : 'tardanzas'} y {guardado.totales.falta}{' '}
            {guardado.totales.falta === 1 ? 'falta' : 'faltas'}.
          </p>
        )}
        {(guardado || (datos.alumnos.length > 0 && pendientes.length === 0)) && (
          <p>
            <Link to="/intranet/asistencia/$seccion/registro-del-dia" params={{ seccion: codigo }}>
              Ver el registro del día
            </Link>
          </p>
        )}
        {datos.registroHabilitado && datos.alumnos.length > 0 && pendientes.length === 0 && !guardado && (
          <p className="alerta" role="status">
            Todos los alumnos ya tienen su asistencia registrada hoy: puntuales {t.puntual}, tardanzas {t.tardanza}, faltas {t.falta}.
          </p>
        )}
        {error && (
          <p className="alerta alerta-error" role="alert">
            {error.message}
          </p>
        )}
        {editable && intentado && sinElegir.length > 0 && !error && (
          <p className="alerta alerta-error" role="alert">
            Faltan {sinElegir.length} {sinElegir.length === 1 ? 'alumno' : 'alumnos'} por registrar: indica el estado de todos para guardar.
          </p>
        )}

        {datos.alumnos.length > 0 && (
          <>
            {editable && (
              <div className="acciones">
                <button type="button" className="btn" onClick={marcarTodosPuntual}>
                  Marcar todos como Puntual
                </button>
                <span className="muted" role="status">
                  {sinElegir.length === 0 ? 'Todos tienen estado' : `${sinElegir.length} por registrar`}
                </span>
              </div>
            )}
            <div className="tabla-scroll">
              <table aria-label="Alumnos de la sección">
                <thead>
                  <tr>
                    <th scope="col">Apellidos y nombres</th>
                    <th scope="col">Código</th>
                    <th scope="col">Asistencia</th>
                  </tr>
                </thead>
                <tbody>
                  {datos.alumnos.map((a) => (
                    <Fila
                      key={a.alumnoId}
                      alumno={a}
                      editable={editable}
                      elegido={seleccion[a.alumnoId]}
                      alElegir={(estado) => elegir(a.alumnoId, estado)}
                      error={errorPorAlumno.get(a.alumnoId) ?? (intentado && a.estado === null && seleccion[a.alumnoId] === undefined ? 'Indica el estado' : null)}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        <p className="muted">
          Los alumnos que lleguen tarde después de confirmar el registro se actualizan después a Tardanza (límite {hora12(datos.limiteTardanza)}).
        </p>
        <div className="acciones">
          <Link className="btn" to="/intranet/inicio">
            Cancelar
          </Link>
          <button type="button" className="btn btn-primario" disabled={!editable || registrar.isPending} onClick={guardar}>
            Guardar asistencia
          </button>
        </div>
      </section>
    </>
  )
}

function Fila({
  alumno,
  editable,
  elegido,
  alElegir,
  error,
}: {
  alumno: AlumnoAsistencia
  editable: boolean
  elegido: EstadoAsistencia | undefined
  alElegir: (estado: EstadoAsistencia) => void
  error: string | null
}) {
  const nombre = `${alumno.apellidos}, ${alumno.nombres}`
  return (
    <tr>
      <td>
        {nombre}
        {error && <div className="error-campo">{error}</div>}
      </td>
      <td>{alumno.codigo}</td>
      <td>
        {alumno.estado !== null || !editable ? (
          <span className="muted">{alumno.estado ? NOMBRE_ESTADO[alumno.estado] : 'Sin registrar'}</span>
        ) : (
          <fieldset className="grupo-opciones" aria-label={`Asistencia de ${nombre}`}>
            {ESTADOS.map((estado) => (
              <label key={estado} className="opcion-radio">
                <input
                  type="radio"
                  name={`asistencia-${alumno.alumnoId}`}
                  checked={elegido === estado}
                  onChange={() => alElegir(estado)}
                />
                {NOMBRE_ESTADO[estado]}
              </label>
            ))}
          </fieldset>
        )}
      </td>
    </tr>
  )
}
