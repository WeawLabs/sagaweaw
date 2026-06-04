import { useTranslation } from 'react-i18next'
import type { SagaMetrics } from '../api/types'
import { fmtCount } from '../utils/fmt'

interface Props { metrics: SagaMetrics | null }

function TotalIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" />
      <rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" />
    </svg>
  )
}

function CompletedIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  )
}

function FailedIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <line x1="15" y1="9" x2="9" y2="15" />
      <line x1="9"  y1="9" x2="15" y2="15" />
    </svg>
  )
}

function RunningIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="5 3 19 12 5 21 5 3" />
    </svg>
  )
}

function CompensatedIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="1 4 1 10 7 10" />
      <path d="M3.51 15a9 9 0 1 0 .49-4.95" />
    </svg>
  )
}

function DeadLetterIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <line x1="12" y1="9"  x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  )
}

export default function MetricsBar({ metrics }: Props) {
  const { t } = useTranslation()

  if (!metrics) return (
    <div className="grid grid-cols-3 sm:grid-cols-6 gap-2 mb-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="bg-surface rounded-xl border border-border px-3 py-2.5 h-[56px] animate-pulse" />
      ))}
    </div>
  )

  const items = [
    { label: t('metrics.today'),       value: metrics.total,                       Icon: TotalIcon,       color: 'text-ink'  },
    { label: t('metrics.running'),     value: metrics.executing + metrics.started, Icon: RunningIcon,     color: 'text-ink'  },
    { label: t('metrics.compensated'), value: metrics.compensated,                 Icon: CompensatedIcon, color: 'text-warn' },
    { label: t('metrics.failed'),      value: metrics.failed,                      Icon: FailedIcon,      color: metrics.failed > 0      ? 'text-fail' : 'text-ink' },
    { label: t('metrics.deadLetters'), value: metrics.deadLetters,                 Icon: DeadLetterIcon,  color: metrics.deadLetters > 0 ? 'text-fail' : 'text-ink' },
    { label: t('metrics.completed'),   value: metrics.completed,                   Icon: CompletedIcon,   color: 'text-ok'   },
  ]

  return (
    <div className="grid grid-cols-3 sm:grid-cols-6 gap-2 mb-4">
      {items.map(({ label, value, Icon, color }) => (
        <div key={label} className="bg-surface rounded-xl border border-border px-3 py-3 shadow-sm
                                    flex flex-col items-center justify-center text-center gap-1.5">
          <div className={`flex items-center gap-2 ${color}`}>
            <Icon />
            <span className="text-[22px] font-bold leading-none">{fmtCount(value)}</span>
          </div>
          <div className="text-[11px] text-gray-500 font-semibold leading-none uppercase tracking-wide">{label}</div>
        </div>
      ))}
    </div>
  )
}
