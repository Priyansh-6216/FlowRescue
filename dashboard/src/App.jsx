import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import Dashboard from './pages/Dashboard.jsx'

function Sidebar() {
  return (
    <div className="sidebar">
      <div className="sidebar-brand">
        <div className="brand-logo">
          <div className="brand-icon">⚡</div>
          <div className="brand-name">FlowRescue</div>
        </div>
        <div className="brand-tagline">
          Distributed Workflow<br />Recovery Engine
        </div>
      </div>

      <nav className="sidebar-nav">
        <div className="nav-section-label">Monitor</div>

        <NavLink
          to="/"
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
          end
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
            <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
          </svg>
          Workflows
          <span className="live-dot" />
        </NavLink>

        <div className="nav-section-label" style={{ marginTop: 16 }}>System</div>

        <div className="nav-item" style={{ cursor: 'default' }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="3"/><path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83"/>
          </svg>
          LocalStack
          <span style={{
            marginLeft: 'auto', fontSize: 10, padding: '2px 6px',
            background: 'rgba(16,185,129,0.15)', color: '#34d399',
            borderRadius: 4, fontWeight: 600
          }}>LOCAL</span>
        </div>

        <div className="nav-item" style={{ cursor: 'default' }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
          Step Machine
          <span style={{
            marginLeft: 'auto', fontSize: 10, padding: '2px 6px',
            background: 'rgba(59,130,246,0.15)', color: '#60a5fa',
            borderRadius: 4, fontWeight: 600
          }}>ASL</span>
        </div>
      </nav>

      <div style={{
        padding: '16px 20px',
        borderTop: '1px solid var(--border)',
        fontSize: 11,
        color: 'var(--text-muted)',
        lineHeight: 1.7
      }}>
        <div style={{ fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 4 }}>
          Services
        </div>
        {[
          { name: 'workflow-api', port: 8080 },
          { name: 'task-runner',  port: 8083 },
          { name: 'recovery-svc', port: 8081 },
          { name: 'compensation', port: 8082 },
        ].map(s => (
          <div key={s.name} style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>{s.name}</span>
            <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent)' }}>:{s.port}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="layout">
        <Sidebar />
        <main className="main-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}
