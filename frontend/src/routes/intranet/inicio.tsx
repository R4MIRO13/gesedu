import { createFileRoute, Link } from '@tanstack/react-router'
import { OPCIONES_MENU } from '../../compartido/menu'
import { obtenerSesion, ROLES, tienePermiso } from '../../compartido/auth/sesion'
import { ResumenAsistencia } from '../../modulos/asistencia/componentes/ResumenAsistencia'
import { ResumenNotas } from '../../modulos/evaluacion/componentes/ResumenNotas'

export const Route = createFileRoute('/intranet/inicio')({
  component: Inicio,
})

function Inicio() {
  const sesion = obtenerSesion()
  if (!sesion) return null
  const { usuario } = sesion
  const opciones = OPCIONES_MENU.filter((o) => tienePermiso(sesion, o.permiso))

  return (
    <>
      <h1>Hola, {usuario.nombres}</h1>
      <p className="muted">
        Rol: <span className="etiqueta">{ROLES[usuario.rol] ?? usuario.rol}</span>
        {usuario.cargo && <> · {usuario.cargo}</>}
      </p>

      {tienePermiso(sesion, 'ASISTENCIA_CONSULTAR_PROPIA') && <ResumenAsistencia />}
      {usuario.rol === 'ALUMNO' && tienePermiso(sesion, 'NOTA_CONSULTAR_PROPIA') && <ResumenNotas />}

      <h2>Tus opciones</h2>
      <div className="rejilla">
        {opciones.map((o) => (
          <Link key={o.titulo} {...o.enlace} className="tarjeta opcion">
            <h3>{o.titulo}</h3>
            <p className="muted">{o.descripcion}</p>
          </Link>
        ))}
        {opciones.length === 0 && <p className="muted">Tu rol aún no tiene opciones disponibles.</p>}
      </div>

      <details className="permisos">
        <summary>Mis permisos ({usuario.permisos.length})</summary>
        <p>
          {usuario.permisos.map((p) => (
            <code key={p}>{p}</code>
          ))}
        </p>
      </details>
    </>
  )
}
