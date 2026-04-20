import { useState, useEffect } from 'react'
import { workflowApi } from '../api/client.js'

const STATUS_COLORS = {
  RUNNING:         '#60a5fa',
  SUCCESS:         '#34d399',
  FAILED:          '#f87171',
  COMPENSATING:    '#c084fc',
  COMPENSATED:     '#a78bfa',
  RETRY_SCHEDULED: '#fbbf24',
  MANUAL_REVIEW:   '#fca5a5',
  CREATED:         '#9ca3af',
}

const STATUS_EMOJI = {
  RUNNING:         '⚡',
  SUCCESS:         '✅',
  FAILED:          '❌',
  COMPENSATING:    '↩️',
  COMPENSATED:     '🔁',
  RETRY_SCHEDULED: '⏱️',
  MANUAL_REVIEW:   '🚨',
  CREATED:         '🔄',
}

export default function StatusBadge({ status }) {
  if (!status) return null
  const color = STATUS_COLORS[status] || '#9ca3af'
  const emoji = STATUS_EMOJI[status] || '•'
  return (
    <span className={`badge badge-${status}`}>
      <span>{emoji}</span>
      {status.replace('_', ' ')}
    </span>
  )
}
