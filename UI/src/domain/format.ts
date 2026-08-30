/** Spanish figures: a dot groups the thousands and a comma marks the decimals. */

const INTEGER = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 })
const ONE_DECIMAL = new Intl.NumberFormat('es-ES', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
})

/** A figure the backend could not work out reads as an em dash, never as zero. */
export const NO_VALUE = '—'

export function integer(value: number | null | undefined): string {
  return value === null || value === undefined ? NO_VALUE : INTEGER.format(value)
}

export function grams(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return NO_VALUE
  }
  return Number.isInteger(value) ? INTEGER.format(value) : ONE_DECIMAL.format(value)
}

/** A difference against the goal, with the typographic minus the design uses. */
export function signed(value: number): string {
  return (value >= 0 ? '+' : '\u2212') + INTEGER.format(Math.abs(value))
}

/** `80` and `gr` as one string, the way a diet writes a portion. */
export function quantity(amount: number, unit: string): string {
  return `${grams(amount)} ${unit}`.trim()
}
