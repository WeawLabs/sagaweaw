import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import type { SagaInstance, SagaMetrics, SagaStatusName } from '../api/types'
import StatusBadge from '../components/StatusBadge'
import MetricsBar from '../components/MetricsBar'
import SidePanel from '../components/SidePanel'
import RightPanel from '../components/RightPanel'
import { useStomp } from '../hooks/useStomp'
import { fmtDuration } from '../utils/fmt'
import EmptyState from '../components/EmptyState'

const PAGE_SIZE = 20

const barColor: Record<SagaStatusName, string> = {
  COMPLETED:    'bg-ok',
  FAILED:       'bg-fail',
  COMPENSATED:  'bg-warn',
  COMPENSATING: 'bg-warn animate-pulse',
  EXECUTING:    'bg-accent animate-pulse',
  STARTED:      'bg-gray-300',
}

export default function SagaFeed() {
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()

  const [sagas,      setSagas]      = useState<SagaInstance[]>([])
  const [stuckSagas, setStuckSagas] = useState<SagaInstance[]>([])
  const [metrics,    setMetrics]    = useState<SagaMetrics | null>(null)
  const [status,     setStatus]     = useState<SagaStatusName | ''>('')
  const [name,       setName]       = useState('')
  const [page,       setPage]       = useState(0)
  const [loading,    setLoading]    = useState(true)
  const [error,      setError]      = useState<string | null>(null)

  const locale = i18n.language.startsWith('pt') ? 'pt-BR' : 'en-US'

  function fmtAge(iso: string) {
    const diff = Date.now() - new Date(iso).getTime()
    if (diff < 60_000)    return t('feed.agoSeconds', { n: Math.floor(diff / 1000) })
    if (diff < 3_600_000) return t('feed.agoMinutes', { n: Math.floor(diff / 60_000) })
    return new Date(iso).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
  }

  const loadStuck = useCallback(async () => {
    try {
      setStuckSagas(await api.sagas.stuck())
    } catch { /* informational — silent */ }
  }, [])

  const load = useCallback(async () => {
    try {
      const isIdSearch      = name.startsWith('#')
      const isContextSearch = name.startsWith('@')
      const [s, m] = await Promise.all([
        api.sagas.list({
          status: status || undefined,
          name:   isIdSearch || isContextSearch ? undefined : name || undefined,
          id:     isIdSearch      ? name.slice(1) || undefined : undefined,
          contextSearch: isContextSearch ? name.slice(1) || undefined : undefined,
        }),
        api.sagas.metrics(),
      ])
      setSagas(s)
      setMetrics(m)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : t('feed.loadError'))
    } finally {
      setLoading(false)
    }
  }, [status, name, t])

  const { connected } = useStomp('/topic/sagas', load)

  useEffect(() => {
    loadStuck()
    const id = setInterval(loadStuck, 10000)
    return () => clearInterval(id)
  }, [loadStuck])

  // Reset to live page when filters change
  useEffect(() => { setPage(0) }, [status, name])

  useEffect(() => {
    load()
    if (page > 0) return
    const id = setInterval(load, 3000)
    return () => clearInterval(id)
  }, [load, page])

  const pagedSagas = sagas.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const hasMore    = sagas.length > (page + 1) * PAGE_SIZE
  const isFiltered = status !== '' || name !== ''

  return (
    <div className="h-full flex flex-col">
      <MetricsBar metrics={metrics} />

      <div className="flex-1 min-h-0 flex gap-3">
        <SidePanel
          status={status}
          onStatusChange={setStatus}
          name={name}
          onNameChange={setName}
          sagaCount={sagas.length}
          loading={loading}
          connected={connected}
          outboxPending={metrics?.outboxPending ?? null}
          successRate={metrics?.successRate ?? null}
          completed={metrics?.completed ?? null}
          compensated={metrics?.compensated ?? null}
          failed={metrics?.failed ?? null}
        />

        <div className="flex-1 min-w-0 flex flex-col overflow-hidden">
          {error && (
            <div className="error-card text-[13px] flex-shrink-0 mb-1">{error}</div>
          )}

          <div className="flex-1 overflow-y-auto flex flex-col gap-1.5 pt-0.5 pb-4">

            {stuckSagas.length > 0 && (
              <div className="flex flex-col gap-1 rounded-xl border border-fail/40 bg-fail/5 px-3 py-2 flex-shrink-0">
                <span className="text-[11px] font-semibold text-fail/80 uppercase tracking-wider flex items-center gap-1.5">
                  <span className="w-[6px] h-[6px] rounded-full bg-fail animate-pulse inline-block" />
                  {t('feed.stuck', { count: stuckSagas.length })}
                </span>
                {stuckSagas.map(saga => (
                  <button
                    key={saga.id}
                    onClick={() => navigate(`/sagas/${saga.id}`)}
                    className="bg-surface rounded-lg border border-fail/40 text-left overflow-hidden
                               hover:border-fail hover:shadow-sm transition-all duration-200 w-full"
                  >
                    <div className="px-3 py-2">
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2 min-w-0">
                          <span className="text-[13px] font-semibold text-ink truncate">{saga.name}</span>
                          <span className="text-[11px] text-gray-600 font-mono">#{saga.id.slice(0, 8)}</span>
                        </div>
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-bold
                                         bg-fail text-white uppercase tracking-wider flex-shrink-0">
                          {t('feed.stuckBadge')}
                        </span>
                      </div>
                      <div className="mt-0.5 text-[12px] text-gray-500">
                        {t('feed.stuckSince', { time: fmtAge(saga.updatedAt) })}
                      </div>
                    </div>
                    <div className="h-[3px] w-full bg-fail" />
                  </button>
                ))}
              </div>
            )}

            {!loading && pagedSagas.length === 0 && (
              <div className="bg-surface rounded-[10px] border border-border flex-shrink-0">
                <EmptyState
                  icon="sagas"
                  title={isFiltered ? t('feed.emptyFiltered') : t('feed.empty')}
                  subtitle={isFiltered ? t('feed.emptyFilteredSubtitle') : t('feed.emptySubtitle')}
                />
              </div>
            )}

            {pagedSagas.map(saga => {
              const dur = saga.completedAt
                ? new Date(saga.completedAt).getTime() - new Date(saga.createdAt).getTime()
                : null
              const stepsOk = saga.steps.filter(s => s.status === 'COMPLETED').length
              return (
                <button
                  key={saga.id}
                  onClick={() => navigate(`/sagas/${saga.id}`)}
                  className="bg-surface rounded-lg border border-border text-left overflow-hidden
                             hover:border-accent hover:shadow-sm transition-all duration-200 w-full flex-shrink-0"
                >
                  <div className="px-3 py-2">
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="text-[13px] font-semibold text-ink truncate">{saga.name}</span>
                        <span className="text-[11px] text-gray-600 font-mono">#{saga.id.slice(0, 8)}</span>
                      </div>
                      <StatusBadge status={saga.status} />
                    </div>
                    <div className="mt-0.5 text-[12px] text-gray-700 flex items-center gap-1">
                      <span className="font-semibold text-ink">
                        {dur !== null ? fmtDuration(dur) : t('feed.inProgress')}
                      </span>
                      <span className="text-gray-400">·</span>
                      <span>{t('feed.steps', { done: stepsOk, total: saga.steps.length })}</span>
                      <span className="text-gray-400">·</span>
                      <span>{fmtAge(saga.createdAt)}</span>
                    </div>
                  </div>
                  <div className={`h-[4px] w-full ${barColor[saga.status]}`} />
                </button>
              )
            })}
          </div>

          <div className="flex items-center justify-end py-1 border-t border-border flex-shrink-0">
            <button
              onClick={() => api.sagas.exportCsv({ status: status || undefined })}
              disabled={sagas.length === 0}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[12px]
                         text-gray-400 hover:text-ink hover:bg-muted border border-transparent
                         hover:border-border transition-all duration-150 disabled:opacity-30"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              {t('feed.exportCsv')}
            </button>
          </div>

          {(page > 0 || sagas.length > PAGE_SIZE) && (
            <div className="flex items-center justify-center gap-4 py-2 border-t border-border
                            text-[12px] text-gray-500 flex-shrink-0">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="px-2.5 py-1 rounded-lg hover:bg-muted disabled:opacity-30 transition-colors text-ink"
              >←</button>
              <span className="flex items-center gap-1.5">
                {page === 0 ? (
                  <><span className="w-[6px] h-[6px] rounded-full bg-ok inline-block" />{t('header.live')}</>
                ) : (
                  t('feed.page', { n: page + 1 })
                )}
              </span>
              <button
                disabled={!hasMore}
                onClick={() => setPage(p => p + 1)}
                className="px-2.5 py-1 rounded-lg hover:bg-muted disabled:opacity-30 transition-colors text-ink"
              >→</button>
            </div>
          )}
        </div>

        <RightPanel
          byName={metrics?.byName ?? []}
          activeName={name}
          onNameFilter={setName}
        />
      </div>
    </div>
  )
}
