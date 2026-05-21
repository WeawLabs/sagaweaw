import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import type { SagaMetrics } from '../api/types'

export default function SagaMetrics() {
  const { t } = useTranslation()
  const [metrics, setMetrics] = useState<SagaMetrics | null>(null)
  const [error, setError]     = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    async function load() {
      try {
        const m = await api.sagas.metrics()
        if (alive) { setMetrics(m); setError(null) }
      } catch {
        if (alive) setError(t('feed.loadError'))
      }
    }
    load()
    const id = setInterval(load, 5000)
    return () => { alive = false; clearInterval(id) }
  }, [t])

  if (error) return <div className="error-card text-[13px]">{error}</div>
  if (!metrics) return (
    <div className="flex items-center justify-center py-20 text-[13px] text-gray-600">
      {t('feed.loading')}
    </div>
  )

  const rate = metrics.successRate ?? 0
  const rateColor = rate >= 90 ? 'text-ok' : rate >= 60 ? 'text-warn' : 'text-fail'
  const rateBar   = rate >= 90 ? 'bg-ok'   : rate >= 60 ? 'bg-warn'   : 'bg-fail'

  const cards = [
    { label: t('metrics.today'),       value: metrics.total,       sub: null },
    { label: t('metrics.running'),     value: metrics.executing,   sub: null },
    { label: t('metrics.completed'),   value: metrics.completed,   sub: null },
    { label: t('metrics.failed'),      value: metrics.failed,      sub: null },
    { label: t('metrics.compensated'), value: metrics.compensated, sub: null },
    { label: t('metrics.deadLetters'), value: metrics.deadLetters, sub: null },
  ]

  return (
    <div className="space-y-6">

      {/* summary cards */}
      <div className="grid grid-cols-3 sm:grid-cols-6 gap-3">
        {cards.map(c => (
          <div key={c.label} className="bg-surface border border-border rounded-xl px-4 py-4 text-center shadow-sm">
            <div className="text-2xl font-semibold text-ink">{c.value}</div>
            <div className="text-[11px] text-gray-600 uppercase tracking-wide mt-1">{c.label}</div>
          </div>
        ))}
      </div>

      {/* success rate */}
      <div className="bg-surface border border-border rounded-xl p-5 shadow-sm">
        <div className="flex items-baseline justify-between mb-3">
          <span className="text-[13px] font-medium text-ink">{t('metrics.rate')}</span>
          <span className={`text-2xl font-semibold ${rateColor}`}>{rate.toFixed(1)}%</span>
        </div>
        <div className="h-2 bg-muted rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-700 ${rateBar}`}
            style={{ width: `${rate}%` }}
          />
        </div>
        <div className="flex justify-between mt-1.5 text-[11px] text-gray-600">
          <span>0%</span>
          <span>100%</span>
        </div>
      </div>

      {/* breakdown by saga name */}
      {metrics.byName.length > 0 && (
        <div className="bg-surface border border-border rounded-xl p-5 shadow-sm">
          <h2 className="text-[13px] font-medium text-ink mb-4">{t('metrics.bySaga')}</h2>
          <div className="space-y-4">
            {metrics.byName.map(row => {
              const rowRate = row.total > 0 ? (row.completed / row.total) * 100 : 0
              const rowFail = row.total > 0 ? (row.failed   / row.total) * 100 : 0
              return (
                <div key={row.name}>
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-[13px] text-ink font-mono">{row.name}</span>
                    <span className="text-[12px] text-gray-600">
                      {row.completed}/{row.total} &nbsp;·&nbsp;
                      <span className={rowRate >= 80 ? 'text-ok' : rowRate >= 50 ? 'text-warn' : 'text-fail'}>
                        {rowRate.toFixed(0)}%
                      </span>
                    </span>
                  </div>
                  <div className="h-2 bg-muted rounded-full overflow-hidden flex">
                    <div
                      className="h-full bg-ok transition-all duration-700"
                      style={{ width: `${rowRate}%` }}
                    />
                    <div
                      className="h-full bg-fail transition-all duration-700"
                      style={{ width: `${rowFail}%` }}
                    />
                  </div>
                </div>
              )
            })}
          </div>
          <div className="flex gap-4 mt-4 text-[11px] text-gray-600">
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-ok inline-block" />{t('metrics.completed')}</span>
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-fail inline-block" />{t('metrics.failed')}</span>
          </div>
        </div>
      )}

    </div>
  )
}
