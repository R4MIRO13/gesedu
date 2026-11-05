import { Link } from '@tanstack/react-router'
import { romano, useCursosAlumno, useMisAlumnos } from '../api'

/** La tarjeta «Mis notas» del inicio del alumno: las notas del último bimestre consolidado, por curso. */
export function ResumenNotas() {
  const alumnos = useMisAlumnos()
  const alumno = alumnos.data?.[0]
  const cursos = useCursosAlumno(alumno?.alumnoId, null, null)
  if (!alumno || !cursos.data) return null
  const consolidado = cursos.data.bimestres.some((b) => b.consolidado)

  return (
    <section className="tarjeta" aria-label="Mis notas">
      <h2>Mis notas{consolidado ? ` del Bimestre ${romano(cursos.data.bimestre)}` : ''}</h2>
      {!consolidado && <p className="muted">Aún no hay notas consolidadas. Aparecerán cuando el colegio consolide el bimestre.</p>}
      {consolidado && (
        <ul aria-label="Notas por curso">
          {cursos.data.cursos.map((c) => (
            <li key={c.cursoId}>
              <strong>{c.curso}</strong>: {c.competencias.map((k) => `${k.codigo} ${k.bimestres[String(cursos.data.bimestre)] ?? '—'}`).join(' · ')}
            </li>
          ))}
        </ul>
      )}
      <p>
        <Link to="/intranet/notas">Ver mis notas y mi libreta</Link>
      </p>
    </section>
  )
}
