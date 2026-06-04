import { useState, useEffect, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import type { SagaNameStats, StepStats } from '../api/types'
import { fmtDuration } from '../utils/fmt'
import EmptyState from './EmptyState'

const MIN_W = 180
const MAX_W = 380
const DEF_W = 240

interface Props {
  byName: { name: string; total: number; completed: number; failed: number }[]
  activeName: string
  onNameFilter: (name: string) => void
}

export default function RightPanel({ byName, activeName, onNameFilter }: Props) {
  const { t } = useTranslation()

  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem('sagaweaw.rightbar.collapsed') === 'true'
  )
  const [width, setWidth] = useState(() => {
    const v = parseInt(localStorage.getItem('sagaweaw.rightbar.width') ?? '')
    return isNaN(v) ? DEF_W : Math.max(MIN_W, Math.min(MAX_W, v))
  })
  const [stats,      setStats]      = useState<SagaNameStats[]>([])
  const [stepStats,  setStepStats]  = useState<StepStats[]>([])
  const [stepTimesH, setStepTimesH] = useState(() => {
    const v = parseInt(localStorage.getItem('sagaweaw.rightbar.steph') ?? '')
    return isNaN(v) ? 180 : Math.max(0, Math.min(600, v))
  })

  const dragging     = useRef(false)
  const startX       = useRef(0)
  const startW       = useRef(0)
  const curW         = useRef(width)

  const divDragging  = useRef(false)
  const divStartY    = useRef(0)
  const divStartH    = useRef(0)
  const curStepH     = useRef(stepTimesH)

  const loadStats = useCallback(async () => {
    try {
      const [nameStats, steps] = await Promise.all([
        api.sagas.statsByName(),
        api.sagas.stepsStats(),
      ])
      setStats(nameStats)
      setStepStats(steps)
    } catch { /* informational */ }
  }, [])

  useEffect(() => {
    loadStats()
    const id = setInterval(loadStats, 10_000)
    return () => clearInterval(id)
  }, [loadStats])

  useEffect(() => {
    function onMove(e: MouseEvent) {
      if (dragging.current) {
        const newW = Math.max(MIN_W, Math.min(MAX_W, startW.current - (e.clientX - startX.current)))
        curW.current = newW
        setWidth(newW)
      }
      if (divDragging.current) {
        const newH = Math.max(0, Math.min(600, divStartH.current - (e.clientY - divStartY.current)))
        curStepH.current = newH
        setStepTimesH(newH)
      }
    }
    function onUp() {
      if (dragging.current) {
        dragging.current = false
        localStorage.setItem('sagaweaw.rightbar.width', String(curW.current))
      }
      if (divDragging.current) {
        divDragging.current = false
        localStorage.setItem('sagaweaw.rightbar.steph', String(curStepH.current))
      }
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
      localStorage.setItem('sagaweaw.rightbar.collapsed', String(next))
      return next
    })
  }

  // Build avg duration lookup from the dedicated endpoint
  const avgMap = new Map(stats.map(s => [s.name, s.avgDurationMs]))

  // Proportion of total saga time per step (within its own saga)
  const sagaTotals = new Map<string, number>()
  stepStats.forEach(s => {
    if (s.avgDurationMs != null)
      sagaTotals.set(s.sagaName, (sagaTotals.get(s.sagaName) ?? 0) + s.avgDurationMs)
  })
  const stepsWithPct = stepStats
    .filter(s => s.avgDurationMs != null)
    .map(s => {
      const sagaTotal = sagaTotals.get(s.sagaName) ?? 0
      const pct = sagaTotal > 0 ? (s.avgDurationMs! / sagaTotal) * 100 : 0
      return { ...s, pct }
    })
    .sort((a, b) => b.pct - a.pct)
  const maxPct = stepsWithPct.length > 0 ? stepsWithPct[0].pct : null

  function pctColor(pct: number) {
    if (pct > 40) return 'text-fail'
    if (pct >= 20) return 'text-warn'
    return 'text-ok'
  }

  const totals = byName.reduce(
    (acc, row) => ({ completed: acc.completed + row.completed, failed: acc.failed + row.failed }),
    { completed: 0, failed: 0 }
  )

  if (collapsed) {
    return (
      <button
        onClick={toggleCollapsed}
        title={t('sidebar.expand')}
        className="flex-shrink-0 flex flex-col items-center justify-start pt-2
                   bg-sidebar rounded-xl border border-border w-9 cursor-pointer
                   hover:bg-accent/20 transition-colors h-full"
      >
        <span className="text-ink/50 hover:text-ink text-base leading-none">‹</span>
      </button>
    )
  }

  return (
    <div className="flex-shrink-0 relative flex mb-4" style={{ width, height: 'calc(100% - 1rem)' }}>
      {/* drag handle — left edge */}
      <div
        onMouseDown={onDragStart}
        className="absolute left-0 top-0 bottom-0 w-1.5 cursor-col-resize z-10"
      />

      <div className="flex-1 bg-sidebar rounded-xl border border-border p-3
                      flex flex-col gap-3 overflow-hidden h-full ml-1.5">

        <div className="flex items-center justify-between flex-shrink-0">
          <span className="text-[11px] font-semibold text-ink/50 uppercase tracking-wider">
            {t('metrics.bySaga')}
          </span>
          <button
            onClick={toggleCollapsed}
            title={t('sidebar.collapse')}
            className="p-1 rounded text-ink/40 hover:text-ink hover:bg-black/10 transition-colors text-base leading-none"
          >›</button>
        </div>

        {/* saga type rows — scrollable */}
        <div className="scrollbar-subtle flex-1 min-h-0 overflow-y-auto flex flex-col gap-1">
          {byName.length === 0 ? (
            <EmptyState compact icon="stats" title={t('rightPanel.bySagaEmpty')} subtitle={t('rightPanel.bySagaEmptySubtitle')} />
          ) : (
            byName.map(row => {
              const rowRate = row.total > 0 ? (row.completed / row.total) * 100 : 0
              const rowFail = row.total > 0 ? (row.failed   / row.total) * 100 : 0
              const isActive  = activeName === row.name
              const avgMs     = avgMap.get(row.name)
              return (
                <button
                  key={row.name}
                  type="button"
                  onClick={() => onNameFilter(isActive ? '' : row.name)}
                  className={`text-left w-full rounded-lg px-2 py-2 transition-colors flex-shrink-0 ${
                    isActive
                      ? 'bg-accent/15 ring-1 ring-accent/40'
                      : 'hover:bg-accent/10'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1 gap-1">
                    <span className="text-[12px] text-ink font-mono truncate">{row.name}</span>
                    <span className="text-[11px] text-gray-500 flex-shrink-0 whitespace-nowrap">
                      {row.completed}/{row.total}
                      {' · '}
                      <span className={rowRate >= 80 ? 'text-ok' : rowRate >= 50 ? 'text-warn' : 'text-fail'}>
                        {rowRate.toFixed(0)}%
                      </span>
                    </span>
                  </div>
                  <div className="h-[5px] rounded-full overflow-hidden flex" style={{ background: 'rgb(var(--muted))' }}>
                    <div className="h-full bg-ok transition-all duration-700"  style={{ width: `${rowRate}%` }} />
                    <div className="h-full bg-fail transition-all duration-700" style={{ width: `${rowFail}%` }} />
                  </div>
                  {avgMs != null && (
                    <div className="mt-1 text-[10px] text-gray-400">
                      {t('rightPanel.avg')} {fmtDuration(avgMs)}
                    </div>
                  )}
                </button>
              )
            })
          )}
        </div>

        {/* totals */}
        <div className="flex-shrink-0 pt-2">
          <div className="flex gap-3 text-[11px] text-gray-500 flex-wrap">
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-ok inline-block" />
              {totals.completed} {t('metrics.completed')}
            </span>
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-fail inline-block" />
              {totals.failed} {t('metrics.failed')}
            </span>
          </div>
        </div>

        {/* step times */}
        <div className="flex-shrink-0 relative border-t border-border pt-2 flex flex-col gap-2">
          {/* drag handle — sits on top of the border-t giving the impression it's the divider */}
          <div
            onMouseDown={e => {
              divDragging.current = true
              divStartY.current   = e.clientY
              divStartH.current   = stepTimesH
              e.preventDefault()
            }}
            className="absolute inset-x-0 -top-1.5 h-3 cursor-row-resize z-10"
          />
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-semibold text-ink/70 uppercase tracking-wider">
              {t('rightPanel.stepTimes')}
            </span>
            {maxPct != null && (
              <span className={`text-[11px] font-bold ${pctColor(maxPct)}`}>
                {maxPct.toFixed(0)}%
              </span>
            )}
          </div>
          {stepsWithPct.length === 0 ? (
            <EmptyState compact icon="stats" title={t('rightPanel.noStepStats')} subtitle={t('rightPanel.noStepStatsSubtitle')} />
          ) : (
            <div className="scrollbar-subtle overflow-y-auto flex flex-col gap-2 pr-0.5" style={{ height: stepTimesH }}>
              {stepsWithPct.map(s => (
                <div key={`${s.sagaName}:${s.stepName}`}
                  className="flex items-center justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <span className="text-[13px] font-mono text-ink font-medium truncate block leading-tight">{s.stepName}</span>
                    <span className="text-[11px] text-gray-500 truncate block leading-tight">{s.sagaName}</span>
                  </div>
                  <div className="flex-shrink-0 text-right leading-tight">
                    <span className={`text-[13px] font-bold block ${pctColor(s.pct)}`}>
                      {fmtDuration(s.avgDurationMs!)}
                    </span>
                    <span className={`text-[11px] font-semibold block ${pctColor(s.pct)}`}>
                      {s.pct.toFixed(0)}%
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* integrations note */}
        <div className="flex-shrink-0 border-t border-border pt-2">
          <p className="text-[10px] text-gray-400 leading-snug">{t('rightPanel.integrationsNote')}</p>
        </div>
      </div>
    </div>
  )
}
