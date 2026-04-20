import { useState } from 'react'
import { workflowApi } from '../api/client.js'

const DEFAULT_FORM = {
  claimId: 'CLM-',
  memberId: 'MBR-',
  providerId: 'P-',
  amount: '245.12',
  diagnosisCode: 'X12',
  serviceDate: '2026-04-15',
}

function generateIdKey() {
  return 'idem-' + Math.random().toString(36).substring(2, 10)
}

export default function StartWorkflowForm({ onToast, onStarted }) {
  const [form, setForm] = useState(DEFAULT_FORM)
  const [loading, setLoading] = useState(false)

  const handleChange = (e) => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.claimId || !form.memberId) {
      onToast?.('claimId and memberId are required', 'error')
      return
    }
    setLoading(true)
    try {
      const payload = { ...form, amount: parseFloat(form.amount) }
      const result = await workflowApi.start(generateIdKey(), payload)
      onToast?.(`Workflow started: ${result.workflowId}`, 'success')
      onStarted?.(result)
      setForm({ ...DEFAULT_FORM, claimId: 'CLM-', memberId: 'MBR-', providerId: 'P-' })
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to start workflow'
      onToast?.(msg, 'error')
    } finally {
      setLoading(false)
    }
  }

  const fillDemo = () => {
    const n = Math.floor(Math.random() * 90000) + 10000
    setForm({
      claimId: `CLM-${n}`,
      memberId: `MBR-${Math.floor(Math.random() * 900) + 100}`,
      providerId: `P-${Math.floor(Math.random() * 900) + 100}`,
      amount: (Math.random() * 2000 + 100).toFixed(2),
      diagnosisCode: ['X12', 'Y44', 'Z99', 'A10', 'B22'][Math.floor(Math.random() * 5)],
      serviceDate: '2026-04-' + String(Math.floor(Math.random() * 20) + 1).padStart(2, '0'),
    })
  }

  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">🚀 Submit Claim Workflow</div>
        <button className="btn btn-ghost btn-sm" onClick={fillDemo} type="button">
          🎲 Random Demo
        </button>
      </div>

      <form onSubmit={handleSubmit}>
        <div className="form-grid">
          {[
            { name: 'claimId',       label: 'Claim ID',      placeholder: 'CLM-10001' },
            { name: 'memberId',      label: 'Member ID',     placeholder: 'MBR-771' },
            { name: 'providerId',    label: 'Provider ID',   placeholder: 'P-892' },
            { name: 'amount',        label: 'Amount ($)',    placeholder: '245.12' },
            { name: 'diagnosisCode', label: 'Diagnosis Code',placeholder: 'X12' },
            { name: 'serviceDate',   label: 'Service Date',  placeholder: '2026-04-15' },
          ].map(field => (
            <div key={field.name} className="form-group">
              <label className="form-label">{field.label}</label>
              <input
                className="form-input"
                name={field.name}
                value={form[field.name]}
                onChange={handleChange}
                placeholder={field.placeholder}
              />
            </div>
          ))}
        </div>

        <div className="mt-4">
          <button className="btn btn-primary w-full" type="submit" disabled={loading}>
            {loading
              ? <><span className="spinner" /> Submitting...</>
              : '⚡ Start Workflow'}
          </button>
        </div>
      </form>
    </div>
  )
}
