import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
})

const taskApi = axios.create({
  baseURL: import.meta.env.VITE_TASK_RUNNER_URL || 'http://localhost:8083',
  timeout: 10000,
})

// ── Workflow API ───────────────────────────────────────────────────────────────

export const workflowApi = {
  list: () => api.get('/api/v1/workflows').then(r => r.data),

  getById: (id) => api.get(`/api/v1/workflows/${id}`).then(r => r.data),

  start: (idempotencyKey, payload) =>
    api.post('/api/v1/workflows/claims', payload, {
      headers: { 'Idempotency-Key': idempotencyKey }
    }).then(r => r.data),

  recover: (id) => api.post(`/api/v1/workflows/${id}/recover`).then(r => r.data),

  compensate: (id) => api.post(`/api/v1/workflows/${id}/compensate`).then(r => r.data),

  replay: (id, fromStep) =>
    api.post(`/api/v1/workflows/${id}/replay`, { fromStep }).then(r => r.data),
}

// ── Failure Injection API ─────────────────────────────────────────────────────

export const injectionApi = {
  getFlags: () => taskApi.get('/api/v1/inject').then(r => r.data),

  setFlag: (stepName, fail) =>
    taskApi.post(`/api/v1/inject/${stepName}`, { fail }).then(r => r.data),

  resetAll: () => taskApi.delete('/api/v1/inject').then(r => r.data),
}
