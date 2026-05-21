import { useTranslation } from 'react-i18next'
import type { SagaMetrics } from '../api/types'
import { fmtCount } from '../utils/fmt'

interface Props { metrics: SagaMetrics | null }

export default function MetricsBar({ metrics }: Props) {
  const { t } = useTranslation()

  if (!metrics) return (
    <div className="grid grid-cols-3 sm:grid-cols-6 gap-2 mb-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="bg-surface rounded-xl border border-border px-3 py-2.5 h-[44px] animate-pulse" />
      ))}
    </div>
  )

  const items = [
    { label: t('metrics.today'),       value: metrics.total,                       icon: '●', color: 'text-ink'  },
    { label: t('metrics.completed'),   value: metrics.completed,                   icon: '✓', color: 'text-ink'  },
    { label: t('metrics.failed'),      value: metrics.failed,                      icon: '✕', color: metrics.failed > 0      ? 'text-fail' : 'text-ink' },
    { label: t('metrics.running'),     value: metrics.executing + metrics.started, icon: '⚡', color: 'text-ink' },
    { label: t('metrics.compensated'), value: metrics.compensated,                 icon: '↺', color: 'text-ink'  },
    { label: t('metrics.deadLetters'), value: metrics.deadLetters,                 icon: '⚠', color: metrics.deadLetters > 0 ? 'text-fail' : 'text-ink' },
  ]

  return (
    <div className="grid grid-cols-3 sm:grid-cols-6 gap-2 mb-4">
      {items.map(({ label, value, icon, color }) => (
        <div key={label} className="bg-surface rounded-xl border border-border px-3 py-2.5 shadow-sm">
          <div className={`flex items-baseline gap-1.5 ${color}`}>
            <span className="text-[16px] leading-none">{icon}</span>
            <span className="text-[22px] font-bold leading-none">{fmtCount(value)}</span>
          </div>
          <div className="text-[11px] text-gray-500 font-semibold mt-1.5 leading-none uppercase tracking-wide">{label}</div>
        </div>
      ))}
    </div>
  )
}
