import { useState, useEffect, useCallback } from 'react'
import { workflowApi } from '../api/client.js'
import { useWorkflowSocket } from '../hooks/useWorkflowSocket.js'
import StatusBadge from '../components/StatusBadge.jsx'
import StepTimeline from '../components/StepTimeline.jsx'
import StartWorkflowForm from '../components/StartWorkflowForm.jsx'
import InjectionPanel from '../components/InjectionPanel.jsx'

function formatTime(iso) {
  if (!iso) return '—'
  try { return new Date(iso).toLocaleTimeString() } catch { return iso }
}

function Toast({ toasts }) {
  return (
    <div className="toast-container">
      {toasts.map(t => (
        <div key={t.id} className={`toast ${t.type}`}>{t.msg}</div>
      ))}
    </div>
  )
}

export default function Dashboard() {
  const [workflows, setWorkflows] = useState([])
  const [selected, setSelected] = useState(null)
  const [toasts, setToasts] = useState([])
  const [tab, setTab] = useState('workflows') // workflows | inject | submit

  const addToast = (msg, type = 'info') => {
    const id = Date.now()
    setToasts(prev => [...prev, { id, msg, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000)
  }

  // Initial load
  useEffect(() => {
    workflowApi.list().then(setWorkflows).catch(() => {})
  }, [])

  // Live WebSocket updates
  const { connected } = useWorkflowSocket((updatedList) => {
    setWorkflows(updatedList)
  })

  // Stats
  const stats = {
    total:    workflows.length,
    running:  workflows.filter(w => w.status === 'RUNNING').length,
    success:  workflows.filter(w => w.status === 'SUCCESS').length,
    failed:   workflows.filter(w => ['FAILED','MANUAL_REVIEW'].includes(w.status)).length,
    recovery: workflows.filter(w => ['COMPENSATING','COMPENSATED','RETRY_SCHEDULED'].includes(w.status)).length,
  }

  const handleRecover = async (wf) => {
    try {
      const result = await workflowApi.recover(wf.workflowId)
      addToast(`Recovery triggered for ${wf.workflowId}`, 'info')
      setSelected(result)
    } catch (e) {
      addToast('Recovery failed: ' + (e.response?.data?.message || e.message), 'error')
    }
  }

  const handleCompensate = async (wf) => {
    try {
      const result = await workflowApi.compensate(wf.workflowId)
      addToast(`Compensation started for ${wf.workflowId}`, 'info')
      setSelected(result)
    } catch (e) {
      addToast('Compensation failed', 'error')
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-base)' }}>
      <Toast toasts={toasts} />

      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="glow-header" style={{ marginLeft: 240 }}>
        <div className="flex items-center justify-between">
          <div>
            <h1 className="page-title">⚡ FlowRescue</h1>
            <p className="page-subtitle">
              Distributed Workflow Failure Recovery Engine — Healthcare Claims
            </p>
          </div>
          <div className="flex items-center gap-3">
            {connected
              ? <span className="live-indicator"><span className="dot" /> LIVE</span>
              : <span className="live-indicator" style={{ borderColor: 'rgba(239,68,68,0.3)', color: '#f87171' }}>
                  <span className="dot" style={{ background: '#f87171' }} /> OFFLINE
                </span>
            }
          </div>
        </div>

        {/* Tab bar */}
        <div className="flex gap-2 mt-4">
          {[
            { id: 'workflows', label: '📋 Workflows' },
            { id: 'submit',    label: '🚀 Submit Claim' },
            { id: 'inject',    label: '🔥 Inject Failure' },
          ].map(t => (
            <button
              key={t.id}
              className={`btn btn-sm ${tab === t.id ? 'btn-primary' : 'btn-ghost'}`}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div style={{ marginLeft: 240, padding: '28px 32px' }}>
        {/* ── Stats ────────────────────────────────────────────────────── */}
        <div className="stats-grid" style={{ padding: 0, marginBottom: 28 }}>
          {[
            { label: 'Total',    value: stats.total,    color: '#94a3b8' },
            { label: 'Running',  value: stats.running,  color: 'var(--color-info)' },
            { label: 'Success',  value: stats.success,  color: 'var(--color-success)' },
            { label: 'Failed',   value: stats.failed,   color: 'var(--color-danger)' },
            { label: 'Recovery', value: stats.recovery, color: 'var(--color-purple)' },
          ].map(s => (
            <div key={s.label} className="stat-card" style={{ '--stat-color': s.color }}>
              <div className="stat-value">{s.value}</div>
              <div className="stat-label">{s.label}</div>
            </div>
          ))}
        </div>

        {/* ── Main content ──────────────────────────────────────────────── */}
        {tab === 'submit' && (
          <StartWorkflowForm
            onToast={addToast}
            onStarted={(wf) => { setTab('workflows'); setSelected(wf) }}
          />
        )}

        {tab === 'inject' && (
          <InjectionPanel onToast={addToast} />
        )}

        {tab === 'workflows' && (
          <div style={{ display: 'grid', gridTemplateColumns: selected ? '1fr 420px' : '1fr', gap: 20 }}>
            {/* Workflow table */}
            <div className="table-wrap">
              {workflows.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-icon">📭</div>
                  <div className="empty-title">No workflows yet</div>
                  <div className="empty-desc">
                    Submit a claim via the "Submit Claim" tab or run the demo script to see workflows appear here live.
                  </div>
                </div>
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th>Workflow ID</th>
                      <th>Status</th>
                      <th>Step Progress</th>
                      <th>Retries</th>
                      <th>Started</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {workflows.map(wf => (
                      <tr
                        key={wf.workflowId}
                        onClick={() => setSelected(wf)}
                        style={selected?.workflowId === wf.workflowId
                          ? { background: 'var(--accent-soft)' } : {}}
                      >
                        <td className="mono">{wf.workflowId}</td>
                        <td><StatusBadge status={wf.status} /></td>
                        <td>
                          <span className="text-xs text-muted">
                            {wf.completedSteps?.length || 0}/6 steps
                          </span>
                          <div style={{ marginTop: 4, height: 4, background: 'var(--bg-elevated)', borderRadius: 2, width: 80 }}>
                            <div style={{
                              height: '100%',
                              borderRadius: 2,
                              width: `${((wf.completedSteps?.length || 0) / 6) * 100}%`,
                              background: wf.status === 'SUCCESS' ? 'var(--color-success)'
                                        : wf.status === 'FAILED'  ? 'var(--color-danger)'
                                        : 'var(--accent)',
                              transition: 'width 0.4s ease',
                            }} />
                          </div>
                        </td>
                        <td style={{ color: wf.retryCount > 0 ? '#fbbf24' : 'var(--text-muted)' }}>
                          {wf.retryCount || 0}
                        </td>
                        <td className="mono">{formatTime(wf.startedAt)}</td>
                        <td onClick={e => e.stopPropagation()}>
                          <div className="flex gap-2">
                            {['FAILED','MANUAL_REVIEW'].includes(wf.status) && (
                              <button className="btn btn-ghost btn-sm" onClick={() => handleRecover(wf)}>
                                ↺ Retry
                              </button>
                            )}
                            {['FAILED','RUNNING'].includes(wf.status) && (
                              <button className="btn btn-ghost btn-sm" onClick={() => handleCompensate(wf)}>
                                ↩ Undo
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Detail panel */}
            {selected && (
              <div className="card" style={{ height: 'fit-content', position: 'sticky', top: 28 }}>
                <div className="card-header">
                  <div>
                    <div className="card-title">Workflow Detail</div>
                    <div className="mono text-xs text-muted mt-1">{selected.workflowId}</div>
                  </div>
                  <button className="btn btn-ghost btn-sm" onClick={() => setSelected(null)}>✕</button>
                </div>

                <StatusBadge status={selected.status} />

                <div className="mt-4">
                  <div className="detail-section-title">Step Progress</div>
                  <StepTimeline workflow={selected} />
                </div>

                {selected.lastErrorCode && (
                  <div className="mt-4 audit-entry failure" style={{ borderRadius: 'var(--radius-sm)' }}>
                    <div>
                      <div className="font-bold text-sm" style={{ color: '#f87171' }}>
                        {selected.lastErrorCode}
                      </div>
                      <div className="text-xs text-muted mt-1">{selected.lastErrorMessage}</div>
                      {selected.failedStep && (
                        <div className="text-xs mt-1">
                          Failed at: <span className="mono text-accent">{selected.failedStep}</span>
                        </div>
                      )}
                    </div>
                  </div>
                )}

                {selected.nextRetryAt && (
                  <div className="mt-3 audit-entry warn" style={{ borderRadius: 'var(--radius-sm)' }}>
                    <div className="text-xs">
                      ⏱️ Retry scheduled at{' '}
                      <span className="mono text-accent">{formatTime(selected.nextRetryAt)}</span>
                    </div>
                  </div>
                )}

                {selected.compensationReason && (
                  <div className="mt-3 audit-entry compensate" style={{ borderRadius: 'var(--radius-sm)' }}>
                    <div className="text-xs">↩️ {selected.compensationReason}</div>
                  </div>
                )}

                <div className="meta-grid mt-4">
                  {[
                    { label: 'Retries',     value: selected.retryCount || 0 },
                    { label: 'Started',     value: formatTime(selected.startedAt) },
                    { label: 'Updated',     value: formatTime(selected.updatedAt) },
                  ].map(m => (
                    <div key={m.label} className="meta-item">
                      <div className="meta-item-label">{m.label}</div>
                      <div className="meta-item-value">{m.value}</div>
                    </div>
                  ))}
                </div>

                <div className="flex gap-2 mt-4">
                  {['FAILED','MANUAL_REVIEW'].includes(selected.status) && (
                    <button
                      className="btn btn-success btn-sm"
                      onClick={() => handleRecover(selected)}
                    >
                      ↺ Force Retry
                    </button>
                  )}
                  {['FAILED','RUNNING','RETRY_SCHEDULED'].includes(selected.status) && (
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={() => handleCompensate(selected)}
                    >
                      ↩ Compensate
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
