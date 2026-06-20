import { useLocation, useNavigate } from 'react-router-dom'
import { Package, FileEdit, Rocket, Settings } from 'lucide-react'

const navItems = [
  { path: '/', icon: Package, label: 'Bundles' },
  { path: '/editor', icon: FileEdit, label: 'Editor' },
  { path: '/publish', icon: Rocket, label: 'Publish' },
]

export function Sidebar() {
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <aside className="w-16 bg-charcoal border-r border-white/5 flex flex-col items-center py-4 gap-2">
      {/* Logo */}
      <div className="w-10 h-10 rounded-lg bg-teal/10 flex items-center justify-center mb-6 glow-teal">
        <span className="text-teal font-mono font-bold text-sm">C</span>
      </div>

      {/* Nav Items */}
      {navItems.map((item) => {
        const isActive = location.pathname === item.path
        return (
          <button
            key={item.path}
            onClick={() => navigate(item.path)}
            className={`w-10 h-10 rounded-lg flex items-center justify-center transition-all duration-200 group relative ${
              isActive
                ? 'bg-teal/15 text-teal glow-teal'
                : 'text-white/30 hover:text-white/60 hover:bg-white/5'
            }`}
            title={item.label}
          >
            <item.icon size={20} />
            {/* Tooltip */}
            <span className="absolute left-14 bg-surface-light text-white text-xs px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap z-50">
              {item.label}
            </span>
          </button>
        )
      })}

      {/* Spacer */}
      <div className="flex-1" />

      {/* Settings (placeholder) */}
      <button
        className="w-10 h-10 rounded-lg flex items-center justify-center text-white/20 hover:text-white/50 hover:bg-white/5 transition-all"
        title="Settings"
      >
        <Settings size={18} />
      </button>
    </aside>
  )
}
