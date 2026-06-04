import type { ReactElement } from 'react'

type IconKey = 'sagas' | 'dead-letters' | 'flow' | 'related' | 'retry' | 'stats'

interface Props {
  icon: IconKey
  title: string
  subtitle?: string
  compact?: boolean
}

const ICONS: Record<IconKey, ReactElement> = {
  sagas: (
    <svg className="w-full h-full" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
    </svg>
  ),
  'dead-letters': (
    <svg className="w-full h-full" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
      <path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
    </svg>
  ),
  flow: (
    <svg className="w-full h-full" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="18" cy="18" r="3" />
      <circle cx="6" cy="6" r="3" />
      <path d="M13 6h3a2 2 0 0 1 2 2v7" />
      <line x1="6" y1="9" x2="6" y2="21" />
    </svg>
  ),
  related: (
    <svg className="w-full h-full" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
      <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
    </svg>
  ),
  retry: (
    <svg className="w-full h-full" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  ),
  stats: (
    <svg className="w-full h-full" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <line x1="18" y1="20" x2="18" y2="10" />
      <line x1="12" y1="20" x2="12" y2="4" />
      <line x1="6" y1="20" x2="6" y2="14" />
    </svg>
  ),
}

export default function EmptyState({ icon, title, subtitle, compact = false }: Props) {
  if (compact) {
    return (
      <div className="flex flex-col gap-0.5 py-0.5">
        <div className="flex items-center gap-1.5 text-accent/60">
          <span className="w-[14px] h-[14px] flex-shrink-0 block">{ICONS[icon]}</span>
          <span className="text-[12px] text-gray-400">{title}</span>
        </div>
        {subtitle && (
          <span className="text-[11px] text-gray-400 leading-snug pl-[22px]">{subtitle}</span>
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center gap-3 py-10 px-4 text-center">
      <div className="w-10 h-10 rounded-xl bg-muted flex items-center justify-center text-accent">
        <span className="w-[22px] h-[22px] block">{ICONS[icon]}</span>
      </div>
      <div className="flex flex-col gap-1">
        <span className="text-[14px] font-semibold text-ink">{title}</span>
        {subtitle && (
          <span className="text-[12px] text-gray-500 max-w-[240px] leading-snug">{subtitle}</span>
        )}
      </div>
    </div>
  )
}
