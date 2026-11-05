import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import { diaLargo, hora12, horaDeInstante } from '../../../../compartido/formato'
import {
  NOMBRE_ESTADO,
  useAsistenciaDocentesDia,
  type AsistenciaDocentesDia,
  type EstadoAsistencia,
  type FilaAsistenciaDocente,
} from '../../../../modulos/asistencia/api'

export const Route = createFileRoute('/intranet/asistencia/docentes/$fecha')({
  beforeLoad: () => exigirPermiso('ASISTENCIA_DOCENTE_CONSULTAR'),
  component: AsistenciaDocenteDelDia,
})

const CLASE_ESTADO: Record<EstadoAsistencia, string> = {
  PUNTUAL: 'etiqueta etiqueta-si',
  TARDANZA: 'etiqueta etiqueta-alerta',
  FALTA: 'etiqueta etiqueta-no',
}

/** UC-12, consulta: la asistencia de todos los docentes en un día. `$fecha` es una fecha AAAA-MM-DD o «hoy» (el servidor decide cuál). */
function AsistenciaDocenteDelDia() {
  const { fecha } = Route.useParams()
  const navegar = useNavigate()
  const hoy = fecha === 'hoy'
  const datos = useAsistenciaDocentesDia(hoy ? null : fecha)

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Asistencia docente del día</h1>
          <p className="muted">Inicio › Asistencia › Docentes</p>
        </div>
        <label className="campo campo-corto">
          Fecha
          <input
            type="date"
            value={datos.data?.fecha ?? (hoy ? '' : fecha)}
            max={hoy ? datos.data?.fecha : undefined}
            onChange={(e) => {
              if (e.target.value) void navegar({ to: '/intranet/asistencia/docentes/$fecha', params: { fecha: e.target.value } })
            }}
          />
        </label>
      </div>

      {datos.isPending && <p className="muted">Cargando…</p>}
      {datos.isError && (
        <p className="alerta alerta-error" role="alert">
          {datos.error.message}
        </p>
      )}
      {datos.isSuccess && <Detalle datos={datos.data} />}
    </>
  )
}

function Detalle({ datos }: { datos: AsistenciaDocentesDia }) {
  const t = datos.totales
  return (
    <section className="tarjeta" aria-label="Asistencia de los docentes">
      <p>
        <strong>{diaLargo(datos.fecha)}</strong>
      </p>
      <p className="resumen-asistencia" aria-label="Totales">
        <span className={CLASE_ESTADO.PUNTUAL}>Puntual {t.puntual}</span> <span className={CLASE_ESTADO.TARDANZA}>Tardanza {t.tardanza}</span>{' '}
        <span className={CLASE_ESTADO.FALTA}>Falta {t.falta}</span>
        {t.sinRegistrar > 0 && <span className="etiqueta"> Sin registrar {t.sinRegistrar}</span>}
      </p>
      {!datos.diaLectivo && (
        <p className="alerta" role="status">
          La fecha no es un día lectivo: no hay docentes programados ni se generan faltas.
        </p>
      )}
      {datos.diaLectivo && <Cierre datos={datos} />}

      {datos.docentes.length === 0 ? (
        <p className="muted">No hay docentes con clases ni registros en esta fecha.</p>
      ) : (
        <div className="tabla-scroll">
          <table aria-label="Docentes del día">
            <thead>
              <tr>
                <th scope="col">Docente</th>
                <th scope="col">Primera clase</th>
                <th scope="col">Hora de marcado</th>
                <th scope="col">Estado</th>
              </tr>
            </thead>
            <tbody>
              {datos.docentes.map((d) => (
                <Fila key={d.docenteId} fila={d} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function Cierre({ datos }: { datos: AsistenciaDocentesDia }) {
  if (datos.cierre === 'PROCESADO') {
    return (
      <p className="alerta alerta-ok" role="status">
        El cierre diario ya procesó esta fecha{datos.detalleCierre ? `: ${datos.detalleCierre}` : ''}.
      </p>
    )
  }
  if (datos.cierre === 'FALLIDO') {
    return (
      <p className="alerta alerta-error" role="alert">
        El cierre diario no pudo completarse y se reintenta cada minuto: {datos.detalleCierre}. Los docentes sin registro siguen «sin registrar»; no se
        inventan faltas.
      </p>
    )
  }
  return (
    <p className="alerta" role="status">
      El cierre diario aún no procesó esta fecha: las faltas de quienes no marquen se generan al cierre de la jornada.
    </p>
  )
}

function Fila({ fila }: { fila: FilaAsistenciaDocente }) {
  return (
    <tr>
      <td>{fila.nombre}</td>
      <td>{fila.primerBloque ? hora12(fila.primerBloque) : <span className="muted">—</span>}</td>
      <td>
        {fila.horaRegistro ? horaDeInstante(fila.horaRegistro) : <span className="muted">—</span>}
        {fila.generadaAlCierre && <div className="muted">Generado al cierre de jornada</div>}
      </td>
      <td>{fila.estado ? <span className={CLASE_ESTADO[fila.estado]}>{NOMBRE_ESTADO[fila.estado]}</span> : <span className="muted">Sin registrar</span>}</td>
    </tr>
  )
}
