import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import logo from '../assets/logo.svg'
import { setToken } from '../auth'

interface Props { onAuth: () => void }

export default function TokenGate({ onAuth }: Props) {
  const { t } = useTranslation()
  const [value,   setValue]   = useState('')
  const [error,   setError]   = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const token = value.trim()
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      const res = await fetch('/api/sagas/metrics', {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (res.status === 403 || res.status === 401) {
        setError(t('auth.error403'))
        return
      }
      if (!res.ok) {
        setError(t('auth.errorOther'))
        return
      }
      setToken(token)
      onAuth()
    } catch {
      setError(t('auth.errorOther'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-canvas flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="flex flex-col items-center mb-8">
          <img src={logo} className="h-12 w-auto mb-4" alt="Sagaweaw" />
          <h1 className="text-[15px] font-medium text-ink">{t('auth.title')}</h1>
          <p className="text-[12px] text-gray-400 mt-1 text-center">{t('auth.subtitle')}</p>
        </div>

        <form
          onSubmit={submit}
          className="bg-surface border border-border rounded-[10px] p-6 flex flex-col gap-3"
        >
          <input
            type="password"
            value={value}
            onChange={e => setValue(e.target.value)}
            placeholder={t('auth.placeholder')}
            autoFocus
            className="bg-canvas border border-border rounded-[6px] px-3 py-[9px] text-[13px] text-ink
                       placeholder:text-gray-400 focus:outline-none focus:border-accent transition-colors"
          />

          {error && (
            <div className="text-[12px] text-accent bg-muted border border-border rounded-[6px] px-3 py-2">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading || !value.trim()}
            className="bg-ink text-canvas rounded-[6px] py-[9px] text-[13px] font-medium
                       hover:opacity-80 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {loading ? '…' : t('auth.submit')}
          </button>
        </form>
      </div>
    </div>
  )
}
