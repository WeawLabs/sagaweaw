import { useState, useEffect, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import type { RetryingStep, SagaStatusName } from '../api/types'
import Select from './Select'
import EmptyState from './EmptyState'

const MIN_W = 200
const MAX_W = 420
const DEF_W = 260
const RETRY_PAGE_SIZE = 5

const STATUSES: SagaStatusName[] = [
  'STARTED', 'EXECUTING', 'COMPLETED', 'COMPENSATING', 'COMPENSATED', 'FAILED',
]

interface Props {
  status: SagaStatusName | ''
  onStatusChange: (v: SagaStatusName | '') => void
  name: string
  onNameChange: (v: string) => void
  sagaCount: number
  loading: boolean
  connected: boolean
  outboxPending: number | null
  successRate: number | null
  completed: number | null
  compensated: number | null
  failed: number | null
}

function SuccessBar({ rate, completed, compensated, failed, label }: {
  rate: number; completed: number; compensated: number; failed: number; label: string
}) {
  const total   = completed + compensated + failed
  const pct     = Math.min(100, Math.max(0, rate))
  const okPct   = total > 0 ? (completed   / total) * 100 : 0
  const warnPct = total > 0 ? (compensated / total) * 100 : 0
  const failPct = total > 0 ? (failed      / total) * 100 : 0
  const color   = pct >= 80 ? 'text-ok' : pct >= 50 ? 'text-warn' : 'text-fail'
  return (
    <div className="flex flex-col gap-2 pt-3 border-t border-border flex-shrink-0">
      <span className="text-[11px] font-semibold text-ink/50 uppercase tracking-wider">{label}</span>
      <div className="flex items-baseline gap-1">
        <span className={`text-[28px] font-bold leading-none ${color}`}>{pct.toFixed(0)}</span>
        <span className={`text-[13px] font-semibold ${color}`}>%</span>
      </div>
      <div className="flex h-[6px] rounded-full overflow-hidden bg-border gap-px">
        {okPct   > 0 && <div className="bg-ok   rounded-l-full" style={{ width: `${okPct}%`,   transition: 'width 0.6s ease' }} />}
        {warnPct > 0 && <div className="bg-warn"                style={{ width: `${warnPct}%`, transition: 'width 0.6s ease' }} />}
        {failPct > 0 && <div className="bg-fail  rounded-r-full" style={{ width: `${failPct}%`, transition: 'width 0.6s ease' }} />}
      </div>
      <div className="flex items-center gap-1.5 text-[11px]">
        <span className="text-ok font-medium flex items-center gap-1">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
          {completed}
        </span>
        <span className="text-gray-300">·</span>
        <span className="text-warn font-medium flex items-center gap-1">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 .49-4.95"/></svg>
          {compensated}
        </span>
        <span className="text-gray-300">·</span>
        <span className="text-fail font-medium flex items-center gap-1">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          {failed}
        </span>
      </div>
    </div>
  )
}

export default function SidePanel({
  status, onStatusChange, name, onNameChange,
  sagaCount, loading, connected, outboxPending,
  successRate, completed, compensated, failed,
}: Props) {
  const { t } = useTranslation()

  const [collapsed,  setCollapsed]  = useState(
    () => localStorage.getItem('sagaweaw.sidebar.collapsed') === 'true'
  )
  const [width, setWidth] = useState(() => {
    const v = parseInt(localStorage.getItem('sagaweaw.sidebar.width') ?? '')
    return isNaN(v) ? DEF_W : Math.max(MIN_W, Math.min(MAX_W, v))
  })
  const [retrying,  setRetrying]  = useState<RetryingStep[]>([])
  const [retryPage, setRetryPage] = useState(0)

  const dragging = useRef(false)
  const startX   = useRef(0)
  const startW   = useRef(0)
  const curW     = useRef(width)

  const loadRetrying = useCallback(async () => {
    try {
      setRetrying(await api.sagas.retrying())
    } catch { /* informational — silent */ }
  }, [])

  useEffect(() => {
    loadRetrying()
    const id = setInterval(loadRetrying, 5000)
    return () => clearInterval(id)
  }, [loadRetrying])

  useEffect(() => {
    function onMove(e: MouseEvent) {
      if (!dragging.current) return
      const newW = Math.max(MIN_W, Math.min(MAX_W, startW.current + e.clientX - startX.current))
      curW.current = newW
      setWidth(newW)
    }
    function onUp() {
      if (!dragging.current) return
      dragging.current = false
      localStorage.setItem('sagaweaw.sidebar.width', String(curW.current))
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    return () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
  }, [])

  function onDragStart(e: React.MouseEvent) {
    dragging.current = true
    startX.current   = e.clientX
    startW.current   = width
    e.preventDefault()
  }

  function toggleCollapsed() {
    setCollapsed(c => {
      const next = !c
      localStorage.setItem('sagaweaw.sidebar.collapsed', String(next))
      return next
    })
  }

  function outboxColor() {
    if (outboxPending === null) return 'bg-gray-300'
    if (outboxPending === 0)   return 'bg-ok'
    if (outboxPending <= 10)   return 'bg-warn'
    return 'bg-fail'
  }

  function outboxLabel() {
    if (outboxPending === null || outboxPending === 0) return t('sidebar.outboxHealthy')
    return t('sidebar.outboxPending', { n: outboxPending })
  }

  function timeUntil(iso: string) {
    const diff = new Date(iso).getTime() - Date.now()
    if (diff <= 0) return t('sidebar.retrySoon')
    const s = Math.floor(diff / 1000)
    return t('sidebar.retryIn', { time: s < 60 ? `${s}s` : `${Math.floor(s / 60)}m` })
  }

  const totalRetryPages = Math.ceil(retrying.length / RETRY_PAGE_SIZE)
  const pagedRetrying   = retrying.slice(retryPage * RETRY_PAGE_SIZE, (retryPage + 1) * RETRY_PAGE_SIZE)

  if (collapsed) {
    return (
      <button
        onClick={toggleCollapsed}
        title={t('sidebar.expand')}
        className="flex-shrink-0 flex flex-col items-center justify-start pt-2
                   bg-sidebar rounded-xl border border-border w-9 cursor-pointer
                   hover:bg-accent/20 transition-colors h-full"
      >
        <span className="text-ink/50 hover:text-ink text-base leading-none">›</span>
      </button>
    )
  }

  return (
    <div className="flex-shrink-0 relative flex mb-4" style={{ width, height: 'calc(100% - 1rem)' }}>
      <div className="flex-1 bg-sidebar rounded-xl border border-border p-3
                      flex flex-col gap-3 overflow-hidden h-full mr-1.5">

        {/* ── Filters ─────────────────────────────── */}
        <div className="flex flex-col gap-2 flex-shrink-0">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-semibold text-ink/50 uppercase tracking-wider">
              {t('sidebar.filters')}
            </span>
            <button
              onClick={toggleCollapsed}
              title={t('sidebar.collapse')}
              className="p-1 rounded text-ink/40 hover:text-ink hover:bg-black/10 transition-colors text-base leading-none"
            >‹</button>
          </div>

          <Select
            value={status}
            onChange={v => onStatusChange(v as SagaStatusName | '')}
            placeholder={t('feed.allStatuses')}
            options={[
              { value: '', label: t('feed.allStatuses') },
              ...STATUSES.map(s => ({ value: s, label: t(`feed.statuses.${s}`) })),
            ]}
          />

          <input
            value={name}
            onChange={e => onNameChange(e.target.value)}
            placeholder={t('feed.filterPlaceholder')}
            className="bg-surface border border-border rounded-xl px-3 py-[7px] text-[13px] text-ink
                       placeholder:text-gray-400 focus:outline-none focus:border-accent focus:ring-2
                       focus:ring-accent/20 transition-all duration-200 hover:border-accent/60 shadow-sm w-full"
          />

          <span className="text-[12px] text-gray-500 flex items-center gap-1.5">
            {loading ? t('feed.loading') : (
              connected
                ? <><span className="w-[6px] h-[6px] rounded-full bg-ok inline-block" />{t('feed.counterLive', { count: sagaCount })}</>
                : t('feed.counter', { count: sagaCount })
            )}
          </span>
        </div>

        {/* ── Outbox Health ────────────────────────── */}
        <div className="flex flex-col gap-1.5 flex-shrink-0">
          <span className="text-[11px] font-semibold text-ink/50 uppercase tracking-wider">
            {t('sidebar.outboxHealth')}
          </span>
          <div className="flex items-center gap-2 text-[13px] text-ink">
            <span className={`w-2 h-2 rounded-full flex-shrink-0 ${outboxColor()}`} />
            <span>{outboxLabel()}</span>
          </div>
        </div>

        {/* ── Retry Queue ──────────────────────────── */}
        <div className="flex flex-col gap-1.5 flex-1 min-h-0">
          <span className="text-[11px] font-semibold text-ink/50 uppercase tracking-wider flex-shrink-0">
            {t('sidebar.retryQueue')}
            {retrying.length > 0 && (
              <span className="ml-1.5 inline-flex items-center justify-center
                               bg-warn/20 text-warn rounded-full px-1.5 py-0 text-[10px] font-bold normal-case tracking-normal">
                {retrying.length}
              </span>
            )}
          </span>

          {retrying.length === 0 ? (
            <EmptyState compact icon="retry" title={t('sidebar.retryQueueEmpty')} subtitle={t('sidebar.retryQueueEmptySubtitle')} />
          ) : (
            <>
              <div className="flex flex-col gap-1.5 overflow-y-auto flex-1 min-h-0">
                {pagedRetrying.map(r => (
                  <div
                    key={`${r.sagaId}-${r.stepName}`}
                    className="bg-surface rounded-lg border border-border px-2.5 py-2 text-[12px] flex-shrink-0"
                  >
                    <div className="font-semibold text-ink truncate">{r.stepName}</div>
                    <div className="text-gray-500 truncate">{r.sagaName}</div>
                    <div className="flex items-center justify-between mt-0.5">
                      <span className="text-gray-400">
                        {t('sidebar.attempt', { current: r.attempt, max: r.maxAttempts })}
                      </span>
                      <span className="text-warn font-medium">{timeUntil(r.nextRetryAt)}</span>
                    </div>
                  </div>
                ))}
              </div>
              {totalRetryPages > 1 && (
                <div className="flex items-center justify-between text-[11px] text-gray-500 flex-shrink-0 pt-0.5">
                  <button
                    disabled={retryPage === 0}
                    onClick={() => setRetryPage(p => p - 1)}
                    className="px-2 py-0.5 rounded hover:bg-muted disabled:opacity-30 transition-colors"
                  >←</button>
                  <span>{retryPage + 1}/{totalRetryPages}</span>
                  <button
                    disabled={retryPage + 1 >= totalRetryPages}
                    onClick={() => setRetryPage(p => p + 1)}
                    className="px-2 py-0.5 rounded hover:bg-muted disabled:opacity-30 transition-colors"
                  >→</button>
                </div>
              )}
            </>
          )}
        </div>

        {/* ── Success Rate bar ─────────────────────── */}
        {successRate !== null && completed !== null && compensated !== null && failed !== null && (
          <SuccessBar
            rate={successRate}
            completed={completed}
            compensated={compensated}
            failed={failed}
            label={t('metrics.rate')}
          />
        )}
      </div>

      {/* drag handle */}
      <div
        onMouseDown={onDragStart}
        className="absolute right-0 top-0 bottom-0 w-1.5 cursor-col-resize"
      />
    </div>
  )
}
