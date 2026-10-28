import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, type SubmitEvent } from 'react'
import { redirigirSiHaySesion } from '../compartido/auth/guardias'
import { guardarSesion } from '../compartido/auth/sesion'
import { useIniciarSesion } from '../modulos/seguridad/api'

export const Route = createFileRoute('/login')({
  beforeLoad: () => redirigirSiHaySesion(),
  component: Login,
})

function Login() {
  const navegar = useNavigate()
  const iniciar = useIniciarSesion()
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  function enviar(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    setErrorLocal(null)
    if (!correo.trim() || !contrasena) {
      setErrorLocal('Ingresa tu correo y tu contraseña.')
      return
    }
    iniciar.mutate(
      { correo: correo.trim(), contrasena },
      {
        onSuccess: (sesion) => {
          guardarSesion(sesion)
          void navegar({ to: '/intranet/inicio' })
        },
      },
    )
  }

  const mensaje = errorLocal ?? iniciar.error?.message ?? null

  return (
    <main className="centrado">
      <form className="tarjeta login" onSubmit={enviar} noValidate>
        <div className="marca marca-grande">GESEDU</div>
        <h1>Iniciar sesión</h1>
        <p className="muted">Sistema de gestión del colegio</p>

        <label className="campo">
          Correo electrónico
          <input
            type="email"
            autoComplete="username"
            value={correo}
            onChange={(e) => setCorreo(e.target.value)}
            placeholder="usuario@demo.gesedu.pe"
            autoFocus
          />
        </label>
        <label className="campo">
          Contraseña
          <input
            type="password"
            autoComplete="current-password"
            value={contrasena}
            onChange={(e) => setContrasena(e.target.value)}
          />
        </label>

        {mensaje && (
          <p className="alerta alerta-error" role="alert">
            {mensaje}
          </p>
        )}

        <button className="btn btn-primario" type="submit" disabled={iniciar.isPending}>
          {iniciar.isPending ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </main>
  )
}
