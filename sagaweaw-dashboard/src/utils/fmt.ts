export function fmtDuration(ms: number): string {
  if (ms < 1_000)      return `${Math.round(ms)}ms`
  if (ms < 60_000)     return `${(ms / 1_000).toFixed(1)}s`
  if (ms < 3_600_000) {
    const m = Math.floor(ms / 60_000)
    const s = Math.floor((ms % 60_000) / 1_000)
    return s > 0 ? `${m}m ${s}s` : `${m}m`
  }
  if (ms < 86_400_000) {
    const h = Math.floor(ms / 3_600_000)
    const m = Math.floor((ms % 3_600_000) / 60_000)
    return m > 0 ? `${h}h ${m}m` : `${h}h`
  }
  const d = Math.floor(ms / 86_400_000)
  const h = Math.floor((ms % 86_400_000) / 3_600_000)
  return h > 0 ? `${d}d ${h}h` : `${d}d`
}

export function fmtCount(n: number): string {
  if (n < 1_000)       return String(n)
  if (n < 1_000_000)   return `${(n / 1_000).toFixed(n % 1_000 === 0 ? 0 : 1)}k`
  return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`
}
