import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Layout } from './Layout'
import { NAV_ITEMS } from './nav'
import { Admin } from './pages/Admin'
import { Alerts } from './pages/Alerts'
import { Analytics } from './pages/Analytics'
import { Dashboard } from './pages/Dashboard'
import { Landing } from './pages/Landing'
import { Login } from './pages/Login'
import { Products } from './pages/Products'
import { PublicCheck } from './pages/PublicCheck'
import { QrLabels } from './pages/QrLabels'
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
        <Route path="alerts" element={guard('/alerts', <Alerts />)} />
        <Route path="analytics" element={guard('/analytics', <Analytics />)} />
        {/* Labels are printed from the Product Registry, so they share its roles. */}
        <Route path="labels/:batchId" element={guard('/products', <QrLabels />)} />
        {/* Verify is reachable signed-out; the page offers sign-in or the public check. */}
        <Route path="verify" element={<Verify />} />
        <Route path="verify/:tagId" element={<Verify />} />
        {/* Public consumer check: no account needed. QR labels link here. */}
        <Route path="check" element={<PublicCheck />} />
        <Route path="check/:tagId" element={<PublicCheck />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
