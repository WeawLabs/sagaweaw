import type { SagaInstance, SagaMetrics, SagaEvent, DeadLetter, RetryingStep, SagaNameStats, StepStats, StepDefinition } from './types'
import { getToken } from '../auth'

const BASE = '/api'

export class AuthError extends Error {}

type UnauthHandler = () => void
let unauthHandler: UnauthHandler | null = null
export function setUnauthHandler(fn: UnauthHandler) { unauthHandler = fn }

function authHeaders(): HeadersInit {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`, { headers: authHeaders() })
  if (res.status === 403 || res.status === 401) {
    unauthHandler?.()
    throw new AuthError(`${res.status} ${res.statusText}`)
  }
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json()
}

async function download(path: string, filename: string): Promise<void> {
  const res = await fetch(`${BASE}${path}`, { headers: authHeaders() })
  if (res.status === 403 || res.status === 401) { unauthHandler?.(); throw new AuthError(`${res.status}`) }
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  const blob = await res.blob()
  const url  = URL.createObjectURL(blob)
  const a    = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

async function post(path: string, body?: unknown): Promise<void> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { ...authHeaders(), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 403 || res.status === 401) {
    unauthHandler?.()
    throw new AuthError(`${res.status} ${res.statusText}`)
  }
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.error) msg = body.error
    } catch { /* ignore parse errors */ }
    throw new Error(msg)
  }
}

export const api = {
  sagas: {
    list: (params?: { status?: string; name?: string; id?: string; contextSearch?: string; page?: number; size?: number }) => {
      const qs = new URLSearchParams()
      if (params?.status)        qs.set('status', params.status)
      if (params?.name)          qs.set('name', params.name)
      if (params?.id)            qs.set('id', params.id)
      if (params?.contextSearch) qs.set('contextSearch', params.contextSearch)
      if (params?.page != null) qs.set('page', String(params.page))
      if (params?.size != null) qs.set('size', String(params.size))
      return get<SagaInstance[]>(`/sagas?${qs}`)
    },
    get:     (id: string) => get<SagaInstance>(`/sagas/${id}`),
    events:  (id: string) => get<SagaEvent[]>(`/sagas/${id}/events`),
    related:    (id: string)       => get<SagaInstance[]>(`/sagas/${id}/related`),
    definition: (sagaName: string) => get<StepDefinition[]>(`/sagas/definition/${encodeURIComponent(sagaName)}`),
    metrics:    ()          => get<SagaMetrics>('/sagas/metrics'),
    retrying:   ()          => get<RetryingStep[]>('/sagas/steps/retrying'),
    stuck:      ()          => get<SagaInstance[]>('/sagas/stuck'),
    exportCsv:  (params?: { status?: string; from?: string }) => {
      const qs = new URLSearchParams()
      if (params?.status) qs.set('status', params.status)
      if (params?.from)   qs.set('from',   params.from)
      const suffix = params?.status ? `-${params.status.toLowerCase()}` : ''
      return download(`/sagas/export?${qs}`, `sagas${suffix}.csv`)
    },
    statsByName: ()         => get<SagaNameStats[]>('/sagas/stats/by-name'),
    stepsStats:  ()         => get<StepStats[]>('/sagas/steps/stats'),
  },
  deadLetters: {
    list: (params?: { includeReprocessed?: boolean }) => {
      const q = new URLSearchParams()
      if (params?.includeReprocessed) q.set('includeReprocessed', 'true')
      return get<DeadLetter[]>(`/dead-letters?${q}`)
    },
    reprocess:      (id: string)    => post(`/dead-letters/${id}/reprocess`),
    reprocessBatch: (ids: string[]) => post('/dead-letters/reprocess-batch', { ids }),
    exportCsv:      ()              => download('/dead-letters/export', 'dead-letters.csv'),
  },
}
