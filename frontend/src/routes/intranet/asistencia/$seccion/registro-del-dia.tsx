import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { ApiError } from '../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import { diaLargo, hora12, horaDeInstante } from '../../../../compartido/formato'
import {
  NOMBRE_ESTADO,
  useAsistenciaSeccion,
  useCambiarATardanza,
  type AlumnoAsistencia,
  type AsistenciaSeccion,
  type EstadoAsistencia,
} from '../../../../modulos/asistencia/api'

export const Route = createFileRoute('/intranet/asistencia/$seccion/registro-del-dia')({
  beforeLoad: () => exigirPermiso('ASISTENCIA_ALUMNO_REGISTRAR'),
  component: RegistroDelDia,
})

const CLASE_ESTADO: Record<EstadoAsistencia, string> = {
  PUNTUAL: 'etiqueta etiqueta-si',
  TARDANZA: 'etiqueta etiqueta-alerta',
  FALTA: 'etiqueta etiqueta-no',
}

function RegistroDelDia() {
  const { seccion } = Route.useParams()
  const datos = useAsistenciaSeccion(seccion)
  if (datos.isPending) return <p className="muted">Cargando…</p>
  if (datos.isError) {
    return (
      <>
        <h1>Registro del día</h1>
        <p className="alerta alerta-error" role="alert">
          {datos.error.message}
        </p>
      </>
    )
  }
  return <Registro codigo={seccion} datos={datos.data} />
}

/**
 * UC-09, registro del día: lo que el tutor guardó hoy y, mientras dure el plazo, el cambio de Falta a Tardanza de quien se
 * incorpora tarde. No hay historial de estados ni responsable en el módulo (C-07).
 */
function Registro({ codigo, datos }: { codigo: string; datos: AsistenciaSeccion }) {
  const cambiar = useCambiarATardanza()
  const [candidato, setCandidato] = useState<AlumnoAsistencia | null>(null)
  const [resultado, setResultado] = useState<string | null>(null)
  const error = cambiar.error instanceof ApiError ? cambiar.error : null
  const t = datos.totales

  function confirmar() {
    if (!candidato?.registroId) return
    const nombre = `${candidato.apellidos}, ${candidato.nombres}`
    setResultado(null)
    cambiar.mutate(candidato.registroId, {
      onSuccess: (r) => setResultado(r.modificado ? `La asistencia de ${nombre} ahora es Tardanza.` : `${nombre} ya estaba registrado como Tardanza.`),
      onSettled: () => setCandidato(null),
    })
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Registro del día · {datos.nombre}</h1>
          <p className="muted">Inicio › Asistencia de {datos.nombre} › Registro del día</p>
        </div>
        <span className={datos.registroHabilitado ? 'etiqueta etiqueta-si' : 'etiqueta etiqueta-no'}>
          {datos.registroHabilitado ? 'Jornada abierta' : 'Jornada cerrada'}
        </span>
      </div>

      <section className="tarjeta" aria-label="Registro del día">
        <p>
          <strong>{diaLargo(datos.fecha)}</strong> · {horaDeInstante(datos.horaServidor)} · <span className="muted">hora del servidor</span>
        </p>
        <p className="resumen-asistencia" aria-label="Totales">
          <span className={CLASE_ESTADO.PUNTUAL}>Puntual {t.puntual}</span> <span className={CLASE_ESTADO.TARDANZA}>Tardanza {t.tardanza}</span>{' '}
          <span className={CLASE_ESTADO.FALTA}>Falta {t.falta}</span>
          {t.pendientes > 0 && <span className="etiqueta"> Sin registrar {t.pendientes}</span>}
        </p>
        {t.pendientes > 0 && datos.registroHabilitado && (
          <p className="alerta" role="status">
            {t.pendientes} {t.pendientes === 1 ? 'alumno' : 'alumnos'} aún sin registro.{' '}
            <Link to="/intranet/asistencia/$seccion/registrar" params={{ seccion: codigo }}>
              Pasar lista
            </Link>
          </p>
        )}
        {datos.mensaje && (
          <p className="alerta" role="status">
            {datos.mensaje}
          </p>
        )}
        {resultado && (
          <p className="alerta alerta-ok" role="status">
            {resultado}
          </p>
        )}
        {error && (
          <p className="alerta alerta-error" role="alert">
            {error.message}
          </p>
        )}
        {datos.registroHabilitado && !datos.actualizacionTardanzaHabilitada && datos.totales.falta > 0 && (
          <p className="muted">El plazo para cambiar una falta a tardanza venció a las {hora12(datos.limiteTardanza)}.</p>
        )}

        <div className="tabla-scroll">
          <table aria-label="Asistencia de los alumnos">
            <thead>
              <tr>
                <th scope="col">Apellidos y nombres</th>
                <th scope="col">Código</th>
                <th scope="col">Estado</th>
                <th scope="col">Acción</th>
              </tr>
            </thead>
            <tbody>
              {datos.alumnos.map((a) => (
                <tr key={a.alumnoId}>
                  <td>
                    {a.apellidos}, {a.nombres}
                  </td>
                  <td>{a.codigo}</td>
                  <td>{a.estado ? <span className={CLASE_ESTADO[a.estado]}>{NOMBRE_ESTADO[a.estado]}</span> : <span className="muted">Sin registrar</span>}</td>
                  <td>
                    {a.estado === 'FALTA' && datos.actualizacionTardanzaHabilitada && (
                      <button type="button" className="btn" onClick={() => setCandidato(a)} disabled={cambiar.isPending}>
                        Cambiar a Tardanza
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted">Solo se puede cambiar de Falta a Tardanza al alumno que se incorpora tarde. El módulo no guarda un historial de estados.</p>
      </section>

      {candidato && (
        <div className="tarjeta" role="dialog" aria-modal="true" aria-label="Cambiar a Tardanza">
          <h2>Cambiar a Tardanza</h2>
          <p>
            ¿Cambiar la asistencia de{' '}
            <strong>
              {candidato.apellidos}, {candidato.nombres}
            </strong>{' '}
            de Falta a Tardanza? Se puede hacer hasta las {hora12(datos.limiteTardanza)} (hora del servidor).
          </p>
          <div className="acciones">
            <button type="button" className="btn" onClick={() => setCandidato(null)}>
              Cancelar
            </button>
            <button type="button" className="btn btn-primario" onClick={confirmar} disabled={cambiar.isPending}>
              Confirmar cambio
            </button>
          </div>
        </div>
      )}
    </>
  )
}
