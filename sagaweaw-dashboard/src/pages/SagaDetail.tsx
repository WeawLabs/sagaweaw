import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import type { SagaInstance, StepInstance, StepDefinition } from '../api/types'
import StatusBadge from '../components/StatusBadge'
import CopyButton from '../components/CopyButton'
import { fmtDuration as fmtDur } from '../utils/fmt'

// ------------------------------------------------------------------ helpers

const TERMINAL = new Set(['COMPLETED', 'COMPENSATED', 'FAILED'])

function fmtDuration(ms: number) {
  if (ms <= 0) return '—'
  return fmtDur(ms)
}

// ------------------------------------------------------------------ step circle

type StepStatus = StepInstance['status']

const circleClass: Record<StepStatus, string> = {
  COMPLETED:    'step-circle step-circle-completed',
  FAILED:       'step-circle step-circle-failed',
  COMPENSATED:  'step-circle step-circle-compensated',
  COMPENSATING: 'step-circle step-circle-executing',
  EXECUTING:    'step-circle step-circle-executing',
  PENDING:      'step-circle bg-muted border border-border text-gray-400',
  SKIPPED:      'step-circle bg-muted border border-border text-gray-400',
}

const circleIcon: Record<StepStatus, string> = {
  COMPLETED: '✓', FAILED: '✕', COMPENSATED: '↩',
  COMPENSATING: '↩', EXECUTING: '●', PENDING: '○', SKIPPED: '—',
}

const barColor: Partial<Record<StepStatus, string>> = {
  COMPLETED:   '#1a1a1a',
  FAILED:      '#D99E6A',
  COMPENSATED: 'rgba(217,158,106,.5)',
}

// ------------------------------------------------------------------ StepRow

function StepRow({ step, maxMs }: { step: StepInstance; maxMs: number }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const pct = maxMs > 0 ? Math.round((step.durationMs / maxMs) * 100) : 0

  const timeStr = step.executedAt
    ? new Date(step.executedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    : null

  return (
    <div className="step relative mb-5 flex gap-4 items-start last:mb-0">
      <div className={circleClass[step.status]}>{circleIcon[step.status]}</div>

      <div className="flex-1">
        {/* name row */}
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[16px] font-semibold text-ink">{step.name}</span>
            {step.status === 'COMPENSATED' && (
              <span className="text-[13px] text-accent font-medium">
                {t('detail.compensatedLabel')}
              </span>
            )}
          </div>
          <span className="text-[13px] text-gray-600 whitespace-nowrap font-medium">
            {fmtDuration(step.durationMs)}
            {timeStr && <span className="text-gray-500"> · {timeStr}</span>}
          </span>
        </div>

        {/* duration bar */}
        {pct > 0 && (
          <div className="mt-2 h-1.5 bg-muted rounded-full overflow-hidden">
            <div
              className="h-full rounded-full"
              style={{ width: `${Math.max(pct, 3)}%`, background: barColor[step.status] ?? '#e5dfd4' }}
            />
          </div>
        )}

        {/* chips */}
        <div className="flex gap-2 mt-2 flex-wrap items-center">
          <StatusBadge status={step.status} />
          {step.attempt > 0 && (
            <span className="bg-muted text-gray-700 text-[12px] px-3 py-[3px] rounded-full border border-border font-medium">
              {step.maxAttempts > 0
                ? t('detail.attempt',     { current: step.attempt, max: step.maxAttempts })
                : t('detail.attemptOpen', { current: step.attempt })}
            </span>
          )}
          {step.nextRetryAt && (
            <span className="text-[13px] text-accent font-medium">
              {t('detail.nextRetry', { time: new Date(step.nextRetryAt).toLocaleString() })}
            </span>
          )}
        </div>

        {/* error card */}
        {step.lastError && (
          <div className="error-card mt-3">
            <div className="flex items-start justify-between gap-2">
              <button
                onClick={() => setOpen(!open)}
                className="flex-1 text-left flex items-start gap-2 text-ink"
              >
                <span className="text-[14px] mt-0.5">{open ? '▾' : '▸'}</span>
                <span className="font-mono text-[13px] font-semibold break-all">{step.lastError}</span>
              </button>
              <CopyButton text={step.errorTrace ?? step.lastError} />
            </div>
            {open && step.errorTrace && (
              <pre className="mt-3 text-[12px] text-gray-700 overflow-x-auto max-h-48 leading-relaxed font-mono bg-canvas rounded-lg p-3">
                {step.errorTrace}
              </pre>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ SagaDetail

export default function SagaDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()

  const [saga,       setSaga]       = useState<SagaInstance | null>(null)
  const [related,    setRelated]    = useState<SagaInstance[]>([])
  const [definition, setDefinition] = useState<StepDefinition[]>([])
  const [error,      setError]      = useState<string | null>(null)
  const [tab,        setTab]        = useState<'timeline' | 'context' | 'flow'>('timeline')

  const locale = i18n.language.startsWith('pt') ? 'pt-BR' : 'en-US'

  useEffect(() => {
    if (!id) return
    let active = true

    const load = async () => {
      try {
        const s = await api.sagas.get(id)
        if (active) setSaga(s)
      } catch (e) {
        if (active) setError(e instanceof Error ? e.message : t('detail.notFound'))
      }
    }

    load()
    const interval = setInterval(() => {
      if (saga && TERMINAL.has(saga.status)) return
      load()
    }, 3000)

    api.sagas.related(id!).then(setRelated).catch(() => {})

    return () => { active = false; clearInterval(interval) }
  }, [id, saga?.status, t])

  useEffect(() => {
    if (!saga?.name) return
    api.sagas.definition(saga.name).then(setDefinition).catch(() => {})
  }, [saga?.name])

  if (error) return <div className="error-card text-[14px]">{error}</div>

  if (!saga) return (
    <div className="bg-surface rounded-xl border border-border px-4 py-10
                    text-center text-[15px] text-gray-600">
      {t('detail.loading')}
    </div>
  )

  const duration = saga.completedAt
    ? new Date(saga.completedAt).getTime() - new Date(saga.createdAt).getTime()
    : Date.now() - new Date(saga.createdAt).getTime()

  const sorted = [...saga.steps].sort((a, b) => a.order - b.order)
  const maxMs  = Math.max(...sorted.map(s => s.durationMs ?? 0), 1)

  const contextJson = JSON.stringify(JSON.parse(saga.contextJson || '{}'), null, 2)

  const tabItems = [
    { key: 'timeline' as const, label: t('detail.tabTimeline') },
    { key: 'context'  as const, label: t('detail.tabContext')  },
    { key: 'flow'     as const, label: t('detail.tabFlow')     },
  ]

  return (
    <div className="h-full overflow-y-auto pb-4">
      {/* breadcrumb */}
      <div className="text-[14px] text-gray-600 font-medium mb-5 flex items-center gap-2">
        <button onClick={() => navigate('/')} className="hover:text-ink transition-colors">
          {t('detail.breadcrumb')}
        </button>
        <span className="text-gray-400">›</span>
        <span className="text-ink font-semibold">{saga.name}</span>
        <span className="text-gray-500 font-mono text-[13px]">#{saga.id.slice(0, 8)}</span>
      </div>

      {/* saga card */}
      <div className="bg-surface rounded-xl border border-border p-5 mb-5 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-[22px] font-bold text-ink">{saga.name}</h1>
            <div className="flex items-center gap-2 mt-1">
              <span className="font-mono text-[13px] text-gray-600">{saga.id}</span>
              <CopyButton text={saga.id} />
            </div>
          </div>
          <StatusBadge status={saga.status} />
        </div>

        <div className="text-[14px] text-gray-600 font-medium mt-3">
          {t('detail.startedAt', {
            time:     new Date(saga.createdAt).toLocaleString(locale),
            duration: fmtDuration(duration),
          })}
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4">
          {[
            { label: t('detail.steps'),    value: `${saga.steps.filter(s => s.status === 'COMPLETED').length}/${saga.steps.length}` },
            { label: t('detail.duration'), value: fmtDuration(duration) },
            { label: t('detail.created'),  value: new Date(saga.createdAt).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit', second: '2-digit' }) },
            { label: t('detail.version'),  value: `v${saga.version}` },
          ].map(({ label, value }) => (
            <div key={label} className="bg-canvas rounded-xl px-4 py-3">
              <div className="text-[12px] text-gray-600 font-semibold uppercase tracking-wide">{label}</div>
              <div className="text-[18px] font-bold text-ink mt-1">{value}</div>
            </div>
          ))}
        </div>
      </div>

      {/* tabs */}
      <div className="flex gap-1 mb-4">
        {tabItems.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`px-4 py-2 rounded-lg text-[14px] font-semibold transition-colors ${
              tab === key ? 'bg-ink text-canvas' : 'text-gray-600 hover:text-ink hover:bg-muted'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'timeline' && (
        <div className="bg-surface rounded-xl border border-border p-5">
          {/* legend */}
          <div className="flex gap-5 mb-5 pb-4 border-b border-border flex-wrap">
            {[
              { cls: 'status-dot-ok',   label: t('detail.legendCompleted')   },
              { cls: 'status-dot-warn', label: t('detail.legendCompensated') },
              { cls: 'status-dot-fail', label: t('detail.legendFailed')      },
            ].map(({ cls, label }) => (
              <div key={label} className="flex items-center gap-2 text-[13px] text-gray-700 font-medium">
                <span className={`status-dot ${cls}`} />
                {label}
              </div>
            ))}
          </div>

          <div className="timeline-track">
            {sorted.map(step => (
              <StepRow key={step.name} step={step} maxMs={maxMs} />
            ))}
          </div>
        </div>
      )}

      {tab === 'context' && (
        <div className="bg-surface rounded-xl border border-border overflow-hidden">
          <div className="flex items-center justify-between px-5 py-3 border-b border-border bg-canvas">
            <span className="text-[14px] font-semibold text-ink">{t('detail.tabContext')}</span>
            <CopyButton text={contextJson} />
          </div>
          <pre className="text-[14px] text-ink leading-relaxed overflow-x-auto p-5 font-mono">
            {contextJson}
          </pre>
        </div>
      )}

      {tab === 'flow' && (
        <div className="bg-surface rounded-xl border border-border p-5">
          {definition.length === 0 ? (
            <span className="text-[13px] text-gray-400">{t('detail.flowNoData')}</span>
          ) : (
            <div className="flex flex-wrap items-center gap-0">
              {definition.map((step, i) => {
                const typeColor =
                  step.type === 'COMPENSABLE' ? 'border-ok   bg-ok/10   text-ok'
                : step.type === 'PIVOT'       ? 'border-warn bg-warn/10 text-warn'
                :                              'border-accent bg-accent/10 text-accent'
                return (
                  <div key={step.name} className="flex items-center">
                    <div className={`flex flex-col items-center px-3 py-2 rounded-xl border-2 ${typeColor} min-w-[90px]`}>
                      <span className="text-[11px] font-bold uppercase tracking-wider opacity-70">{step.type}</span>
                      <span className="text-[13px] font-semibold text-ink mt-0.5 text-center">{step.name}</span>
                      {step.compensable && (
                        <span className="text-[10px] mt-0.5 opacity-60">↩ compensable</span>
                      )}
                    </div>
                    {i < definition.length - 1 && (
                      <span className="text-gray-400 text-[18px] px-2">→</span>
                    )}
                  </div>
                )
              })}
            </div>
          )}
          <div className="flex gap-4 mt-5 pt-4 border-t border-border flex-wrap">
            {([
              { color: 'bg-ok',     label: t('detail.flowCompensable') },
              { color: 'bg-warn',   label: t('detail.flowPivot')       },
              { color: 'bg-accent', label: t('detail.flowRetriable')   },
            ] as const).map(({ color, label }) => (
              <div key={label} className="flex items-center gap-1.5 text-[12px] text-gray-600">
                <span className={`w-2.5 h-2.5 rounded-sm ${color} opacity-70`} />
                {label}
              </div>
            ))}
          </div>
        </div>
      )}

      {related.length > 0 && (
        <div className="mt-5">
          <div className="text-[11px] font-semibold text-ink/50 uppercase tracking-wider mb-2">
            {t('detail.related')}
          </div>
          <div className="flex flex-col gap-1.5">
            {related.map(r => {
              const dur = r.completedAt
                ? new Date(r.completedAt).getTime() - new Date(r.createdAt).getTime()
                : null
              return (
                <button
                  key={r.id}
                  onClick={() => navigate(`/sagas/${r.id}`)}
                  className="bg-surface rounded-lg border border-border text-left px-3 py-2
                             hover:border-accent hover:shadow-sm transition-all duration-200 w-full"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2 min-w-0">
                      <span className="text-[13px] font-semibold text-ink truncate">{r.name}</span>
                      <span className="text-[11px] text-gray-500 font-mono">#{r.id.slice(0, 8)}</span>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      {dur !== null && (
                        <span className="text-[12px] text-gray-500">{fmtDuration(dur)}</span>
                      )}
                      <StatusBadge status={r.status} />
                    </div>
                  </div>
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
