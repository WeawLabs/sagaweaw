import { useState, useEffect } from 'react'

type Theme = 'light' | 'dark'

function getInitial(): Theme {
  const saved = localStorage.getItem('sagaweaw-theme') as Theme | null
  if (saved) return saved
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(getInitial)

  useEffect(() => {
    const root = document.documentElement
    if (theme === 'dark') {
      root.classList.add('dark')
    } else {
      root.classList.remove('dark')
    }
    localStorage.setItem('sagaweaw-theme', theme)
  }, [theme])

  function toggle() {
    setTheme(t => t === 'dark' ? 'light' : 'dark')
  }

  return { theme, toggle }
}
