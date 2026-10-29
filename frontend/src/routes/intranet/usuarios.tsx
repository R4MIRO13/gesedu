import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { exigirPermiso } from '../../compartido/auth/guardias'
import { usePermiso } from '../../compartido/auth/usePermiso'
import {
  FILTROS_USUARIOS,
  useDesbloquearUsuario,
  useUsuarios,
  type EstadoCuenta,
  type FiltrosUsuarios,
  type UsuarioFila,
} from '../../modulos/seguridad/api'

export const Route = createFileRoute('/intranet/usuarios')({
  beforeLoad: () => exigirPermiso('USUARIO_CONSULTAR'),
  component: Usuarios,
})

const ROLES = [
  { valor: 'ADMINISTRADOR', texto: 'Administrador' },
  { valor: 'DOCENTE', texto: 'Docente' },
  { valor: 'ALUMNO', texto: 'Alumno' },
  { valor: 'APODERADO', texto: 'Apoderado' },
]
const NOMBRE_ROL: Record<string, string> = Object.fromEntries(ROLES.map((r) => [r.valor, r.texto]))

const ESTADOS: { valor: EstadoCuenta | ''; texto: string }[] = [
  { valor: '', texto: 'Todos' },
  { valor: 'ACTIVO', texto: 'Activos' },
  { valor: 'BLOQUEADO', texto: 'Bloqueados' },
  { valor: 'INACTIVO', texto: 'Inactivos' },
]

/** Intentos fallidos que bloquean una cuenta (RNF-06); el texto es el del wireframe. */
const INTENTOS_PARA_BLOQUEO = 5

const formatoHora = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Lima' })

function Usuarios() {
  const [borrador, setBorrador] = useState<FiltrosUsuarios>(FILTROS_USUARIOS)
  const [filtros, setFiltros] = useState<FiltrosUsuarios>(FILTROS_USUARIOS)
  const [aviso, setAviso] = useState<string | null>(null)
  const usuarios = useUsuarios(filtros)
  const desbloquear = useDesbloquearUsuario()
  const puedeGestionar = usePermiso('USUARIO_GESTIONAR')
  const datos = usuarios.data

  function levantarBloqueo(u: UsuarioFila) {
    setAviso(null)
    desbloquear.mutate(u.id, { onSuccess: () => setAviso(`Se desbloqueó la cuenta de ${u.nombre}.`) })
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Usuarios</h1>
          <p className="muted">Cuentas del sistema. Las cuentas no se eliminan: se desactivan.</p>
        </div>
      </div>

      <form
        className="tarjeta filtros"
        onSubmit={(e) => {
          e.preventDefault()
          setAviso(null)
          setFiltros({ ...borrador, pagina: 0 })
        }}
      >
        <label className="campo">
          Buscar
          <input
            type="search"
            placeholder="Nombre o correo"
            value={borrador.q}
            onChange={(e) => setBorrador({ ...borrador, q: e.target.value })}
          />
        </label>
        <label className="campo">
          Rol
          <select value={borrador.rol} onChange={(e) => setBorrador({ ...borrador, rol: e.target.value })}>
            <option value="">Todos</option>
            {ROLES.map((r) => (
              <option key={r.valor} value={r.valor}>
                {r.texto}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          Estado
          <select value={borrador.estado} onChange={(e) => setBorrador({ ...borrador, estado: e.target.value as FiltrosUsuarios['estado'] })}>
            {ESTADOS.map((e) => (
              <option key={e.valor} value={e.valor}>
                {e.texto}
              </option>
            ))}
          </select>
        </label>
        <div className="acciones">
          <button className="btn btn-primario" type="submit">
            Filtrar
          </button>
        </div>
      </form>

      {aviso && (
        <p className="alerta alerta-ok" role="status">
          {aviso}
        </p>
      )}
      {desbloquear.error && (
        <p className="alerta alerta-error" role="alert">
          {desbloquear.error.message}
        </p>
      )}
      {usuarios.isPending && <p className="muted">Cargando…</p>}
      {usuarios.isError && (
        <p className="alerta alerta-error" role="alert">
          {usuarios.error.message}
        </p>
      )}

      {datos && (
        <>
          <div className="tabla-scroll">
            <table aria-label="Usuarios">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Correo</th>
                  <th>Rol</th>
                  <th>Estado</th>
                  <th>Último acceso</th>
                  <th>
                    <span className="solo-lectores">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {datos.contenido.map((u) => (
                  <tr key={u.id}>
                    <td>{u.nombre}</td>
                    <td>{u.correo ?? <span className="muted">Sin correo</span>}</td>
                    <td>{NOMBRE_ROL[u.rol] ?? u.rol}</td>
                    <td>
                      <EstadoCuentaEtiqueta u={u} />
                    </td>
                    <td className="nowrap">{u.ultimoAcceso ? formatoHora.format(new Date(u.ultimoAcceso)) : '—'}</td>
                    <td>
                      {puedeGestionar && u.estado === 'BLOQUEADO' && (
                        <button
                          type="button"
                          className="btn"
                          disabled={desbloquear.isPending}
                          onClick={() => levantarBloqueo(u)}
                          aria-label={`Desbloquear a ${u.nombre}`}
                        >
                          Desbloquear
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {datos.contenido.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted vacio">
                      No hay usuarios para los filtros indicados.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="paginacion">
            <button className="btn" disabled={datos.pagina === 0} onClick={() => setFiltros((f) => ({ ...f, pagina: f.pagina - 1 }))}>
              ← Anterior
            </button>
            <span className="muted">
              Página {datos.pagina + 1} de {Math.max(datos.totalPaginas, 1)} · {datos.totalElementos} usuarios
            </span>
            <button
              className="btn"
              disabled={datos.pagina + 1 >= datos.totalPaginas}
              onClick={() => setFiltros((f) => ({ ...f, pagina: f.pagina + 1 }))}
            >
              Siguiente →
            </button>
          </div>
        </>
      )}
    </>
  )
}

function EstadoCuentaEtiqueta({ u }: { u: UsuarioFila }) {
  if (u.estado === 'BLOQUEADO') {
    const hasta = u.bloqueadoHasta ? formatoHora.format(new Date(u.bloqueadoHasta)) : null
    return (
      <span className="etiqueta etiqueta-no" title={hasta ? `Hasta ${hasta}` : undefined}>
        Bloqueado · {INTENTOS_PARA_BLOQUEO} intentos
      </span>
    )
  }
  return <span className={u.estado === 'ACTIVO' ? 'etiqueta etiqueta-si' : 'etiqueta'}>{u.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'}</span>
}
