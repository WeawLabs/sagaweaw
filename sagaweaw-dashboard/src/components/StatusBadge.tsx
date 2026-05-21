import { useTranslation } from 'react-i18next'
import type { SagaStatusName, StepStatusName } from '../api/types'

type AnyStatus = SagaStatusName | StepStatusName

const cfg: Record<string, string> = {
  COMPLETED:    'bg-ink text-canvas',
  FAILED:       'bg-accent text-ink',
  COMPENSATED:  'bg-muted text-accent border border-accent',
  COMPENSATING: 'bg-muted text-ink border border-ink animate-status-pulse',
  EXECUTING:    'bg-muted text-ink border border-ink animate-status-pulse',
  STARTED:      'bg-muted text-gray-500 border border-border',
  PENDING:      'bg-muted text-gray-400 border border-border',
  SKIPPED:      'bg-muted text-gray-400 border border-border',
}

export default function StatusBadge({ status }: { status: AnyStatus }) {
  const { t } = useTranslation()
  const cls  = cfg[status] ?? 'bg-muted text-gray-400 border border-border'
  const text = (t as (k: string) => string)(`status.${status}`) || status
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-3 py-1 text-[13px] font-semibold ${cls}`}>
      {text}
    </span>
  )
}
