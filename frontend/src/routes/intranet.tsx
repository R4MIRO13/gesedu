import { createFileRoute, Outlet } from '@tanstack/react-router'
import { exigirSesion } from '../compartido/auth/guardias'
import { Layout } from '../compartido/componentes/Layout'

export const Route = createFileRoute('/intranet')({
  beforeLoad: () => exigirSesion(),
  component: () => (
    <Layout>
      <Outlet />
    </Layout>
  ),
})
