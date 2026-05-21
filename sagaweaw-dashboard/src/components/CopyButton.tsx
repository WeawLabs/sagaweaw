import { useState } from 'react'

interface Props {
  text: string
  className?: string
}

export default function CopyButton({ text, className = '' }: Props) {
  const [copied, setCopied] = useState(false)

  function copy() {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <button
      onClick={copy}
      title={copied ? 'Copiado!' : 'Copiar'}
      className={`inline-flex items-center justify-center p-1 rounded-md
                  transition-all duration-150 border flex-shrink-0
                  ${copied
                    ? 'bg-ok/10 border-ok/30 text-ok'
                    : 'bg-muted border-border text-gray-500 hover:border-accent hover:text-accent'
                  } ${className}`}
    >
      {copied ? <CheckIcon /> : <CopyIcon />}
    </button>
  )
}

function CopyIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <rect x="5" y="5" width="9" height="9" rx="1.5" />
      <path d="M11 5V3.5A1.5 1.5 0 0 0 9.5 2h-6A1.5 1.5 0 0 0 2 3.5v6A1.5 1.5 0 0 0 3.5 11H5" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 8l3.5 3.5L13 4" />
    </svg>
  )
}
