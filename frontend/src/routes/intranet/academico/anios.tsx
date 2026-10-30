import { createFileRoute, Link } from '@tanstack/react-router'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { usePermiso } from '../../../compartido/auth/usePermiso'
import { fechaCorta } from '../../../compartido/formato'
import { ESTADO_ANIO, useAnios } from '../../../modulos/academico/api'

export const Route = createFileRoute('/intranet/academico/anios')({
  beforeLoad: () => exigirPermiso('ACADEMICO_CONSULTAR'),
  component: AniosEscolares,
})

/** Años escolares registrados (RF-03) y acceso a crear uno nuevo (UC-13). */
function AniosEscolares() {
  const anios = useAnios()
  const puedeConfigurar = usePermiso('ACADEMICO_CONFIGURAR')
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Año escolar</h1>
          <p className="muted">Calendario lectivo: fechas del año y de sus cuatro bimestres.</p>
        </div>
        {puedeConfigurar && (
          <Link className="btn btn-primario" to="/intranet/academico/anio-escolar/nuevo">
            Nuevo año escolar
          </Link>
        )}
      </div>
      {anios.isPending && <p className="muted">Cargando…</p>}
      {anios.isError && (
        <p className="alerta alerta-error" role="alert">
          {anios.error.message}
        </p>
      )}
      {anios.data && (
        <div className="tabla-scroll">
          <table aria-label="Años escolares">
            <thead>
              <tr>
                <th>Año</th>
                <th>Inicio</th>
                <th>Fin</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {anios.data.map((a) => (
                <tr key={a.id}>
                  <td>
                    <strong>{a.anio}</strong>
                  </td>
                  <td>{fechaCorta(a.fechaInicio)}</td>
                  <td>{fechaCorta(a.fechaFin)}</td>
                  <td>
                    <span className={a.estado === 'ACTIVO' ? 'etiqueta etiqueta-si' : 'etiqueta'}>{ESTADO_ANIO[a.estado]}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
