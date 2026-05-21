import { useState, useRef, useEffect } from 'react'
import { createPortal } from 'react-dom'

interface Option {
  value: string
  label: string
}

interface SelectProps {
  value: string
  onChange: (value: string) => void
  options: Option[]
  placeholder?: string
}

export default function Select({ value, onChange, options, placeholder }: SelectProps) {
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState({ top: 0, left: 0, width: 0 })
  const triggerRef  = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)

  const selected = options.find(o => o.value === value)

  function handleToggle() {
    if (!open && triggerRef.current) {
      const r = triggerRef.current.getBoundingClientRect()
      setPos({ top: r.bottom + 4, left: r.left, width: r.width })
    }
    setOpen(o => !o)
  }

  useEffect(() => {
    function onClose(e: MouseEvent) {
      if (triggerRef.current?.contains(e.target as Node)) return
      if (dropdownRef.current?.contains(e.target as Node)) return
      setOpen(false)
    }
    if (open) document.addEventListener('mousedown', onClose)
    return () => document.removeEventListener('mousedown', onClose)
  }, [open])

  return (
    <div className="relative">
      <button
        ref={triggerRef}
        type="button"
        onClick={handleToggle}
        className={`
          flex items-center gap-2 bg-surface border rounded-xl px-3 py-[7px] text-[13px] text-ink
          shadow-sm transition-all duration-200 cursor-pointer min-w-[160px] w-full justify-between
          ${open
            ? 'border-accent ring-2 ring-accent/20'
            : 'border-border hover:border-accent/60'
          }
        `}
      >
        <span className={selected ? 'text-ink' : 'text-gray-400'}>
          {selected ? selected.label : (placeholder ?? '')}
        </span>
        <svg
          className={`w-3 h-3 text-gray-400 transition-transform duration-200 flex-shrink-0 ${open ? 'rotate-180' : ''}`}
          viewBox="0 0 12 12" fill="currentColor"
        >
          <path d="M6 8L1 3h10z" />
        </svg>
      </button>

      {open && createPortal(
        <div
          ref={dropdownRef}
          style={{ position: 'fixed', top: pos.top, left: pos.left, minWidth: pos.width, zIndex: 9999 }}
          className="bg-surface border border-border rounded-xl shadow-lg overflow-hidden animate-in"
        >
          {options.map(opt => (
            <button
              key={opt.value}
              type="button"
              onClick={() => { onChange(opt.value); setOpen(false) }}
              className={`
                w-full text-left px-3 py-[8px] text-[13px] transition-colors duration-100
                ${opt.value === value
                  ? 'bg-muted text-accent font-medium'
                  : 'text-ink hover:bg-muted'
                }
              `}
            >
              {opt.label}
            </button>
          ))}
        </div>,
        document.body
      )}
    </div>
  )
}
