export function formatPrice(price: number | null) {
  if (price == null) {
    return '--'
  }

  return price.toFixed(2)
}

export function formatPercent(pct: number | null) {
  if (pct == null) {
    return '--'
  }

  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(2)}%`
}

export function formatDate(value: string | undefined) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')

  return `${year}-${month}-${day} ${hours}:${minutes}`
}

export function toneByPercent(pct: number | null) {
  if (pct == null || pct === 0) {
    return 'neutral'
  }

  return pct > 0 ? 'up' : 'down'
}
