import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { ApiError } from '../../../compartido/api/errores'
import {
  useActualizarUsuario,
  useCrearUsuario,
  useDetalleUsuario,
  type DetalleUsuario,
  type RolCreable,
} from '../api'
import { esquemaUsuario, type DatosUsuario } from '../esquemas'

const ROLES_CREABLES: { valor: RolCreable; texto: string }[] = [
  { valor: 'DOCENTE', texto: 'Docente' },
  { valor: 'APODERADO', texto: 'Apoderado' },
  { valor: 'ADMINISTRADOR', texto: 'Administrador' },
]

interface Props {
  /** Id de la cuenta que se edita; nulo para crear una nueva (UC-24). */
  id: string | null
  onCerrar: (mensaje?: string) => void
}

/**
 * Alta y edición de cuentas (UC-24, RF-01). Al crear no se pide contraseña: la cuenta recibe una invitación por correo
 * con un enlace de 72 horas para definirla (S-77). El rol no cambia al editar.
 */
export function DialogoUsuario({ id, onCerrar }: Props) {
  const detalle = useDetalleUsuario(id)
  if (id && detalle.isPending) {
    return (
      <div className="dialogo-fondo">
        <div className="dialogo tarjeta" role="dialog" aria-modal="true" aria-label="Editar usuario">
          <p className="muted">Cargando…</p>
        </div>
      </div>
    )
  }
  if (id && detalle.isError) {
    return (
      <div className="dialogo-fondo">
        <div className="dialogo tarjeta" role="dialog" aria-modal="true" aria-label="Editar usuario">
          <p className="alerta alerta-error" role="alert">
            {detalle.error.message}
          </p>
          <button type="button" className="btn" onClick={() => onCerrar()}>
            Cerrar
          </button>
        </div>
      </div>
    )
  }
  return <Formulario inicial={detalle.data ?? null} onCerrar={onCerrar} />
}

function Formulario({ inicial, onCerrar }: { inicial: DetalleUsuario | null; onCerrar: (mensaje?: string) => void }) {
  const crear = useCrearUsuario()
  const actualizar = useActualizarUsuario(inicial?.id ?? '')
  const editando = inicial !== null
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<DatosUsuario>({
    resolver: zodResolver(esquemaUsuario),
    defaultValues: {
      rol: (inicial?.rol as RolCreable | undefined) ?? 'DOCENTE',
      nombres: inicial?.nombres ?? '',
      apellidos: inicial?.apellidos ?? '',
      documento: inicial?.documento ?? '',
      correo: inicial?.correo ?? '',
      cargo: inicial?.cargo ?? '',
      especialidad: inicial?.especialidad ?? '',
      telefono: inicial?.telefono ?? '',
    },
  })
  const rol = useWatch({ control, name: 'rol' })
  const operacion = editando ? actualizar : crear

  useEffect(() => {
    const cerrar = (e: KeyboardEvent) => e.key === 'Escape' && onCerrar()
    window.addEventListener('keydown', cerrar)
    return () => window.removeEventListener('keydown', cerrar)
  }, [onCerrar])

  const enviar = handleSubmit((datos) => {
    if (inicial) {
      actualizar.mutate(
        { ...datos, version: inicial.version },
        { onSuccess: (d) => onCerrar(`Se guardaron los datos de ${d.nombres} ${d.apellidos}.`) },
      )
    } else {
      crear.mutate(datos, {
        onSuccess: (d) =>
          onCerrar(`Se creó la cuenta de ${d.nombres} ${d.apellidos}. Enviamos la invitación a ${d.correo} para que defina su contraseña.`),
      })
    }
  })

  const error = operacion.error
  const conflicto = error instanceof ApiError && error.codigo === 'CONFLICTO_CONCURRENCIA'

  return (
    <div className="dialogo-fondo">
      <form
        className="dialogo tarjeta"
        role="dialog"
        aria-modal="true"
        aria-label={editando ? 'Editar usuario' : 'Nuevo usuario'}
        onSubmit={enviar}
        noValidate
      >
        <h2>{editando ? 'Editar usuario' : 'Nuevo usuario'}</h2>
        {!editando && (
          <p className="muted">
            No se define ninguna contraseña: la persona recibe un correo con un enlace (vale 72 horas) para elegir la suya.
            Los estudiantes no se crean aquí: nacen con la solicitud de matrícula.
          </p>
        )}

        <label className="campo">
          Rol
          <select disabled={editando} {...register('rol')}>
            {ROLES_CREABLES.map((r) => (
              <option key={r.valor} value={r.valor}>
                {r.texto}
              </option>
            ))}
          </select>
        </label>
        <div className="dos-columnas">
          <label className="campo">
            Nombres
            <input aria-invalid={errors.nombres ? true : undefined} {...register('nombres')} />
            {errors.nombres && <span className="error-campo">{errors.nombres.message}</span>}
          </label>
          <label className="campo">
            Apellidos
            <input aria-invalid={errors.apellidos ? true : undefined} {...register('apellidos')} />
            {errors.apellidos && <span className="error-campo">{errors.apellidos.message}</span>}
          </label>
        </div>
        <div className="dos-columnas">
          <label className="campo">
            Documento de identidad
            <input inputMode="numeric" aria-invalid={errors.documento ? true : undefined} {...register('documento')} />
            {errors.documento && <span className="error-campo">{errors.documento.message}</span>}
          </label>
          <label className="campo">
            Correo
            <input type="email" aria-invalid={errors.correo ? true : undefined} {...register('correo')} />
            {errors.correo && <span className="error-campo">{errors.correo.message}</span>}
          </label>
        </div>
        {rol === 'ADMINISTRADOR' && (
          <label className="campo">
            Cargo
            <input aria-invalid={errors.cargo ? true : undefined} {...register('cargo')} />
            {errors.cargo && <span className="error-campo">{errors.cargo.message}</span>}
          </label>
        )}
        {rol === 'DOCENTE' && (
          <label className="campo">
            Especialidad (opcional)
            <input {...register('especialidad')} />
          </label>
        )}
        {rol === 'APODERADO' && (
          <label className="campo">
            Teléfono (opcional)
            <input inputMode="tel" {...register('telefono')} />
          </label>
        )}

        {error && (
          <p className="alerta alerta-error" role="alert">
            {conflicto ? 'Otra persona modificó esta cuenta. Cierra y vuelve a abrirla para ver los datos actuales.' : error.message}
          </p>
        )}
        <div className="acciones">
          <button type="button" className="btn" onClick={() => onCerrar()}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primario" disabled={operacion.isPending}>
            {operacion.isPending ? 'Guardando…' : editando ? 'Guardar cambios' : 'Crear y enviar invitación'}
          </button>
        </div>
      </form>
    </div>
  )
}
