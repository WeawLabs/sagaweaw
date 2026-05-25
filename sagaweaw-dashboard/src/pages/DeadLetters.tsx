import { useState, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import type { DeadLetter } from '../api/types'
import CopyButton from '../components/CopyButton'

export default function DeadLetters() {
  const { t, i18n } = useTranslation()
  const locale = i18n.language.startsWith('pt') ? 'pt-BR' : 'en-US'

  const [items,           setItems]           = useState<DeadLetter[]>([])
  const [loading,         setLoading]         = useState(true)
  const [error,           setError]           = useState<string | null>(null)
  const [reprocessing,    setReprocessing]    = useState<Set<string>>(new Set())
  const [selected,        setSelected]        = useState<Set<string>>(new Set())
  const [batchProcessing, setBatchProcessing] = useState(false)
  const [expandedTrace,   setExpandedTrace]   = useState<Set<string>>(new Set())
  const [expandedCtx,     setExpandedCtx]     = useState<Set<string>>(new Set())

  const load = useCallback(async () => {
    try {
      setItems(await api.deadLetters.list({ includeReprocessed: false }))
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : t('dl.loadError'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => { load() }, [load])

  function toggleSelected(id: string) {
    setSelected(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n })
  }

  function toggleAll() {
    setSelected(s => s.size === items.length ? new Set() : new Set(items.map(i => i.id)))
  }

  async function reprocessSelected() {
    if (selected.size === 0) return
    setBatchProcessing(true)
    try {
      await api.deadLetters.reprocessBatch([...selected])
      setSelected(new Set())
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : t('dl.reprocessError'))
    } finally {
      setBatchProcessing(false)
    }
  }

  async function reprocess(id: string) {
    setReprocessing(s => new Set(s).add(id))
    try {
      await api.deadLetters.reprocess(id)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : t('dl.reprocessError'))
    } finally {
      setReprocessing(s => { const n = new Set(s); n.delete(id); return n })
    }
  }

  function toggle(setFn: (fn: (s: Set<string>) => Set<string>) => void, id: string) {
    setFn(s => {
      const n = new Set(s)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })
  }

  return (
    <div className="h-full overflow-y-auto pb-4">
      {/* title bar */}
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <h2 className="text-[16px] font-semibold text-ink">{t('dl.title')}</h2>
          {items.length > 0 && (
            <span className="px-2 py-0.5 rounded-full text-[11px] font-medium bg-fail/10 text-fail border border-fail/20">
              {items.length}
            </span>
          )}
        </div>
        {items.length > 0 && (
          <div className="flex items-center gap-2">
            <button
              onClick={toggleAll}
              className="text-[12px] text-gray-500 hover:text-ink transition-colors"
            >
              {selected.size === items.length ? t('dl.deselectAll') : t('dl.selectAll')}
            </button>
            {selected.size > 0 && (
              <button
                onClick={reprocessSelected}
                disabled={batchProcessing}
                className="px-3 py-1.5 rounded-lg text-[12px] font-semibold
                           bg-ink text-canvas hover:bg-accent hover:text-ink
                           transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {batchProcessing
                  ? t('dl.reprocessing')
                  : t('dl.reprocessSelected', { count: selected.size })}
              </button>
            )}
          </div>
        )}
      </div>

      {error && <div className="error-card mb-4 text-[13px]">{error}</div>}

      {!loading && items.length === 0 && (
        <div className="bg-surface rounded-[10px] border border-border px-4 py-8
                        text-center text-[13px] text-gray-400">
          {t('dl.empty')}
        </div>
      )}

      <div className="flex flex-col gap-2">
        {items.map(dl => {
          const isReprocessing = reprocessing.has(dl.id)
          const traceOpen      = expandedTrace.has(dl.id)
          const ctxOpen        = expandedCtx.has(dl.id)

          return (
            <div key={dl.id} className="bg-surface rounded-xl border border-border overflow-hidden">
              <div className="flex items-start gap-3 px-4 py-3 pb-2">

                <input
                  type="checkbox"
                  checked={selected.has(dl.id)}
                  onChange={() => toggleSelected(dl.id)}
                  className="mt-[5px] w-[14px] h-[14px] flex-shrink-0 accent-ink cursor-pointer"
                />
                <div className="mt-[5px] w-[7px] h-[7px] rounded-full flex-shrink-0 bg-fail" />

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-[14px] font-semibold text-ink">{dl.stepName}</span>
                    {dl.sagaName && (
                      <span className="text-[12px] text-gray-600 font-medium">{dl.sagaName}</span>
                    )}
                    <span className="text-[12px] font-mono text-gray-400">#{dl.sagaId.slice(0, 8)}</span>
                  </div>

                  <p className="mt-0.5 text-[13px] text-gray-700 line-clamp-2">{dl.errorMessage}</p>

                  <div className="mt-1 text-[12px] text-gray-500">
                    {new Date(dl.createdAt).toLocaleString(locale, {
                      day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
                    })}
                  </div>
                </div>

                {/* actions */}
                <div className="flex items-center gap-1.5 flex-shrink-0 pt-0.5">
                  {dl.errorTrace && (
                    <button
                      onClick={() => toggle(setExpandedTrace, dl.id)}
                      className={`px-2.5 py-1.5 rounded-lg text-[12px] font-medium border transition-colors ${
                        traceOpen
                          ? 'bg-muted border-border text-ink'
                          : 'border-border text-gray-600 hover:text-ink hover:bg-muted'
                      }`}
                    >
                      {traceOpen ? t('dl.collapse') : t('dl.expand')}
                    </button>
                  )}
                  {dl.contextSnapshot && (
                    <button
                      onClick={() => toggle(setExpandedCtx, dl.id)}
                      className={`px-2.5 py-1.5 rounded-lg text-[12px] font-medium border transition-colors ${
                        ctxOpen
                          ? 'bg-muted border-border text-ink'
                          : 'border-border text-gray-600 hover:text-ink hover:bg-muted'
                      }`}
                    >
                      {ctxOpen ? t('dl.collapseCtx') : t('dl.expandCtx')}
                    </button>
                  )}
                  <button
                    onClick={() => reprocess(dl.id)}
                    disabled={isReprocessing}
                    className="px-3 py-1.5 rounded-lg text-[12px] font-semibold
                               bg-ink text-canvas hover:bg-accent hover:text-ink
                               transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {isReprocessing ? t('dl.reprocessing') : t('dl.reprocess')}
                  </button>
                </div>
              </div>

              {ctxOpen && dl.contextSnapshot && (
                <div className="border-t border-border bg-muted px-4 py-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-[11px] text-gray-500 uppercase tracking-wide font-medium">
                      {t('dl.context')}
                    </span>
                    <CopyButton text={dl.contextSnapshot} />
                  </div>
                  <pre className="text-[11px] text-ink font-mono whitespace-pre-wrap overflow-x-auto leading-relaxed">
                    {dl.contextSnapshot}
                  </pre>
                </div>
              )}

              {traceOpen && dl.errorTrace && (
                <div className="border-t border-border bg-muted px-4 py-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-[11px] text-gray-500 uppercase tracking-wide font-medium">
                      {t('dl.trace')}
                    </span>
                    <CopyButton text={dl.errorTrace} />
                  </div>
                  <pre className="text-[11px] text-ink font-mono whitespace-pre-wrap overflow-x-auto leading-relaxed">
                    {dl.errorTrace}
                  </pre>
                </div>
              )}

              <div className="h-[4px] w-full bg-fail" />
            </div>
          )
        })}
      </div>
    </div>
  )
}
