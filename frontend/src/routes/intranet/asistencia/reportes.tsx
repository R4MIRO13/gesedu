import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { ApiError } from '../../../compartido/api/errores'
import { exigirAlgunPermiso } from '../../../compartido/auth/guardias'
import { usePermiso } from '../../../compartido/auth/usePermiso'
import { fechaCorta } from '../../../compartido/formato'
import { guardarArchivo } from '../../../compartido/guardarArchivo'
import { ANIO_TRABAJO } from '../../../compartido/menu'
import {
  descargarReporteAsistencia,
  useReporteAsistencia,
  type AmbitoReporte,
  type FiltrosReporte,
  type FormatoReporte,
  type ReporteAsistencia,
} from '../../../modulos/asistencia/api'
import { useGrados } from '../../../modulos/academico/api'

export const Route = createFileRoute('/intranet/asistencia/reportes')({
  beforeLoad: () => exigirAlgunPermiso('ASISTENCIA_REPORTE_SECCION', 'ASISTENCIA_REPORTE_GENERAL'),
  component: ReportesDeAsistencia,
})

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']

const formatoGeneracion = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Lima' })

/** Por omisión, el mes actual: lo decide el servidor, no la fecha de este equipo. */
function filtrosIniciales(): FiltrosReporte {
  return { ambito: 'ALUMNOS', mes: null, anio: null, seccion: '' }
}

/** UC-10 y UC-11: reporte mensual de asistencia de alumnos o docentes, calculado al pedirlo, en pantalla, Excel o PDF. */
function ReportesDeAsistencia() {
  const esGeneral = usePermiso('ASISTENCIA_REPORTE_GENERAL')
  const [borrador, setBorrador] = useState<FiltrosReporte>(filtrosIniciales)
  const [pedido, setPedido] = useState<FiltrosReporte | null>(null)
  const [descargando, setDescargando] = useState<FormatoReporte | null>(null)
  const [errorDescarga, setErrorDescarga] = useState<string | null>(null)
  const reporte = useReporteAsistencia(pedido)
  // Solo el administrador elige sección; si no puede leer las secciones del año, queda «todas»
  const grados = useGrados(ANIO_TRABAJO, esGeneral)
  const secciones = esGeneral && borrador.ambito === 'ALUMNOS' ? (grados.data?.grados ?? []).flatMap((g) => g.secciones) : []

  function cambiar<K extends keyof FiltrosReporte>(campo: K, valor: FiltrosReporte[K]) {
    setBorrador((actual) => ({ ...actual, [campo]: valor, ...(campo === 'ambito' ? { seccion: '' } : {}) }))
  }

  async function descargar(formato: FormatoReporte) {
    setDescargando(formato)
    setErrorDescarga(null)
    try {
      const { blob, nombre } = await descargarReporteAsistencia(borrador, formato)
      guardarArchivo(blob, nombre)
    } catch (error) {
      setErrorDescarga(error instanceof ApiError ? error.message : 'No se pudo generar el archivo')
    } finally {
      setDescargando(null)
    }
  }

  const error = reporte.error instanceof ApiError ? reporte.error : null
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Reportes de asistencia</h1>
          <p className="muted">Inicio › Asistencia › Reportes</p>
        </div>
      </div>

      <section className="tarjeta" aria-label="Filtros del reporte">
        <form
          className="filtros"
          onSubmit={(e) => {
            e.preventDefault()
            setErrorDescarga(null)
            setPedido({ ...borrador })
          }}
        >
          {esGeneral && (
            <label className="campo">
              Ámbito
              <select name="ambito" value={borrador.ambito} onChange={(e) => cambiar('ambito', e.target.value as AmbitoReporte)}>
                <option value="ALUMNOS">Alumnos</option>
                <option value="DOCENTES">Docentes</option>
              </select>
            </label>
          )}
          <label className="campo">
            Mes
            <select
              name="mes"
              value={borrador.mes ?? ''}
              onChange={(e) => setBorrador((actual) => ({ ...actual, mes: e.target.value === '' ? null : Number(e.target.value), anio: e.target.value === '' ? null : actual.anio ?? ANIO_TRABAJO }))}
            >
              <option value="">Mes actual</option>
              {MESES.map((nombre, i) => (
                <option key={nombre} value={i + 1}>
                  {nombre}
                </option>
              ))}
            </select>
          </label>
          <label className="campo campo-corto">
            Año
            <input
              type="number"
              name="anio"
              min={2000}
              max={2999}
              value={borrador.anio ?? ''}
              placeholder="Año actual"
              disabled={borrador.mes === null}
              onChange={(e) => cambiar('anio', e.target.value === '' ? null : Number(e.target.value))}
            />
          </label>
          {esGeneral && borrador.ambito === 'ALUMNOS' && (
            <label className="campo">
              Sección
              <select name="seccion" value={borrador.seccion} onChange={(e) => cambiar('seccion', e.target.value)}>
                <option value="">Todas las secciones</option>
                {secciones.map((s) => (
                  <option key={s.id} value={s.nombre.replace('° ', '')}>
                    {s.nombre}
                  </option>
                ))}
              </select>
            </label>
          )}
          <div className="acciones">
            <button type="submit" className="btn btn-primario">
              Ver reporte
            </button>
            <button type="button" className="btn" disabled={descargando !== null} onClick={() => void descargar('xlsx')}>
              Descargar Excel
            </button>
            <button type="button" className="btn" disabled={descargando !== null} onClick={() => void descargar('pdf')}>
              Descargar PDF
            </button>
          </div>
        </form>
        {errorDescarga && (
          <p className="alerta alerta-error" role="alert">
            {errorDescarga}
          </p>
        )}
      </section>

      {reporte.isFetching && <p className="muted">Calculando…</p>}
      {error && (
        <p className={error.status === 422 ? 'alerta' : 'alerta alerta-error'} role={error.status === 422 ? 'status' : 'alert'}>
          {error.message}
        </p>
      )}
      {reporte.isSuccess && <Vista reporte={reporte.data} />}
    </>
  )
}

function Vista({ reporte }: { reporte: ReporteAsistencia }) {
  const docentes = reporte.ambito === 'DOCENTES'
  return (
    <section className="tarjeta" aria-label="Vista previa del reporte">
      <h2>
        {docentes ? 'Asistencia de docentes' : 'Asistencia de alumnos'} · {reporte.alcance} · {MESES[reporte.mes - 1]} {reporte.anio}
      </h2>
      <p className="muted">
        Generado el {formatoGeneracion.format(new Date(reporte.generadoEn))} · {reporte.diasLectivos}{' '}
        {reporte.diasLectivos === 1 ? 'día lectivo considerado' : 'días lectivos considerados'}
      </p>
      {reporte.parcial && (
        <p className="alerta" role="status">
          Reporte parcial: el mes sigue en curso y se calcula hasta el {fechaCorta(reporte.fechaCorte)}. El día actual solo entra cuando la jornada cierra.
        </p>
      )}
      {reporte.totalNoRegistrados > 0 && (
        <p className="alerta" role="status">
          Información incompleta: {reporte.totalNoRegistrados} {reporte.totalNoRegistrados === 1 ? 'día esperado' : 'días esperados'} sin registro. No se
          consideran faltas ni entran en el porcentaje
          {docentes ? '; en docentes puede señalar que el cierre diario no se ejecutó' : ''}.
        </p>
      )}
      <div className="tabla-scroll">
        <table aria-label="Resumen mensual de asistencia">
          <thead>
            <tr>
              <th scope="col">N.°</th>
              <th scope="col">Apellidos y nombres</th>
              {!docentes && <th scope="col">Sección</th>}
              <th scope="col">Puntual</th>
              <th scope="col">Tardanza</th>
              <th scope="col">Falta</th>
              <th scope="col">No registrados</th>
              <th scope="col">% de asistencia</th>
            </tr>
          </thead>
          <tbody>
            {reporte.filas.map((f, i) => (
              <tr key={f.id}>
                <td>{i + 1}</td>
                <td>{f.nombre}</td>
                {!docentes && <td>{f.seccion}</td>}
                <td>{f.puntual}</td>
                <td>{f.tardanza}</td>
                <td>{f.falta}</td>
                <td>{f.noRegistrados}</td>
                <td>{f.porcentaje === null ? <span className="muted">No disponible</span> : `${f.porcentaje} %`}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted">% de asistencia = 100 × (puntual + tardanza) / días registrados. La tardanza cuenta como presencia.</p>
    </section>
  )
}
