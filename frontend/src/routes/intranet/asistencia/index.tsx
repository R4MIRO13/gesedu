import { createFileRoute, Navigate } from '@tanstack/react-router'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { useMiSeccion } from '../../../modulos/asistencia/api'

export const Route = createFileRoute('/intranet/asistencia/')({
  beforeLoad: () => exigirPermiso('ASISTENCIA_ALUMNO_REGISTRAR'),
  component: AsistenciaDeMiSeccion,
})

/** Lleva al tutor a la asistencia de su sección; sin tutoría o en un día no lectivo, explica por qué no hay pantalla (FA-3). */
function AsistenciaDeMiSeccion() {
  const seccion = useMiSeccion()
  if (seccion.isPending) return <p className="muted">Cargando…</p>
  if (seccion.isError) {
    return (
      <>
        <h1>Asistencia de mi sección</h1>
        <p className="alerta" role="status">
          {seccion.error.message}
        </p>
      </>
    )
  }
  return <Navigate to="/intranet/asistencia/$seccion/registrar" params={{ seccion: seccion.data.codigo }} replace />
}
