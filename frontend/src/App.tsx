import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Layout } from './Layout'
import { NAV_ITEMS } from './nav'
import { Admin } from './pages/Admin'
import { Dashboard } from './pages/Dashboard'
import { Landing } from './pages/Landing'
import { Login } from './pages/Login'
import { Products } from './pages/Products'
import { ScanConsole } from './pages/ScanConsole'
import { Verify } from './pages/Verify'
import type { ReactElement } from 'react'

const rolesFor = (path: string) => NAV_ITEMS.find((n) => n.path === path)?.roles

const guard = (path: string, page: ReactElement) => (
  <ProtectedRoute roles={rolesFor(path)}>{page}</ProtectedRoute>
)

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Landing />} />
        <Route path="login" element={<Login />} />
        <Route path="dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        <Route path="products" element={guard('/products', <Products />)} />
        <Route path="scan" element={guard('/scan', <ScanConsole />)} />
        <Route path="admin" element={guard('/admin', <Admin />)} />
        {/* Verify is reachable signed-out so the landing page box works; the page asks for sign-in. */}
        <Route path="verify" element={<Verify />} />
        <Route path="verify/:tagId" element={<Verify />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
