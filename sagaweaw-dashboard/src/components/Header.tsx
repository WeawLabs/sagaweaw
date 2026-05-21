import { useState, useEffect, useRef } from 'react'
import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import logo from '../assets/logo.svg'
import { useTheme } from '../hooks/useTheme'
import { getToken } from '../auth'
import CopyButton from './CopyButton'

const LANGS = [
  { code: 'pt-BR', label: 'PT' },
  { code: 'en',    label: 'EN' },
] as const

const DOCS_URL        = 'https://doc.sagaweaw.dev'
const DISCUSSIONS_URL = 'https://github.com/amosjuda/sagaweaw/discussions'

function MoonIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  )
}

function SunIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="5" />
      <line x1="12" y1="1"  x2="12" y2="3"  />
      <line x1="12" y1="21" x2="12" y2="23" />
      <line x1="4.22" y1="4.22"  x2="5.64" y2="5.64"  />
      <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
      <line x1="1"  y1="12" x2="3"  y2="12" />
      <line x1="21" y1="12" x2="23" y2="12" />
      <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
      <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
    </svg>
  )
}

function GearIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  )
}

function BookIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
      <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
    </svg>
  )
}

function maskToken(token: string | null): string {
  if (!token) return '—'
  if (token.length <= 6) return '••••••••'
  return token.slice(0, 4) + '••••••••'
}

export default function Header() {
  const { t, i18n } = useTranslation()
  const { theme, toggle } = useTheme()

  const [showGear, setShowGear] = useState(false)
  const gearRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!showGear) return
    function handler(e: MouseEvent) {
      if (gearRef.current && !gearRef.current.contains(e.target as Node)) {
        setShowGear(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [showGear])

  const token = getToken()

  return (
    <header className="bg-surface border-b border-border sticky top-0 z-50">
      <div className="max-w-8xl mx-auto px-4 sm:px-6 h-14 flex items-center gap-4">

        <NavLink to="/" className="flex items-center gap-2 text-ink font-medium text-[15px]">
          <img src={logo} alt="sagaweaw" className="h-7 w-auto" />
          <span>sagaweaw</span>
        </NavLink>

        <nav className="flex gap-1 ml-1">
          {([
            { to: '/',             label: t('header.feed'),        exact: true  },
            { to: '/dead-letters', label: t('header.deadLetters'), exact: false },
          ] as const).map(({ to, label, exact }) => (
            <NavLink
              key={to}
              to={to}
              end={exact}
              className={({ isActive }) =>
                `px-4 py-[6px] rounded-md text-[14px] font-semibold transition-colors ${
                  isActive
                    ? 'bg-ink text-canvas'
                    : 'text-gray-500 hover:text-ink hover:bg-muted'
                }`
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-3">
          {/* live indicator */}
          <div className="flex items-center gap-1.5 text-[11px] text-gray-400">
            <span className="w-[7px] h-[7px] rounded-full bg-ok inline-block" />
            {t('header.live')}
          </div>

          {/* docs link */}
          <a
            href={DOCS_URL}
            target="_blank"
            rel="noopener noreferrer"
            title={t('header.docs')}
            className="w-8 h-8 rounded-lg flex items-center justify-center
                       text-gray-500 hover:text-ink hover:bg-muted
                       border border-border transition-all duration-200"
          >
            <BookIcon />
          </a>

          {/* gear / settings dropdown */}
          <div ref={gearRef} className="relative">
            <button
              onClick={() => setShowGear(v => !v)}
              title={t('header.settings')}
              className={`w-8 h-8 rounded-lg flex items-center justify-center
                         border border-border transition-all duration-200 ${
                showGear
                  ? 'bg-accent/20 text-ink border-accent/50'
                  : 'text-gray-500 hover:text-ink hover:bg-muted'
              }`}
            >
              <GearIcon />
            </button>

            {showGear && (
              <div className="absolute right-0 top-[calc(100%+6px)] w-64
                              bg-surface border border-border rounded-xl shadow-lg
                              p-3 flex flex-col gap-3 z-50">

                <span className="text-[11px] font-semibold text-ink/50 uppercase tracking-wider">
                  {t('header.settings')}
                </span>

                {/* token */}
                <div className="flex flex-col gap-0.5">
                  <span className="text-[10px] text-gray-400 uppercase tracking-wide font-medium">Token</span>
                  <code className="text-[12px] font-mono text-ink bg-muted rounded px-2 py-1 border border-border">
                    {maskToken(token)}
                  </code>
                </div>

                {/* API endpoint */}
                <div className="flex flex-col gap-0.5">
                  <span className="text-[10px] text-gray-400 uppercase tracking-wide font-medium">API endpoint</span>
                  <code className="text-[12px] font-mono text-ink bg-muted rounded px-2 py-1 border border-border">
                    /api/sagas
                  </code>
                </div>

                {/* MDC fields */}
                <div className="flex flex-col gap-1">
                  <span className="text-[10px] text-gray-400 uppercase tracking-wide font-medium">MDC fields</span>
                  <div className="flex flex-wrap gap-1">
                    {['sagaId', 'sagaName', 'stepName', 'attempt'].map(f => (
                      <span
                        key={f}
                        className="px-1.5 py-0.5 rounded bg-muted border border-border
                                   text-[10px] font-mono text-ink/70"
                      >
                        {f}
                      </span>
                    ))}
                  </div>
                </div>

                {/* Micrometer */}
                <div className="flex flex-col gap-1">
                  <span className="text-[10px] text-gray-400 uppercase tracking-wide font-medium">Micrometer</span>
                  <div className="flex items-center gap-1.5 text-[12px] text-ok font-medium">
                    <span className="w-[6px] h-[6px] rounded-full bg-ok inline-block" />
                    active
                  </div>
                </div>

                {/* Prometheus */}
                <div className="flex flex-col gap-0.5">
                  <span className="text-[10px] text-gray-400 uppercase tracking-wide font-medium">Prometheus</span>
                  <div className="flex items-center gap-1">
                    <code className="flex-1 text-[10px] font-mono text-ink/70 bg-muted rounded px-1.5 py-0.5 border border-border truncate">
                      your-api/actuator/prometheus
                    </code>
                    <CopyButton text="your-api/actuator/prometheus" />
                  </div>
                </div>

                {/* links */}
                <div className="flex flex-col gap-1 border-t border-border pt-2">
                  <a
                    href={DISCUSSIONS_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 text-[12px] text-gray-600 hover:text-ink transition-colors"
                  >
                    <span>💬</span>
                    <span>GitHub Discussions — ajuda</span>
                  </a>
                  <a
                    href={DOCS_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 text-[12px] text-gray-600 hover:text-ink transition-colors"
                  >
                    <span>📖</span>
                    <span>Documentação</span>
                  </a>
                </div>

                <div className="border-t border-border pt-2 flex items-center justify-between">
                  <span className="text-[10px] text-gray-400 font-mono">sagaweaw</span>
                  <span className="text-[10px] text-gray-400 font-mono">v1.0.1</span>
                </div>

              </div>
            )}
          </div>

          {/* theme toggle */}
          <button
            onClick={toggle}
            title={theme === 'dark' ? 'Tema claro' : 'Tema escuro'}
            className="w-8 h-8 rounded-lg flex items-center justify-center
                       text-gray-500 hover:text-ink hover:bg-muted
                       border border-border transition-all duration-200"
          >
            {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
          </button>

          {/* language toggle */}
          <div className="flex gap-[2px]">
            {LANGS.map(({ code, label }) => (
              <button
                key={code}
                onClick={() => i18n.changeLanguage(code)}
                className={`px-2 py-[3px] rounded text-[11px] font-medium transition-colors ${
                  i18n.language === code || i18n.language.startsWith(code.split('-')[0])
                    ? 'bg-ink text-canvas'
                    : 'text-gray-500 hover:text-ink hover:bg-muted'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

      </div>
    </header>
  )
}
