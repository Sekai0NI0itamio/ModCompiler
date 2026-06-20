import { Routes, Route, Navigate } from 'react-router-dom'
import { Sidebar } from './components/Sidebar'
import { BundleManager } from './pages/BundleManager'
import { BundleEditor } from './pages/BundleEditor'
import { PublishDashboard } from './pages/PublishDashboard'

export default function App() {
  return (
    <div className="flex h-screen w-screen bg-surface">
      <Sidebar />
      <main className="flex-1 overflow-auto">
        <Routes>
          <Route path="/" element={<BundleManager />} />
          <Route path="/editor" element={<BundleEditor />} />
          <Route path="/publish" element={<PublishDashboard />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}
