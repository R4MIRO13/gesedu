import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { ApiError } from '../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import { usePermiso } from '../../../../compartido/auth/usePermiso'
import {
  useCrearSeccion,
  useGrados,
  useModificarSeccion,
  type DocenteTutor,
  type GradoConSecciones,
  type Seccion,
} from '../../../../modulos/academico/api'
import {
  esquemaSeccion,
  esquemaSeccionNueva,
  type DatosSeccion,
  type DatosSeccionNueva,
} from '../../../../modulos/academico/esquemas'

export const Route = createFileRoute('/intranet/academico/$anio/secciones')({
  beforeLoad: () => exigirPermiso('ACADEMICO_CONSULTAR'),
  component: SeccionesYVacantes,
})

/** RF-03 y RF-04: secciones de cada grado con su capacidad, sus vacantes y su tutor. */
function SeccionesYVacantes() {
  const { anio: anioTexto } = Route.useParams()
  const anio = Number(anioTexto)
  const grados = useGrados(anio)
  const puedeConfigurar = usePermiso('ACADEMICO_CONFIGURAR')
  const [aviso, setAviso] = useState<string | null>(null)

  if (grados.isPending) return <p className="muted">Cargando…</p>
  if (grados.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {grados.error.message}
      </p>
    )
  }
  const editable = puedeConfigurar && grados.data.estado !== 'CERRADO'
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Secciones y vacantes · Año escolar {anio}</h1>
          <p className="muted">
            Las vacantes se descuentan solas con cada matrícula: aquí solo se cambia la capacidad y el tutor de cada sección.
          </p>
        </div>
        {grados.data.estado === 'CERRADO' && <span className="etiqueta etiqueta-alerta">Año cerrado: solo consulta</span>}
      </div>
      {aviso && (
        <p className="alerta alerta-ok" role="status">
          {aviso}
        </p>
      )}
      {grados.data.grados.length === 0 && <p className="muted">Este año aún no tiene grados.</p>}
      {grados.data.grados.map((g) => (
        <TarjetaGrado key={g.id} anio={anio} grado={g} docentes={grados.data.docentes} editable={editable} alAvisar={setAviso} />
      ))}
    </>
  )
}

function mensajeDe(error: unknown): string | null {
  if (!error) return null
  if (error instanceof ApiError) return error.message
  return 'No se pudo guardar. Inténtalo de nuevo.'
}

function TarjetaGrado({
  anio,
  grado,
  docentes,
  editable,
  alAvisar,
}: {
  anio: number
  grado: GradoConSecciones
  docentes: DocenteTutor[]
  editable: boolean
  alAvisar: (mensaje: string | null) => void
}) {
  const [editando, setEditando] = useState<string | null>(null)
  return (
    <section className="tarjeta tarjeta-grado" aria-label={`${grado.numeroNivel}° de secundaria`}>
      <h2>{grado.numeroNivel}° de secundaria</h2>
      {grado.secciones.length === 0 ? (
        <p className="muted">Sin secciones todavía.</p>
      ) : (
        <div className="tabla-scroll">
          <table aria-label={`Secciones de ${grado.numeroNivel}°`}>
            <thead>
              <tr>
                <th>Sección</th>
                <th>Capacidad</th>
                <th>Matriculados</th>
                <th>Vacantes</th>
                <th>Tutor</th>
                <th>
                  <span className="solo-lectores">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {grado.secciones.map((s) =>
                editando === s.id ? (
                  <FilaEdicion
                    key={s.id}
                    anio={anio}
                    seccion={s}
                    docentes={docentes}
                    alTerminar={(mensaje) => {
                      setEditando(null)
                      alAvisar(mensaje)
                    }}
                  />
                ) : (
                  <tr key={s.id}>
                    <td>
                      <strong>{s.nombre}</strong>
                    </td>
                    <td>{s.capacidad}</td>
                    <td>{s.matriculados}</td>
                    <td>
                      <span className={s.vacantes === 0 ? 'etiqueta etiqueta-alerta' : 'etiqueta'}>{s.vacantes}</span>
                    </td>
                    <td>{s.tutor ?? <span className="muted">Sin tutor</span>}</td>
                    <td>
                      {editable && (
                        <button type="button" className="btn" aria-label={`Editar ${s.nombre}`} onClick={() => (alAvisar(null), setEditando(s.id))}>
                          Editar
                        </button>
                      )}
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>
      )}
      {editable && <FormularioNueva anio={anio} grado={grado} docentes={docentes} alAvisar={alAvisar} />}
    </section>
  )
}

function SelectorTutor({ docentes, ...props }: { docentes: DocenteTutor[] } & React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select {...props}>
      <option value="">Sin tutor</option>
      {docentes.map((d) => (
        <option key={d.id} value={d.id}>
          {d.nombre}
        </option>
      ))}
    </select>
  )
}

function FilaEdicion({
  anio,
  seccion,
  docentes,
  alTerminar,
}: {
  anio: number
  seccion: Seccion
  docentes: DocenteTutor[]
  alTerminar: (mensaje: string) => void
}) {
  const modificar = useModificarSeccion(anio)
  const form = useForm<DatosSeccion>({
    resolver: zodResolver(esquemaSeccion),
    defaultValues: { capacidad: String(seccion.capacidad), tutorDocenteId: seccion.tutorDocenteId ?? '' },
  })
  const errores = form.formState.errors

  function enviar(datos: DatosSeccion) {
    modificar.mutate(
      { id: seccion.id, version: seccion.version, capacidad: Number(datos.capacidad), tutorDocenteId: datos.tutorDocenteId || null },
      { onSuccess: (guardada) => alTerminar(`Se guardó ${guardada.nombre}: capacidad ${guardada.capacidad}, ${guardada.vacantes} vacantes.`) },
    )
  }

  return (
    <tr className="fila-activa">
      <td>
        <strong>{seccion.nombre}</strong>
      </td>
      <td colSpan={5}>
        <form className="fila-edicion" onSubmit={form.handleSubmit(enviar)} noValidate aria-label={`Editar ${seccion.nombre}`}>
          <label className="campo campo-corto">
            Capacidad
            <input inputMode="numeric" {...form.register('capacidad')} aria-invalid={!!errores.capacidad} />
          </label>
          <label className="campo">
            Tutor
            <SelectorTutor docentes={docentes} {...form.register('tutorDocenteId')} />
          </label>
          <span className="muted">{seccion.matriculados} matriculados</span>
          <div className="acciones">
            <button type="submit" className="btn btn-primario" disabled={modificar.isPending}>
              Guardar
            </button>
            <button type="button" className="btn" onClick={() => alTerminar('')}>
              Cancelar
            </button>
          </div>
          {errores.capacidad && <span className="error-campo">{errores.capacidad.message}</span>}
          {mensajeDe(modificar.error) && (
            <span className="error-campo" role="alert">
              {mensajeDe(modificar.error)}
            </span>
          )}
        </form>
      </td>
    </tr>
  )
}

function FormularioNueva({
  anio,
  grado,
  docentes,
  alAvisar,
}: {
  anio: number
  grado: GradoConSecciones
  docentes: DocenteTutor[]
  alAvisar: (mensaje: string | null) => void
}) {
  const crear = useCrearSeccion(anio)
  const form = useForm<DatosSeccionNueva>({
    resolver: zodResolver(esquemaSeccionNueva),
    defaultValues: { letra: '', capacidad: '30', tutorDocenteId: '' },
  })
  const errores = form.formState.errors

  function enviar(datos: DatosSeccionNueva) {
    alAvisar(null)
    crear.mutate(
      { gradoId: grado.id, letra: datos.letra.trim().toUpperCase(), capacidad: Number(datos.capacidad), tutorDocenteId: datos.tutorDocenteId || null },
      {
        onSuccess: (creada) => {
          alAvisar(`Se agregó la sección ${creada.nombre} con ${creada.vacantes} vacantes.`)
          form.reset({ letra: '', capacidad: '30', tutorDocenteId: '' })
        },
      },
    )
  }

  return (
    <form className="filtros" onSubmit={form.handleSubmit(enviar)} noValidate aria-label={`Agregar sección a ${grado.numeroNivel}°`}>
      <label className="campo campo-corto">
        Letra
        <input maxLength={1} {...form.register('letra')} aria-invalid={!!errores.letra} />
        {errores.letra && <span className="error-campo">{errores.letra.message}</span>}
      </label>
      <label className="campo campo-corto">
        Capacidad
        <input inputMode="numeric" {...form.register('capacidad')} aria-invalid={!!errores.capacidad} />
        {errores.capacidad && <span className="error-campo">{errores.capacidad.message}</span>}
      </label>
      <label className="campo">
        Tutor
        <SelectorTutor docentes={docentes} {...form.register('tutorDocenteId')} />
      </label>
      <button type="submit" className="btn" disabled={crear.isPending}>
        Agregar sección
      </button>
      {mensajeDe(crear.error) && (
        <p className="alerta alerta-error" role="alert">
          {mensajeDe(crear.error)}
        </p>
      )}
    </form>
  )
}
