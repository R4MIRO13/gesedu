import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { exigirPermiso } from '../../compartido/auth/guardias'
import { useRegistrosAuditoria, type FiltrosAuditoria } from '../../modulos/auditoria/api'

export const Route = createFileRoute('/intranet/auditoria')({
  beforeLoad: () => exigirPermiso('AUDITORIA_CONSULTAR'),
  component: Auditoria,
})

const VACIOS: FiltrosAuditoria = { modulo: '', accion: '', desde: '', hasta: '', pagina: 0 }

const formatoFecha = new Intl.DateTimeFormat('es-PE', {
  dateStyle: 'short',
  timeStyle: 'medium',
  timeZone: 'America/Lima',
})

function Auditoria() {
  const [borrador, setBorrador] = useState(VACIOS)
  const [filtros, setFiltros] = useState(VACIOS)
  const registros = useRegistrosAuditoria(filtros)
  const datos = registros.data

  function cambiar(campo: 'modulo' | 'accion' | 'desde' | 'hasta', valor: string) {
    setBorrador((b) => ({ ...b, [campo]: valor }))
  }

  return (
    <>
      <h1>Auditoría</h1>
      <p className="muted">Más recientes primero. Los registros no se pueden modificar ni borrar.</p>

      <form
        className="tarjeta filtros"
        onSubmit={(e) => {
          e.preventDefault()
          setFiltros({ ...borrador, pagina: 0 })
        }}
      >
        <label className="campo">
          Módulo
          <input value={borrador.modulo} onChange={(e) => cambiar('modulo', e.target.value)} placeholder="HORARIOS" />
        </label>
        <label className="campo">
          Acción
          <input value={borrador.accion} onChange={(e) => cambiar('accion', e.target.value)} placeholder="CREAR_BLOQUE_HORARIO" />
        </label>
        <label className="campo">
          Desde
          <input type="date" value={borrador.desde} onChange={(e) => cambiar('desde', e.target.value)} />
        </label>
        <label className="campo">
          Hasta
          <input type="date" value={borrador.hasta} onChange={(e) => cambiar('hasta', e.target.value)} />
        </label>
        <div className="acciones">
          <button className="btn btn-primario" type="submit">
            Filtrar
          </button>
          <button
            className="btn"
            type="button"
            onClick={() => {
              setBorrador(VACIOS)
              setFiltros(VACIOS)
            }}
          >
            Limpiar
          </button>
        </div>
      </form>

      {registros.isPending && <p className="muted">Cargando…</p>}
      {registros.isError && (
        <p className="alerta alerta-error" role="alert">
          {registros.error.message}
        </p>
      )}

      {datos && (
        <>
          <div className="tabla-scroll">
            <table>
              <thead>
                <tr>
                  <th>Fecha y hora</th>
                  <th>Usuario</th>
                  <th>Módulo</th>
                  <th>Acción</th>
                  <th>Entidad</th>
                  <th>Descripción</th>
                </tr>
              </thead>
              <tbody>
                {datos.contenido.map((r) => (
                  <tr key={r.id}>
                    <td className="nowrap">{formatoFecha.format(new Date(r.fechaHora))}</td>
                    <td>{r.usuario ?? <em>Sistema</em>}</td>
                    <td>
                      <span className="etiqueta">{r.modulo}</span>
                    </td>
                    <td>
                      <code>{r.accion}</code>
                    </td>
                    <td>{r.tipoEntidad ?? '—'}</td>
                    <td>{r.descripcion ?? '—'}</td>
                  </tr>
                ))}
                {datos.contenido.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted vacio">
                      Sin registros para los filtros indicados.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="paginacion">
            <button
              className="btn"
              disabled={datos.pagina === 0}
              onClick={() => setFiltros((f) => ({ ...f, pagina: f.pagina - 1 }))}
            >
              ← Anterior
            </button>
            <span className="muted">
              Página {datos.pagina + 1} de {Math.max(datos.totalPaginas, 1)} · {datos.totalElementos} registros
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
