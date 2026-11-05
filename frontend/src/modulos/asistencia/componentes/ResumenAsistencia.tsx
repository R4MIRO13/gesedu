import { ApiError } from '../../../compartido/api/errores'
import { fechaCorta } from '../../../compartido/formato'
import { useMisHijos } from '../../matricula/api'
import { useResumenAsistenciaAlumno } from '../api'

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

/**
 * Tarjeta «Mi asistencia» del inicio del alumno (RF-16 bajo demanda): el porcentaje del mes, calculado por el servidor al
 * pedirlo, con sus puntuales, tardanzas y faltas y los días que aún no tienen registro (que no son faltas).
 */
export function ResumenAsistencia() {
  const hijos = useMisHijos()
  const alumnoId = hijos.data?.[0]?.alumnoId
  const resumen = useResumenAsistenciaAlumno(alumnoId)

  if (hijos.isPending || (alumnoId !== undefined && resumen.isPending)) {
    return (
      <section className="tarjeta" aria-label="Mi asistencia">
        <h2>Mi asistencia</h2>
        <p className="muted">Cargando…</p>
      </section>
    )
  }
  const r = resumen.data
  if (alumnoId === undefined || resumen.isError || r === undefined) {
    const mensaje = resumen.error instanceof ApiError ? resumen.error.message : 'Aún no hay información de asistencia.'
    return (
      <section className="tarjeta" aria-label="Mi asistencia">
        <h2>Mi asistencia</h2>
        <p className="muted">{mensaje}</p>
      </section>
    )
  }
  return (
    <section className="tarjeta" aria-label="Mi asistencia">
      <h2>Mi asistencia de {MESES[r.mes - 1]}</h2>
      <p className="porcentaje-asistencia">
        {r.porcentaje === null ? <span className="muted">No disponible</span> : <strong aria-label="Porcentaje de asistencia">{r.porcentaje} %</strong>}
      </p>
      <p>
        <span className="etiqueta etiqueta-si">Puntual {r.puntual}</span> <span className="etiqueta etiqueta-alerta">Tardanza {r.tardanza}</span>{' '}
        <span className="etiqueta etiqueta-no">Falta {r.falta}</span>
      </p>
      {r.noRegistrados > 0 && (
        <p className="muted">
          {r.noRegistrados} {r.noRegistrados === 1 ? 'día' : 'días'} sin registro: no se cuentan como faltas.
        </p>
      )}
      {r.parcial && <p className="muted">Mes en curso: calculado hasta el {fechaCorta(r.fechaCorte)}.</p>}
      <p className="muted">La tardanza cuenta como presencia.</p>
    </section>
  )
}
