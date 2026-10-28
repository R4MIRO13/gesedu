import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { redirigirSiHaySesion } from '../compartido/auth/guardias'
import { useSolicitarRecuperacion } from '../modulos/seguridad/api'
import { esquemaSolicitudRecuperacion, type DatosSolicitudRecuperacion } from '../modulos/seguridad/esquemas'
import { mensajeDeInicioSesion } from '../modulos/seguridad/mensajes'

export const Route = createFileRoute('/recuperar-contrasena')({
  beforeLoad: () => redirigirSiHaySesion(),
  component: RecuperarContrasena,
})

function RecuperarContrasena() {
  const solicitar = useSolicitarRecuperacion()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<DatosSolicitudRecuperacion>({
    resolver: zodResolver(esquemaSolicitudRecuperacion),
    defaultValues: { correo: '' },
  })

  return (
    <main className="centrado">
      <form className="tarjeta login" onSubmit={handleSubmit((datos) => solicitar.mutate(datos))} noValidate>
        <div className="marca marca-grande">GESEDU</div>
        <h1>Recuperar contraseña</h1>
        <p className="muted">Escribe tu correo y te enviaremos un enlace para elegir una contraseña nueva.</p>

        {solicitar.isSuccess ? (
          <p className="alerta alerta-ok" role="status">
            {solicitar.data.mensaje}
          </p>
        ) : (
          <>
            <label className="campo">
              Correo
              <input
                type="email"
                autoComplete="username"
                aria-invalid={errors.correo ? true : undefined}
                autoFocus
                {...register('correo')}
              />
              {errors.correo && <span className="error-campo">{errors.correo.message}</span>}
            </label>
            {solicitar.error && (
              <p className="alerta alerta-error" role="alert">
                {mensajeDeInicioSesion(solicitar.error)}
              </p>
            )}
            <button className="btn btn-primario" type="submit" disabled={solicitar.isPending}>
              {solicitar.isPending ? 'Enviando…' : 'Enviar enlace'}
            </button>
          </>
        )}
        <p>
          <Link to="/login">Volver a iniciar sesión</Link>
        </p>
      </form>
    </main>
  )
}
