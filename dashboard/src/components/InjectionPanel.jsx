import { useState, useEffect } from 'react'
import { injectionApi } from '../api/client.js'

const STEPS = [
  'VALIDATE_MEMBER',
  'CHECK_DUPLICATE_CLAIM',
  'CALCULATE_PAYABLE',
  'CREATE_PAYMENT_INSTRUCTION',
  'PERSIST_CLAIM_DECISION',
  'SEND_NOTIFICATION',
]

const STEP_LABELS = {
  'VALIDATE_MEMBER':            'Validate Member',
  'CHECK_DUPLICATE_CLAIM':      'Duplicate Check',
  'CALCULATE_PAYABLE':          'Calculate Payable',
  'CREATE_PAYMENT_INSTRUCTION': 'Payment Instruction ⚠️',
  'PERSIST_CLAIM_DECISION':     'Persist Decision ⚠️',
  'SEND_NOTIFICATION':          'Send Notification',
}

export default function InjectionPanel({ onToast }) {
  const [flags, setFlags] = useState({})
  const [loading, setLoading] = useState(false)

  const fetchFlags = async () => {
    try {
      const data = await injectionApi.getFlags()
      setFlags(data)
    } catch {
      setFlags({})
    }
  }

  useEffect(() => { fetchFlags() }, [])

  const toggle = async (step, currentValue) => {
    const newVal = !currentValue
    setFlags(prev => ({ ...prev, [step]: newVal }))
    try {
      await injectionApi.setFlag(step, newVal)
      onToast?.(
        newVal
          ? `⚠️ Failure injection ON — ${step}`
          : `✅ Failure injection OFF — ${step}`,
        newVal ? 'error' : 'success'
      )
    } catch (e) {
      onToast?.('Failed to toggle flag', 'error')
      setFlags(prev => ({ ...prev, [step]: currentValue }))
    }
  }

  const resetAll = async () => {
    setLoading(true)
    try {
      await injectionApi.resetAll()
      await fetchFlags()
      onToast?.('All failure injections cleared', 'success')
    } catch {
      onToast?.('Reset failed', 'error')
    } finally {
      setLoading(false)
    }
  }

  const activeCount = Object.values(flags).filter(Boolean).length

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <div className="card-title">🔥 Failure Injection</div>
          <div className="text-xs text-muted mt-1">
            Toggle per-step failures to demo recovery flows
            {activeCount > 0 && (
              <span style={{ color: '#f87171', marginLeft: 8 }}>
                ({activeCount} active)
              </span>
            )}
          </div>
        </div>
        <button
          className="btn btn-ghost btn-sm"
          onClick={resetAll}
          disabled={loading}
        >
          {loading ? <span className="spinner" /> : '🔄 Reset All'}
        </button>
      </div>

      <div className="injection-grid">
        {STEPS.map(step => {
          const active = !!flags[step]
          return (
            <div key={step} className={`injection-item ${active ? 'active' : ''}`}>
              <div>
                <div className="injection-step-name">{STEP_LABELS[step]}</div>
                <div className="text-xs text-muted mt-1">
                  {active ? '💥 Will throw error' : 'Normal execution'}
                </div>
              </div>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={active}
                  onChange={() => toggle(step, active)}
                />
                <span className="toggle-slider" />
              </label>
            </div>
          )
        })}
      </div>

      <div className="mt-4 text-xs text-muted" style={{ lineHeight: 1.7 }}>
        ⚠️ Steps marked with <strong>⚠️</strong> have side effects — failures there trigger
        <strong style={{ color: 'var(--color-purple)' }}> compensation logic</strong> after max retries.
      </div>
    </div>
  )
}
