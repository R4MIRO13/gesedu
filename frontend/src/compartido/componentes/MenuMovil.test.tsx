import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { guardarSesion } from '../auth/sesion'
import { SESION_ADMIN, SESION_ALUMNO } from '../../test/msw/handlers'
import { renderizarRuta } from '../../test/utilidades'

/**
 * Menú plegable de móvil (RNF-02, observación del primer incremento): el botón ☰ de la cabecera abre y cierra el menú
 * lateral. jsdom no aplica CSS (css: false), así que el estado se verifica con aria-expanded y data-abierto, que son los
 * que el CSS usa para mostrar el panel bajo 768 px.
 */
async function abrirEnInicio(sesion = SESION_ALUMNO) {
  guardarSesion(sesion)
  const resultado = await renderizarRuta('/intranet/inicio')
  const boton = await screen.findByRole('button', { name: 'Menú' })
  const panel = document.getElementById('menu-principal')
  if (!panel) throw new Error('Falta el panel del menú')
  return { ...resultado, boton, panel }
}

describe('Menú plegable de móvil', () => {
  it('el botón ☰ controla el panel y empieza cerrado', async () => {
    const { boton, panel } = await abrirEnInicio()

    expect(boton).toHaveAttribute('aria-controls', 'menu-principal')
    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(panel).toHaveAttribute('data-abierto', 'false')
    expect(screen.queryByTestId('menu-fondo')).not.toBeInTheDocument()
  })

  it('abre el menú, lleva el foco al primer enlace y lo cierra con el mismo botón devolviéndole el foco', async () => {
    const usuario = userEvent.setup()
    const { boton, panel } = await abrirEnInicio()

    await usuario.click(boton)

    expect(boton).toHaveAttribute('aria-expanded', 'true')
    expect(panel).toHaveAttribute('data-abierto', 'true')
    expect(screen.getByTestId('menu-fondo')).toBeInTheDocument()
    await waitFor(() => expect(within(panel).getByRole('link', { name: 'Inicio' })).toHaveFocus())

    await usuario.click(boton)

    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(panel).toHaveAttribute('data-abierto', 'false')
    await waitFor(() => expect(boton).toHaveFocus())
  })

  it('Escape cierra el menú', async () => {
    const usuario = userEvent.setup()
    const { boton, panel } = await abrirEnInicio()
    await usuario.click(boton)

    await usuario.keyboard('{Escape}')

    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(panel).toHaveAttribute('data-abierto', 'false')
    await waitFor(() => expect(boton).toHaveFocus())
  })

  it('el clic en el fondo cierra el menú', async () => {
    const usuario = userEvent.setup()
    const { boton } = await abrirEnInicio()
    await usuario.click(boton)

    await usuario.click(screen.getByTestId('menu-fondo'))

    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByTestId('menu-fondo')).not.toBeInTheDocument()
  })

  it('elegir una opción navega y cierra el menú', async () => {
    const usuario = userEvent.setup()
    const { boton, panel, router } = await abrirEnInicio()
    await usuario.click(boton)

    await usuario.click(within(panel).getByRole('link', { name: 'Bloques horarios' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/horarios/bloques'))
    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(panel).toHaveAttribute('data-abierto', 'false')
  })

  it('elegir la página en la que ya se está también cierra el menú', async () => {
    const usuario = userEvent.setup()
    const { boton, panel } = await abrirEnInicio()
    await usuario.click(boton)

    await usuario.click(within(panel).getByRole('link', { name: 'Inicio' }))

    expect(boton).toHaveAttribute('aria-expanded', 'false')
  })

  it('el menú plegado ofrece solo las opciones de los permisos de la sesión (RF-50)', async () => {
    const usuario = userEvent.setup()
    const alumno = await abrirEnInicio(SESION_ALUMNO)
    await usuario.click(alumno.boton)
    expect(within(alumno.panel).queryByRole('link', { name: 'Auditoría' })).not.toBeInTheDocument()
    alumno.unmount()

    const admin = await abrirEnInicio(SESION_ADMIN)
    await usuario.click(admin.boton)
    expect(within(admin.panel).getByRole('link', { name: 'Auditoría' })).toBeInTheDocument()
  })
})
