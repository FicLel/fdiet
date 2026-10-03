import type {
  FoodMeasure,
  FoodState,
  PortionSize,
  Ration,
  RecommendationCheck,
  RecommendationStatus,
} from '@/api/types'
import { NO_VALUE } from './format'

/**
 * Display only. Every count on screen is one the backend sent: nothing here
 * divides a weight by a ration, picks a midpoint of a range, or decides whether
 * a day meets a recommendation. (The midpoints the UI shows — a range measure's
 * weight per unit, a range ration's weight in the composer — are proposals for
 * the nutritionist to confirm: `midpointProposal` in `measureCriteria.ts`.)
 */

const UP_TO_TWO = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 })
const UP_TO_ONE = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 1 })

export function amount(value: number | null | undefined, digits: 1 | 2 = 2): string {
  if (value === null || value === undefined) {
    return NO_VALUE
  }
  return (digits === 1 ? UP_TO_ONE : UP_TO_TWO).format(value)
}

/** `1,2`, or `0,8–1,33` when the two ends differ. A range stays a range. */
export function range(min: number | null, max: number | null, digits: 1 | 2 = 2): string {
  if (min === null && max === null) {
    return NO_VALUE
  }
  if (min === null || max === null || min === max) {
    return amount(min ?? max, digits)
  }
  return `${amount(min, digits)}–${amount(max, digits)}`
}

const STATE_WORDS: Record<FoodState, string> = {
  RAW: 'en crudo',
  DRY: 'en seco',
  COOKED: 'cocinado',
  CANNED: 'en conserva',
  DRAINED: 'escurrido',
  UNSPECIFIED: '',
}

export function stateWord(state: FoodState | null | undefined): string {
  return state ? STATE_WORDS[state] : ''
}

const SIZE_WORDS: Record<PortionSize, string> = {
  SMALL: 'pequeña',
  MEDIUM: 'mediana',
  LARGE: 'grande',
}

export function sizeWord(size: PortionSize | null | undefined): string {
  return size ? SIZE_WORDS[size] : ''
}

/** `60–80 g en seco`, `10 ml`, `120–200 g peso bruto`. */
export function rationWeight(ration: Ration): string {
  const grams = ration.gramsMin !== null || ration.gramsMax !== null
  const ml = ration.mlMin !== null || ration.mlMax !== null
  const parts: string[] = []
  if (grams) {
    parts.push(`${range(ration.gramsMin, ration.gramsMax)} g`)
  } else if (ml) {
    parts.push(`${range(ration.mlMin, ration.mlMax)} ml`)
  } else if (ration.householdText) {
    parts.push(ration.householdText)
  }
  const state = stateWord(ration.state)
  if (state) {
    parts.push(state)
  }
  if (ration.weightBasis === 'GROSS') {
    parts.push('peso bruto')
  }
  return parts.join(' ')
}

/** Whether a ration publishes one weight, rather than a range or none at all. */
export function rationIsFixed(ration: Ration): boolean {
  const min = ration.gramsMin ?? ration.mlMin
  const max = ration.gramsMax ?? ration.mlMax
  return min !== null && min === max
}

/**
 * `1 cucharada sopera · 10 ml`, `3 unidades medianas · 180 g`, `1 unidad · 53–63 g`.
 * The weight is the published one for the count published.
 */
export function measureText(measure: FoodMeasure): string {
  const size = sizeWord(measure.size)
  // The source's own wording when it has one ("3 Uds. medianas"); a count of
  // one of the bare word otherwise, never a plural guessed here.
  const head =
    measure.householdText ??
    `${measure.count === 1 ? '1' : `${amount(measure.count)} ×`} ${measure.measureLabel}${size ? ` ${size}` : ''}`
  const grams = measure.gramsMin !== null || measure.gramsMax !== null
  const weight = grams
    ? `${range(measure.gramsMin, measure.gramsMax)} g`
    : `${range(measure.mlMin, measure.mlMax)} ml`
  const state = stateWord(measure.state)
  return `${head} · ${weight}${state ? ` ${state}` : ''}`
}

/** `1 cucharadita → 5 g`: what one of the written unit weighs, when it weighs anything. */
export function perMeasure(measure: FoodMeasure): string | null {
  if (measure.gramsPerMeasure === null) {
    return null
  }
  return `1 ${measure.measureLabel} → ${amount(measure.gramsPerMeasure, 1)} g`
}

export function measureSource(measure: FoodMeasure): string {
  if (measure.dietOwn) {
    return 'Criterio de esta dieta'
  }
  return measure.globalOwn ? 'Tu criterio' : (measure.sourceShortName ?? 'Sin fuente')
}

/** `≥ 4 a la semana`, `≤ 3 al día`, `2–3 al día`. */
export function recommendationText(check: RecommendationCheck): string {
  const period = check.period === 'PER_WEEK' ? 'a la semana' : 'al día'
  const { rationsMin: min, rationsMax: max } = check
  if (min !== null && max !== null) {
    return `${range(min, max)} ${period}`
  }
  if (min !== null) {
    return `≥ ${amount(min)} ${period}`
  }
  return `≤ ${amount(max)} ${period}`
}

export const STATUS_LABELS: Record<RecommendationStatus, string> = {
  WITHIN: 'Dentro',
  BELOW: 'Por debajo',
  ABOVE: 'Por encima',
  UNCERTAIN: 'Sin concluir',
}

export const STATUS_HINTS: Record<RecommendationStatus, string> = {
  WITHIN: 'Todo el recuento cae dentro de la recomendación.',
  BELOW: 'Todo el recuento queda por debajo, y no queda nada sin contar que pudiera sumar.',
  ABOVE: 'Lo contado ya supera la recomendación.',
  UNCERTAIN:
    'El recuento cruza un límite, o hay ingredientes sin contar que podrían cambiar el resultado.',
}
