import { useState, useEffect, useRef } from 'react'
import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import logo from '../assets/logo.svg'
import { useTheme } from '../hooks/useTheme'
import { getToken, clearToken } from '../auth'
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

function BarChartIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="18" y1="20" x2="18" y2="10" />
      <line x1="12" y1="20" x2="12" y2="4"  />
      <line x1="6"  y1="20" x2="6"  y2="14" />
    </svg>
  )
}

function MessageIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
    </svg>
  )
}

function ExternalLinkIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
      <polyline points="15 3 21 3 21 9" />
      <line x1="10" y1="14" x2="21" y2="3" />
    </svg>
  )
}

function LogOutIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" y1="12" x2="9" y2="12" />
    </svg>
  )
}

export default function Header({ onLogout }: { onLogout?: () => void }) {
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

  function logout() {
    clearToken()
    onLogout?.()
  }

  async function downloadGrafanaDashboard() {
    const res = await fetch('/api/grafana-dashboard', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!res.ok) return
    const blob = await res.blob()
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = 'sagaweaw-grafana-dashboard.json'
    a.click()
    URL.revokeObjectURL(url)
  }

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
                <div className="flex flex-col border-t border-border pt-2">
                  <button
                    onClick={downloadGrafanaDashboard}
                    className="flex items-center gap-2.5 px-2 py-[7px] rounded-lg text-[12px]
                               text-gray-500 hover:text-ink hover:bg-muted
                               transition-all duration-150 text-left"
                  >
                    <span className="text-gray-400"><BarChartIcon /></span>
                    Grafana Dashboard (JSON)
                  </button>
                  <a
                    href={DISCUSSIONS_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2.5 px-2 py-[7px] rounded-lg text-[12px]
                               text-gray-500 hover:text-ink hover:bg-muted
                               transition-all duration-150"
                  >
                    <span className="text-gray-400"><MessageIcon /></span>
                    GitHub Discussions
                  </a>
                  <a
                    href={DOCS_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2.5 px-2 py-[7px] rounded-lg text-[12px]
                               text-gray-500 hover:text-ink hover:bg-muted
                               transition-all duration-150"
                  >
                    <span className="text-gray-400"><ExternalLinkIcon /></span>
                    Documentação
                  </a>
                </div>

                <div className="border-t border-border pt-2 flex items-center justify-between">
                  <span className="text-[10px] text-gray-400 font-mono">
                    sagaweaw v{import.meta.env.VITE_APP_VERSION}
                  </span>
                  <button
                    onClick={logout}
                    className="flex items-center gap-1.5 px-2 py-[5px] rounded-lg text-[11px]
                               text-gray-400 hover:text-fail hover:bg-fail/10
                               transition-all duration-150 font-medium"
                  >
                    <LogOutIcon />
                    Sair
                  </button>
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
