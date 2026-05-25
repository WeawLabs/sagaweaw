export type SagaStatusName =
  | 'STARTED'
  | 'EXECUTING'
  | 'COMPLETED'
  | 'COMPENSATING'
  | 'COMPENSATED'
  | 'FAILED'

export type StepStatusName =
  | 'PENDING'
  | 'EXECUTING'
  | 'COMPLETED'
  | 'FAILED'
  | 'COMPENSATING'
  | 'COMPENSATED'
  | 'SKIPPED'

export interface StepInstance {
  name: string
  order: number
  status: StepStatusName
  attempt: number
  maxAttempts: number
  inputPayload: string | null
  outputPayload: string | null
  lastError: string | null
  errorTrace: string | null
  nextRetryAt: string | null
  executedAt: string | null
  completedAt: string | null
  durationMs: number
}

export interface SagaInstance {
  id: string
  name: string
  status: SagaStatusName
  contextJson: string
  steps: StepInstance[]
  createdAt: string
  updatedAt: string
  completedAt: string | null
  version: number
}

export interface SagaMetrics {
  total: number
  started: number
  executing: number
  completed: number
  compensated: number
  failed: number
  deadLetters: number
  successRate: number
  byName: { name: string; total: number; completed: number; failed: number }[]
  outboxPending?: number
}

export interface RetryingStep {
  sagaId: string
  sagaName: string
  stepName: string
  attempt: number
  maxAttempts: number
  nextRetryAt: string
}

export interface SagaNameStats {
  name: string
  total: number
  completed: number
  failed: number
  avgDurationMs: number | null
}

export interface SagaEvent {
  id: string
  sagaId: string
  stepName: string | null
  eventType: string
  createdAt: string
  payload: string | null
}

export interface StepStats {
  stepName: string
  sagaName: string
  avgDurationMs: number | null
  total: number
  failed: number
}

export interface DeadLetter {
  id: string
  sagaId: string
  sagaName: string | null
  stepName: string
  errorMessage: string
  errorTrace: string | null
  contextSnapshot: string | null
  createdAt: string
  reprocessed: boolean
  reprocessedAt: string | null
  reprocessedBy: string | null
}


export interface StepDefinition {
  name:        string
  type:        'COMPENSABLE' | 'PIVOT' | 'RETRIABLE'
  order:       number
  compensable: boolean
}
