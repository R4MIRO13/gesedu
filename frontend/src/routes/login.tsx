import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { redirigirSiHaySesion } from '../compartido/auth/guardias'
import { guardarSesion } from '../compartido/auth/sesion'
import { useIniciarSesion } from '../modulos/seguridad/api'
import { esquemaInicioSesion, type DatosInicioSesion } from '../modulos/seguridad/esquemas'
import { mensajeDeInicioSesion } from '../modulos/seguridad/mensajes'

export const Route = createFileRoute('/login')({
  beforeLoad: () => redirigirSiHaySesion(),
  component: Login,
})

function Login() {
  const navegar = useNavigate()
  const iniciar = useIniciarSesion()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<DatosInicioSesion>({
    resolver: zodResolver(esquemaInicioSesion),
    defaultValues: { correo: '', contrasena: '' },
  })

  function enviar(datos: DatosInicioSesion) {
    iniciar.mutate(datos, {
      onSuccess: (sesion) => {
        guardarSesion(sesion)
        void navegar({ to: '/intranet/inicio' })
      },
    })
  }

  return (
    <main className="centrado">
      <form className="tarjeta login" onSubmit={handleSubmit(enviar)} noValidate>
        <div className="marca marca-grande">GESEDU</div>
        <h1>Iniciar sesión</h1>
        <p className="muted">Sistema de gestión del colegio</p>

        <label className="campo">
          Correo
          <input
            type="email"
            autoComplete="username"
            placeholder="usuario@demo.gesedu.pe"
            aria-invalid={errors.correo ? true : undefined}
            autoFocus
            {...register('correo')}
          />
          {errors.correo && <span className="error-campo">{errors.correo.message}</span>}
        </label>
        <label className="campo">
          Contraseña
          <input
            type="password"
            autoComplete="current-password"
            aria-invalid={errors.contrasena ? true : undefined}
            {...register('contrasena')}
          />
          {errors.contrasena && <span className="error-campo">{errors.contrasena.message}</span>}
        </label>

        {iniciar.error && (
          <p className="alerta alerta-error" role="alert">
            {mensajeDeInicioSesion(iniciar.error)}
          </p>
        )}

        <button className="btn btn-primario" type="submit" disabled={iniciar.isPending}>
          {iniciar.isPending ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </main>
  )
}
