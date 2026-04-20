import React from 'react'

const STEPS = [
  { key: 'VALIDATE_MEMBER',            label: 'Validate\nMember',      icon: '👤' },
  { key: 'CHECK_DUPLICATE_CLAIM',      label: 'Duplicate\nCheck',      icon: '🔍' },
  { key: 'CALCULATE_PAYABLE',          label: 'Calculate\nPayable',    icon: '🧮' },
  { key: 'CREATE_PAYMENT_INSTRUCTION', label: 'Payment\nInstruction',  icon: '💳' },
  { key: 'PERSIST_CLAIM_DECISION',     label: 'Persist\nDecision',     icon: '💾' },
  { key: 'SEND_NOTIFICATION',          label: 'Send\nNotification',    icon: '📨' },
]

function getStepState(step, completedSteps, currentStep, status, failedStep) {
  if (completedSteps && completedSteps.includes(step.key)) {
    if (failedStep && !completedSteps.includes(failedStep) && status === 'COMPENSATING') {
      return 'compensated'
    }
    return 'completed'
  }
  if (currentStep === step.key && (status === 'RUNNING')) return 'active'
  if (failedStep === step.key) return 'failed'
  return 'pending'
}

export default function StepTimeline({ workflow }) {
  if (!workflow) return null
  const { completedSteps = [], currentStep, status, failedStep } = workflow

  const completedCount = completedSteps.length

  return (
    <div className="timeline">
      {STEPS.map((step, idx) => {
        const state = getStepState(step, completedSteps, currentStep, status, failedStep)
        const isLast = idx === STEPS.length - 1

        return (
          <div className="timeline-step" key={step.key}>
            <div className="step-node">
              <div
                className={`step-circle ${state}`}
                title={step.key}
              >
                {state === 'completed' ? '✓'
                  : state === 'failed'     ? '✗'
                  : state === 'compensated'? '↩'
                  : step.icon}
              </div>
              <div className="step-label" style={{ whiteSpace: 'pre-line' }}>
                {step.label}
              </div>
            </div>
            {!isLast && (
              <div
                className={`step-connector ${
                  completedSteps.includes(step.key)      ? 'done'
                  : currentStep === step.key && status === 'RUNNING' ? 'partial'
                  : ''
                }`}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}
