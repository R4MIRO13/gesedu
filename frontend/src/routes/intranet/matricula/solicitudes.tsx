import { createFileRoute, Link } from '@tanstack/react-router'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { useMisSolicitudes } from '../../../modulos/matricula/api'

export const Route = createFileRoute('/intranet/matricula/solicitudes')({
  beforeLoad: () => exigirPermiso('MATRICULA_SOLICITAR'),
  component: MisSolicitudes,
})

const formatoFecha = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeZone: 'America/Lima' })

function MisSolicitudes() {
  const solicitudes = useMisSolicitudes()

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Mis solicitudes</h1>
          <p className="muted">Estado de las solicitudes de matrícula que presentaste.</p>
        </div>
        <Link to="/intranet/matricula/solicitud" className="btn btn-primario">
          Nueva solicitud
        </Link>
      </div>

      {solicitudes.isPending && <p className="muted">Cargando…</p>}
      {solicitudes.isError && (
        <p className="alerta alerta-error" role="alert">
          {solicitudes.error.message}
        </p>
      )}
      {solicitudes.data && solicitudes.data.length === 0 && (
        <p className="muted vacio">Aún no presentaste ninguna solicitud de matrícula.</p>
      )}
      {solicitudes.data && solicitudes.data.length > 0 && (
        <div className="tabla-scroll">
          <table aria-label="Mis solicitudes">
            <thead>
              <tr>
                <th>Código</th>
                <th>Estudiante</th>
                <th>Grado</th>
                <th>Año</th>
                <th>Presentada</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {solicitudes.data.map((s) => (
                <tr key={s.codigo}>
                  <td className="nowrap">{s.codigo}</td>
                  <td>{s.alumno}</td>
                  <td>{s.grado}</td>
                  <td>{s.anio}</td>
                  <td className="nowrap">{formatoFecha.format(new Date(`${s.fechaSolicitud}T12:00:00-05:00`))}</td>
                  <td>
                    <span className={s.estado === 'APROBADA' ? 'etiqueta etiqueta-si' : s.estado === 'RECHAZADA' ? 'etiqueta etiqueta-no' : 'etiqueta'}>
                      {s.estadoVisible}
                    </span>
                    {s.observacion && <div className="muted">{s.observacion}</div>}
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
