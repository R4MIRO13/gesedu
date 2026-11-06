import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { exigirPermiso } from '../../compartido/auth/guardias'
import { useGuardarPermisosRol, useRoles, type CatalogoRoles, type Rol } from '../../modulos/seguridad/api'

export const Route = createFileRoute('/intranet/roles')({
  beforeLoad: () => exigirPermiso('ROL_GESTIONAR'),
  component: RolesYPermisos,
})

/** Permisos que el Administrador no puede perder: con ellos se gestionan las cuentas y los roles. */
const ESENCIALES_DEL_ADMINISTRADOR = ['USUARIO_CONSULTAR', 'USUARIO_GESTIONAR', 'ROL_GESTIONAR']

/** RF-48: el administrador cambia qué permisos tiene cada rol. */
function RolesYPermisos() {
  const catalogo = useRoles()
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Roles y permisos</h1>
          <p className="muted">
            Inicio › Usuarios › Roles. Los cambios se aplican en el siguiente request de cada usuario del rol, sin que tenga que volver a iniciar sesión.
          </p>
        </div>
      </div>
      {catalogo.isPending && <p className="muted">Cargando…</p>}
      {catalogo.isError && (
        <p className="alerta alerta-error" role="alert">
          {catalogo.error.message}
        </p>
      )}
      {catalogo.data && <Editor catalogo={catalogo.data} />}
    </>
  )
}

function Editor({ catalogo }: { catalogo: CatalogoRoles }) {
  const [rolId, setRolId] = useState(catalogo.roles[0]?.id ?? '')
  const rol = catalogo.roles.find((r) => r.id === rolId) ?? catalogo.roles[0]
  if (!rol) return <p className="muted">No hay roles.</p>
  // La clave reinicia el formulario al cambiar de rol o cuando el servidor devuelve otros permisos
  return (
    <div className="dos-columnas">
      <nav aria-label="Roles" className="tarjeta">
        <ul className="lista-sin-vineta">
          {catalogo.roles.map((r) => (
            <li key={r.id}>
              <button
                type="button"
                className={r.id === rol.id ? 'btn btn-primario' : 'btn'}
                aria-pressed={r.id === rol.id}
                onClick={() => setRolId(r.id)}
              >
                {r.nombre}
              </button>
            </li>
          ))}
        </ul>
      </nav>
      <FormularioRol key={`${rol.id}:${rol.permisos.join(',')}`} rol={rol} catalogo={catalogo} />
    </div>
  )
}

function FormularioRol({ rol, catalogo }: { rol: Rol; catalogo: CatalogoRoles }) {
  const guardar = useGuardarPermisosRol()
  const [marcados, setMarcados] = useState<Set<string>>(new Set(rol.permisos))
  const [aviso, setAviso] = useState<string | null>(null)
  const esAdministrador = rol.codigo === 'ADMINISTRADOR'

  const porModulo = [...catalogo.permisos.reduce((mapa, p) => mapa.set(p.modulo, [...(mapa.get(p.modulo) ?? []), p]), new Map<string, CatalogoRoles['permisos']>())]
  const cambios = marcados.size !== rol.permisos.length || rol.permisos.some((c) => !marcados.has(c))

  function alternar(codigo: string) {
    setAviso(null)
    setMarcados((actual) => {
      const nuevo = new Set(actual)
      if (nuevo.has(codigo)) nuevo.delete(codigo)
      else nuevo.add(codigo)
      return nuevo
    })
  }

  return (
    <form
      className="tarjeta"
      aria-label={`Permisos del rol ${rol.nombre}`}
      onSubmit={(e) => {
        e.preventDefault()
        setAviso(null)
        guardar.mutate(
          { id: rol.id, permisos: [...marcados] },
          { onSuccess: () => setAviso(`Se guardaron los permisos del rol ${rol.nombre}. Rigen desde el siguiente request de sus usuarios.`) },
        )
      }}
    >
      <h2>{rol.nombre}</h2>
      {rol.descripcion && <p className="muted">{rol.descripcion}</p>}
      {esAdministrador && (
        <p className="muted">Los permisos para gestionar cuentas y roles no se pueden quitar al Administrador.</p>
      )}
      {porModulo.map(([modulo, permisos]) => (
        <fieldset key={modulo}>
          <legend>{modulo}</legend>
          {permisos.map((p) => {
            const bloqueado = esAdministrador && ESENCIALES_DEL_ADMINISTRADOR.includes(p.codigo)
            return (
              <label key={p.codigo} className="campo-check">
                <input type="checkbox" checked={marcados.has(p.codigo)} disabled={bloqueado} onChange={() => alternar(p.codigo)} />
                <span>
                  <code>{p.codigo}</code>
                  {p.descripcion && <span className="muted"> — {p.descripcion}</span>}
                </span>
              </label>
            )
          })}
        </fieldset>
      ))}
      {aviso && (
        <p className="alerta alerta-ok" role="status">
          {aviso}
        </p>
      )}
      {guardar.error && (
        <p className="alerta alerta-error" role="alert">
          {guardar.error.message}
        </p>
      )}
      <div className="acciones">
        <button type="submit" className="btn btn-primario" disabled={!cambios || guardar.isPending}>
          {guardar.isPending ? 'Guardando…' : 'Guardar permisos'}
        </button>
      </div>
    </form>
  )
}
