import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { useBuscarAlumnos } from '../../../modulos/pagos/api'

export const Route = createFileRoute('/intranet/estudiantes/')({
  beforeLoad: () => exigirPermiso('USUARIO_GESTIONAR'),
  component: BuscarEstudiante,
})

function BuscarEstudiante() {
  const [borrador, setBorrador] = useState('')
  const [texto, setTexto] = useState('')
  const resultados = useBuscarAlumnos(texto)

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Datos de estudiantes</h1>
          <p className="muted">Busca al estudiante por nombre, DNI o código para corregir sus datos y los de su apoderado.</p>
        </div>
      </div>

      <form
        className="tarjeta filtros"
        role="search"
        onSubmit={(e) => {
          e.preventDefault()
          setTexto(borrador.trim())
        }}
      >
        <label className="campo">
          Buscar estudiante
          <input type="search" placeholder="Nombre, DNI o código" value={borrador} onChange={(e) => setBorrador(e.target.value)} />
        </label>
        <div className="acciones">
          <button className="btn btn-primario" type="submit" disabled={borrador.trim().length === 0}>
            Buscar
          </button>
        </div>
      </form>

      {resultados.isFetching && <p className="muted">Buscando…</p>}
      {resultados.isError && (
        <p className="alerta alerta-error" role="alert">
          {resultados.error.message}
        </p>
      )}
      {texto && resultados.data && resultados.data.length === 0 && <p className="muted vacio">No encontramos estudiantes para «{texto}».</p>}
      {resultados.data && resultados.data.length > 0 && (
        <div className="tabla-scroll">
          <table aria-label="Estudiantes">
            <thead>
              <tr>
                <th>Estudiante</th>
                <th>Código</th>
                <th>DNI</th>
              </tr>
            </thead>
            <tbody>
              {resultados.data.map((a) => (
                <tr key={a.id}>
                  <td>
                    <Link to="/intranet/estudiantes/$id" params={{ id: a.id }}>
                      {a.nombre}
                    </Link>
                  </td>
                  <td>{a.codigo}</td>
                  <td>{a.documento}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
