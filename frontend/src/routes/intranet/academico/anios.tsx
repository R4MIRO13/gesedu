import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { usePermiso } from '../../../compartido/auth/usePermiso'
import { fechaCorta } from '../../../compartido/formato'
import { ApiError } from '../../../compartido/api/errores'
import { ESTADO_ANIO, useActivarAnio, useAnios } from '../../../modulos/academico/api'

export const Route = createFileRoute('/intranet/academico/anios')({
  beforeLoad: () => exigirPermiso('ACADEMICO_CONSULTAR'),
  component: AniosEscolares,
})

/** Años escolares registrados (RF-03) y acceso a crear uno nuevo (UC-13). */
function AniosEscolares() {
  const anios = useAnios()
  const puedeConfigurar = usePermiso('ACADEMICO_CONFIGURAR')
  const activar = useActivarAnio()
  const [porActivar, setPorActivar] = useState<number | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  function confirmarActivacion(anio: number) {
    setAviso(null)
    activar.mutate(anio, {
      onSuccess: () => setAviso(`El año escolar ${anio} está activo. El año anterior quedó cerrado.`),
      onError: (e) => setAviso(e instanceof ApiError ? e.message : 'No se pudo activar el año'),
      onSettled: () => setPorActivar(null),
    })
  }

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
      {aviso && (
        <p className="alerta" role="status">
          {aviso}
        </p>
      )}
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
                {puedeConfigurar && <th>Acciones</th>}
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
                  {puedeConfigurar && (
                    <td>
                      {a.estado === 'PLANIFICADO' &&
                        (porActivar === a.anio ? (
                          <span role="group" aria-label={`Confirmar la activación de ${a.anio}`}>
                            Se cerrará el año activo actual.{' '}
                            <button type="button" className="btn btn-primario" disabled={activar.isPending} onClick={() => confirmarActivacion(a.anio)}>
                              Confirmar
                            </button>{' '}
                            <button type="button" className="btn" onClick={() => setPorActivar(null)}>
                              Cancelar
                            </button>
                          </span>
                        ) : (
                          <button type="button" className="btn" onClick={() => setPorActivar(a.anio)}>
                            Activar {a.anio}
                          </button>
                        ))}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
