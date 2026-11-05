import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { ApiError } from '../../../compartido/api/errores'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { fechaCorta } from '../../../compartido/formato'
import { ANIO_TRABAJO } from '../../../compartido/menu'
import { romano, useAvanceBimestre, useConsolidarBimestre, type AvanceBimestre } from '../../../modulos/evaluacion/api'

export const Route = createFileRoute('/intranet/academico/consolidar-notas')({
  beforeLoad: () => exigirPermiso('NOTA_CONSOLIDAR'),
  validateSearch: (busqueda: Record<string, unknown>): { bimestre: number } => {
    const numero = Number(busqueda.bimestre)
    return { bimestre: Number.isInteger(numero) && numero >= 1 && numero <= 4 ? numero : 1 }
  },
  component: ConsolidarNotas,
})

const NOMBRE_ESTADO = {
  SIN_CONSOLIDAR: 'Sin consolidar',
  COMPLETADA: 'Consolidado',
  DESACTUALIZADA: 'Desactualizado',
} as const

function ConsolidarNotas() {
  const { bimestre } = Route.useSearch()
  const navegar = useNavigate({ from: Route.fullPath })
  const avance = useAvanceBimestre(ANIO_TRABAJO, bimestre)

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Consolidar notas</h1>
          <p className="muted">Inicio › Consolidar notas</p>
        </div>
      </div>
      <section className="tarjeta" aria-label="Filtros">
        <div className="dos-columnas">
          <div className="campo">
            <label htmlFor="anio-consolidar">Año escolar</label>
            <input id="anio-consolidar" name="anio" value={ANIO_TRABAJO} readOnly />
          </div>
          <div className="campo">
            <label htmlFor="bimestre-consolidar">Bimestre</label>
            <select
              id="bimestre-consolidar"
              name="bimestre"
              value={bimestre}
              onChange={(e) => void navegar({ search: { bimestre: Number(e.target.value) } })}
            >
              {[1, 2, 3, 4].map((n) => {
                const estado = avance.data?.bimestres.find((b) => b.numero === n)?.estado
                return (
                  <option key={n} value={n}>
                    Bimestre {romano(n)}
                    {estado ? ` · ${NOMBRE_ESTADO[estado]}` : ''}
                  </option>
                )
              })}
            </select>
          </div>
        </div>
      </section>
      {avance.isPending && <p className="muted">Cargando…</p>}
      {avance.isError && (
        <p className="alerta alerta-error" role="alert">
          {avance.error.message}
        </p>
      )}
      {avance.isSuccess && <Avance key={bimestre} datos={avance.data} />}
    </>
  )
}

/** UC-18: el avance de las notas por curso y sección, y el botón que se habilita al 100 %. */
function Avance({ datos }: { datos: AvanceBimestre }) {
  const consolidar = useConsolidarBimestre(datos.anio, datos.bimestre.numero)
  const error = consolidar.error instanceof ApiError ? consolidar.error : null
  const numero = romano(datos.bimestre.numero)
  const resultado = consolidar.data?.resultado ?? null
  const t = datos.totales

  return (
    <section className="tarjeta" aria-label="Avance de las notas">
      <p>
        <strong>Cierre del bimestre: {fechaCorta(datos.bimestre.fechaFin)}</strong> · {t.cursos} cursos por sección · Completos {t.completos} ·
        Incompletos {t.incompletos}
      </p>
      <p>
        <strong>Estado:</strong> {NOMBRE_ESTADO[datos.estado]}
        {datos.fechaConsolidacion ? ` el ${fechaCorta(datos.fechaConsolidacion)}` : ''}
      </p>

      {datos.estado === 'DESACTUALIZADA' && (
        <p className="alerta" role="status">
          Se modificaron logros después de consolidar el Bimestre {numero}: vuelve a consolidarlo para que la libreta tenga las notas nuevas.
        </p>
      )}
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error.message}
        </p>
      )}
      {resultado && (
        <p className="alerta alerta-ok" role="status">
          Bimestre {numero} consolidado: {resultado.creadas} {resultado.creadas === 1 ? 'nota nueva' : 'notas nuevas'}, {resultado.actualizadas}{' '}
          {resultado.actualizadas === 1 ? 'actualizada' : 'actualizadas'} y {resultado.sinCambios} sin cambios; {resultado.libretas}{' '}
          {resultado.libretas === 1 ? 'libreta al día' : 'libretas al día'}.
          {resultado.finalizada && resultado.notasAnuales
            ? ` Se calcularon las notas finales del año (${resultado.notasAnuales.creadas + resultado.notasAnuales.actualizadas} nuevas o cambiadas) y las libretas quedaron finales.`
            : ''}
        </p>
      )}

      <div className="tabla-scroll">
        <table aria-label="Avance por curso y sección">
          <thead>
            <tr>
              <th scope="col">Curso</th>
              <th scope="col">Sección</th>
              <th scope="col">Docente</th>
              <th scope="col">Notas registradas</th>
              <th scope="col">Estado</th>
            </tr>
          </thead>
          <tbody>
            {datos.cursos.map((c) => (
              <tr key={c.asignacionId}>
                <td>{c.curso}</td>
                <td>{c.seccion}</td>
                <td>{c.docente}</td>
                <td>{c.porcentaje} %</td>
                <td>{c.completo ? 'Completo' : 'Incompleto'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {datos.motivo && datos.editable && (
        <div className="alerta" role="status">
          <p>{datos.motivo}</p>
          {datos.docentesPendientes.length > 0 && (
            <>
              <p>Docentes pendientes:</p>
              <ul aria-label="Docentes pendientes">
                {datos.cursos
                  .filter((c) => !c.completo)
                  .map((c) => (
                    <li key={c.asignacionId}>
                      {c.docente} · {c.curso} · {c.seccion} · {c.porcentaje} % registrado
                    </li>
                  ))}
              </ul>
            </>
          )}
        </div>
      )}
      {!datos.editable && (
        <p className="alerta" role="status">
          El año escolar está cerrado: ya no se consolida.
        </p>
      )}
      <p className="muted">
        El botón se habilita cuando todas las secciones tengan 100 % de sus notas. Al consolidar, las notas pasan a la libreta de cada alumno.
        {datos.bimestre.ultimo ? ' En el último bimestre también se calculan las notas finales del año.' : ''}
      </p>
      <div className="acciones">
        <button type="button" className="btn btn-primario" disabled={!datos.puedeConsolidar || consolidar.isPending} onClick={() => consolidar.mutate()}>
          {datos.estado === 'SIN_CONSOLIDAR' ? 'Consolidar' : 'Volver a consolidar'} Bimestre {numero}
        </button>
      </div>
    </section>
  )
}
