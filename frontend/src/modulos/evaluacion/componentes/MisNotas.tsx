import { useState } from 'react'
import { ApiError } from '../../../compartido/api/errores'
import { fechaCorta } from '../../../compartido/formato'
import { guardarArchivo } from '../../../compartido/guardarArchivo'
import {
  descargarLibretaPdf,
  romano,
  useCursosAlumno,
  useLibreta,
  useMisAlumnos,
  type CursoNotas,
  type CursosAlumno,
} from '../api'

/** UC-19: el alumno (o el apoderado, que elige a uno de sus hijos, FA-1) consulta sus cursos, sus notas y su libreta. */
export function MisNotas() {
  const alumnos = useMisAlumnos()
  const [elegido, setElegido] = useState<string | null>(null)

  if (alumnos.isPending) return <p className="muted">Cargando…</p>
  if (alumnos.isError) {
    return (
      <>
        <h1>Mis notas</h1>
        <p className="alerta alerta-error" role="alert">
          {alumnos.error.message}
        </p>
      </>
    )
  }
  if (alumnos.data.length === 0) {
    return (
      <>
        <h1>Mis notas</h1>
        <p className="alerta" role="status">
          No hay estudiantes asociados a tu cuenta.
        </p>
      </>
    )
  }
  const alumno = alumnos.data.find((a) => a.alumnoId === elegido) ?? alumnos.data[0]!
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Mis notas</h1>
          <p className="muted">Inicio › Notas y libreta</p>
        </div>
      </div>
      {alumnos.data.length > 1 && (
        <section className="tarjeta" aria-label="Selección de estudiante">
          <div className="campo">
            <label htmlFor="estudiante-notas">Estudiante</label>
            <select id="estudiante-notas" name="estudiante" value={alumno.alumnoId} onChange={(e) => setElegido(e.target.value)}>
              {alumnos.data.map((a) => (
                <option key={a.alumnoId} value={a.alumnoId}>
                  {a.nombre}
                  {a.seccion ? ` · ${a.seccion}` : ''}
                </option>
              ))}
            </select>
          </div>
        </section>
      )}
      <NotasDelAlumno key={alumno.alumnoId} alumnoId={alumno.alumnoId} />
    </>
  )
}

function NotasDelAlumno({ alumnoId }: { alumnoId: string }) {
  const [anio, setAnio] = useState<number | null>(null)
  const [bimestre, setBimestre] = useState<number | null>(null)
  const cursos = useCursosAlumno(alumnoId, anio, bimestre)

  if (cursos.isPending) return <p className="muted">Cargando…</p>
  if (cursos.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {cursos.error.message}
      </p>
    )
  }
  return (
    <Notas
      datos={cursos.data}
      alCambiarAnio={(nuevo) => {
        setAnio(nuevo)
        setBimestre(null)
      }}
      alCambiarBimestre={setBimestre}
    />
  )
}

function Notas({
  datos,
  alCambiarAnio,
  alCambiarBimestre,
}: {
  datos: CursosAlumno
  alCambiarAnio: (anio: number) => void
  alCambiarBimestre: (bimestre: number) => void
}) {
  const [detalle, setDetalle] = useState<string | null>(null)
  const [verLibreta, setVerLibreta] = useState(false)
  const [errorDescarga, setErrorDescarga] = useState<string | null>(null)
  const libreta = useLibreta(datos.alumnoId, datos.anio, verLibreta)
  const hayNotas = datos.bimestres.some((b) => b.consolidado)

  async function descargar(bimestre: number | null) {
    setErrorDescarga(null)
    try {
      const { blob, nombre } = await descargarLibretaPdf(datos.alumnoId, datos.anio, bimestre)
      guardarArchivo(blob, nombre)
    } catch (error) {
      setErrorDescarga(error instanceof ApiError ? error.message : 'No se pudo descargar el archivo')
    }
  }

  if (datos.anio === null) {
    return (
      <p className="alerta" role="status">
        Aún no hay cursos ni notas para {datos.alumno}.
      </p>
    )
  }
  return (
    <section className="tarjeta" aria-label="Mis cursos">
      <div className="dos-columnas">
        <div className="campo">
          <label htmlFor="anio-notas">Año escolar</label>
          <select id="anio-notas" name="anio" value={datos.anio} onChange={(e) => alCambiarAnio(Number(e.target.value))}>
            {datos.anios.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
        </div>
        <div className="campo">
          <label htmlFor="bimestre-notas">Bimestre</label>
          <select id="bimestre-notas" name="bimestre" value={datos.bimestre} onChange={(e) => alCambiarBimestre(Number(e.target.value))}>
            {datos.bimestres.map((b) => (
              <option key={b.numero} value={b.numero}>
                Bimestre {romano(b.numero)}
                {b.consolidado ? '' : ' (sin consolidar)'}
              </option>
            ))}
          </select>
        </div>
      </div>
      <h2>
        Mis cursos{datos.seccion ? ` · ${datos.seccion}` : ''} · Nota del Bimestre {romano(datos.bimestre)}
      </h2>
      <p className="muted">
        {datos.alumno} · {datos.codigo}
        {datos.libreta ? ` · Libreta ${datos.libreta === 'FINAL' ? 'final' : 'parcial'}` : ''}
      </p>
      {!hayNotas && (
        <p className="alerta" role="status">
          Todavía no hay notas consolidadas en {datos.anio}. Aparecerán cuando el colegio consolide cada bimestre.
        </p>
      )}
      {errorDescarga && (
        <p className="alerta alerta-error" role="alert">
          {errorDescarga}
        </p>
      )}

      <div className="rejilla" role="list" aria-label="Cursos">
        {datos.cursos.map((c) => (
          <Curso key={c.cursoId} curso={c} bimestre={datos.bimestre} abierto={detalle === c.cursoId} alAlternar={() => setDetalle(detalle === c.cursoId ? null : c.cursoId)} />
        ))}
        {datos.cursos.length === 0 && <p className="muted">No hay cursos registrados para este año.</p>}
      </div>

      <div className="acciones">
        <button type="button" className="btn" disabled={!datos.bimestres.find((b) => b.numero === datos.bimestre)?.consolidado} onClick={() => void descargar(datos.bimestre)}>
          Descargar informe de progreso
        </button>
        <button type="button" className="btn" disabled={!hayNotas} onClick={() => setVerLibreta((v) => !v)}>
          {verLibreta ? 'Ocultar libreta' : 'Ver libreta completa'}
        </button>
        <button type="button" className="btn" disabled={!hayNotas} onClick={() => void descargar(null)}>
          Descargar libreta
        </button>
      </div>

      {verLibreta && libreta.isPending && <p className="muted">Cargando la libreta…</p>}
      {verLibreta && libreta.isError && (
        <p className="alerta alerta-error" role="alert">
          {libreta.error.message}
        </p>
      )}
      {verLibreta && libreta.isSuccess && (
        <div className="tabla-scroll">
          <h3>
            Libreta {libreta.data.estado === 'FINAL' ? 'final' : 'parcial'} {libreta.data.anio} · actualizada el{' '}
            {fechaCorta(libreta.data.fechaActualizacion)}
          </h3>
          <table aria-label="Libreta de notas">
            <thead>
              <tr>
                <th scope="col">Curso</th>
                <th scope="col">Competencia</th>
                {libreta.data.bimestres.map((b) => (
                  <th key={b} scope="col">
                    {romano(b)}
                  </th>
                ))}
                <th scope="col">Final</th>
              </tr>
            </thead>
            <tbody>
              {libreta.data.cursos.flatMap((c) =>
                c.competencias.map((k) => (
                  <tr key={`${c.cursoId}-${k.competenciaId}`}>
                    <td>{c.curso}</td>
                    <td>
                      {k.codigo} · {k.nombre}
                    </td>
                    {libreta.data.bimestres.map((b) => (
                      <td key={b}>{k.bimestres[String(b)] ?? '—'}</td>
                    ))}
                    <td>
                      <strong>{k.anual ?? '—'}</strong>
                    </td>
                  </tr>
                )),
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function Curso({ curso, bimestre, abierto, alAlternar }: { curso: CursoNotas; bimestre: number; abierto: boolean; alAlternar: () => void }) {
  return (
    <article className="tarjeta" role="listitem" aria-label={curso.curso}>
      <h3>{curso.curso}</h3>
      <p className="muted">Prof. {curso.docente}</p>
      <p aria-label={`Notas de ${curso.curso} en el bimestre ${romano(bimestre)}`}>
        {curso.competencias.map((k) => `${k.codigo} ${k.bimestres[String(bimestre)] ?? '—'}`).join(' · ') || 'Sin competencias'}
      </p>
      <button type="button" className="btn" aria-expanded={abierto} aria-label={`${abierto ? 'Ocultar' : 'Ver'} detalle de ${curso.curso}`} onClick={alAlternar}>
        {abierto ? 'Ocultar detalle' : 'Ver detalle'}
      </button>
      {abierto && (
        <table aria-label={`Detalle de ${curso.curso}`}>
          <thead>
            <tr>
              <th scope="col">Competencia</th>
              <th scope="col">Nivel esperado</th>
              {[1, 2, 3, 4].map((b) => (
                <th key={b} scope="col">
                  {romano(b)}
                </th>
              ))}
              <th scope="col">Final</th>
            </tr>
          </thead>
          <tbody>
            {curso.competencias.map((k) => (
              <tr key={k.competenciaId}>
                <td>
                  {k.codigo} · {k.nombre}
                </td>
                <td>{k.nivelEsperado ?? '—'}</td>
                {[1, 2, 3, 4].map((b) => (
                  <td key={b}>{k.bimestres[String(b)] ?? '—'}</td>
                ))}
                <td>
                  <strong>{k.anual ?? '—'}</strong>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </article>
  )
}
