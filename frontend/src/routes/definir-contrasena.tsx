import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../compartido/api/errores'
import { redirigirSiHaySesion } from '../compartido/auth/guardias'
import { useRestablecerContrasena } from '../modulos/seguridad/api'
import {
  esquemaNuevaContrasena,
  MENSAJE_POLITICA_CONTRASENA,
  type DatosNuevaContrasena,
} from '../modulos/seguridad/esquemas'

export const Route = createFileRoute('/definir-contrasena')({
  validateSearch: (busqueda) => z.object({ token: z.string().catch('') }).parse(busqueda),
  beforeLoad: () => redirigirSiHaySesion(),
  component: DefinirContrasena,
})

function DefinirContrasena() {
  const { token } = Route.useSearch()
  const restablecer = useRestablecerContrasena()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<DatosNuevaContrasena>({
    resolver: zodResolver(esquemaNuevaContrasena),
    defaultValues: { nuevaContrasena: '', confirmacion: '' },
  })

  const enlaceInvalido =
    !token || (restablecer.error instanceof ApiError && restablecer.error.codigo === 'TOKEN_RECUPERACION_INVALIDO')

  return (
    <main className="centrado">
      <form
        className="tarjeta login"
        onSubmit={handleSubmit((datos) => restablecer.mutate({ token, nuevaContrasena: datos.nuevaContrasena }))}
        noValidate
      >
        <div className="marca marca-grande">GESEDU</div>
        <h1>Define tu contraseña</h1>

        {restablecer.isSuccess ? (
          <>
            <p className="alerta alerta-ok" role="status">
              Tu cuenta está activa. Ya puedes iniciar sesión con tu correo y la contraseña que elegiste.
            </p>
            <Link to="/login" className="btn btn-primario">
              Iniciar sesión
            </Link>
          </>
        ) : enlaceInvalido ? (
          <>
            <p className="alerta alerta-error" role="alert">
              El enlace de invitación no es válido o ya venció. Pide a la administración del colegio que te envíe una invitación nueva.
            </p>
            <Link to="/login" className="btn btn-primario">
              Ir al inicio de sesión
            </Link>
          </>
        ) : (
          <>
            <p className="muted">{MENSAJE_POLITICA_CONTRASENA}</p>
            <label className="campo">
              Contraseña
              <input
                type="password"
                autoComplete="new-password"
                aria-invalid={errors.nuevaContrasena ? true : undefined}
                autoFocus
                {...register('nuevaContrasena')}
              />
              {errors.nuevaContrasena && <span className="error-campo">{errors.nuevaContrasena.message}</span>}
            </label>
            <label className="campo">
              Repite la contraseña
              <input
                type="password"
                autoComplete="new-password"
                aria-invalid={errors.confirmacion ? true : undefined}
                {...register('confirmacion')}
              />
              {errors.confirmacion && <span className="error-campo">{errors.confirmacion.message}</span>}
            </label>
            {restablecer.error && (
              <p className="alerta alerta-error" role="alert">
                {restablecer.error.message}
              </p>
            )}
            <button className="btn btn-primario" type="submit" disabled={restablecer.isPending}>
              {restablecer.isPending ? 'Guardando…' : 'Guardar contraseña'}
            </button>
          </>
        )}
      </form>
    </main>
  )
}
