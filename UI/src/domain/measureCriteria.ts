import type {
  FoodMeasure,
  HouseholdMeasure,
  MeasureCriterionRequest,
  MeasureReweigh,
  MeasureUsage,
  PortionSize,
} from '@/api/types'
import { amount, measureSource, measureText, range, sizeWord } from './rations'

/**
 * The nutritionist's own weight for a household measure — "tu criterio".
 *
 * A published range (`1 huevo mediano · 53–63 g`) never weighs on its own. The
 * first time it is used the nutritionist confirms a weight per unit, and the
 * form starts from the middle of the published range: that is a **proposal**
 * shown as one, with the range and its source beside it, never a weight applied
 * without her. What she confirms is stored as her criterion and reused in every
 * diet; nothing here stores or decides anything.
 */

/** A criterion weighs in exactly one of the two. */
export type CriterionUnit = 'g' | 'ml'

export const CRITERION_UNITS: readonly CriterionUnit[] = ['g', 'ml']

/** The sizes a criterion can be narrowed to; null is any size. */
export const SIZE_OPTIONS: { value: PortionSize | null; label: string }[] = [
  { value: null, label: 'Sin tamaño' },
  { value: 'SMALL', label: 'Pequeño' },
  { value: 'MEDIUM', label: 'Mediano' },
  { value: 'LARGE', label: 'Grande' },
]

/** The measure word offered first for a new unit. */
export const DEFAULT_MEASURE: HouseholdMeasure = 'UNIDAD'

/** Written by the nutritionist, for this diet or for every diet — not a published row. */
export function isOwnCriterion(measure: FoodMeasure): boolean {
  return measure.dietOwn || measure.globalOwn
}

/** Whether one measure has one weight, rather than a range. */
export function weighs(measure: FoodMeasure): boolean {
  return measure.gramsPerMeasure !== null
}

/**
 * The nutritionist's criterion that already answers a published row: same
 * measure word and size. The backend lists the diet's own before the global
 * ones, so the first found is the one that wins.
 */
export function criterionFor(
  measures: readonly FoodMeasure[],
  published: FoodMeasure,
): FoodMeasure | null {
  return (
    measures.find(
      (candidate) =>
        isOwnCriterion(candidate) &&
        weighs(candidate) &&
        candidate.measure === published.measure &&
        candidate.size === published.size,
    ) ?? null
  )
}

/** Grams when the row publishes grams, ml when it publishes only a volume. */
export function unitOf(measure: FoodMeasure): CriterionUnit {
  return measure.gramsMin === null && measure.gramsMax === null && measure.mlMin !== null
    ? 'ml'
    : 'g'
}

/**
 * The middle of a published range, per one measure, to one decimal — offered
 * as a proposal for the nutritionist to confirm or change. Null when the row
 * publishes no figure at all.
 */
export function proposedPerUnit(measure: FoodMeasure): number | null {
  if (measure.count <= 0) {
    return null
  }
  return unitOf(measure) === 'ml'
    ? midpointProposal(measure.mlMin, measure.mlMax, measure.count)
    : midpointProposal(measure.gramsMin, measure.gramsMax, measure.count)
}

/**
 * The middle of `min–max` divided by `per`, to one decimal: the only figure the
 * UI ever proposes on its own, and always shown as a proposal. One open end
 * stands for both; null when neither end is published.
 */
export function midpointProposal(min: number | null, max: number | null, per = 1): number | null {
  const low = min ?? max
  const high = max ?? min
  if (low === null || high === null) {
    return null
  }
  return Math.round(((low + high) / 2 / per) * 10) / 10
}

/** `AESAN 2022: 1 huevo mediano · 53–63 g` — the published figure, with whose it is. */
export function publishedFigure(measure: FoodMeasure): string {
  const gross = measure.weightBasis === 'GROSS' ? ' (peso bruto)' : ''
  return `${measureSource(measure)}: ${measureText(measure)}${gross}`
}

/** `unidad mediana`: the measure word with its size, singular as the vocabulary gives it. */
export function measureName(measure: Pick<FoodMeasure, 'measureLabel' | 'size'>): string {
  const size = sizeWord(measure.size)
  return size ? `${measure.measureLabel} ${size}` : measure.measureLabel
}

/**
 * `2 × unidad mediana ≈ 116 g`: what the count weighs, for the nutritionist
 * only. The cell itself is written by the backend, in units.
 */
export function unitsWeight(count: number, measure: FoodMeasure): string | null {
  if (measure.gramsPerMeasure === null || count <= 0) {
    return null
  }
  return `${amount(count)} × ${measureName(measure)} ≈ ${amount(measure.gramsPerMeasure * count, 1)} g`
}

/** `58 g por unidad mediana`, or `5 ml por cucharadita`. */
export function criterionWeight(measure: FoodMeasure): string {
  const unit = unitOf(measure)
  const value = unit === 'ml' ? range(measure.mlMin, measure.mlMax) : range(measure.gramsMin, measure.gramsMax)
  return `${value} ${unit} por ${measureName(measure)}`
}

/** The criterion's own figure, to start an edit from. */
export function criterionValue(measure: FoodMeasure): number | null {
  return unitOf(measure) === 'ml' ? measure.mlMin : measure.gramsMin
}

/** The body for a criterion: one weight, in the unit chosen. */
export function criterionRequest(
  compositionFoodId: number,
  measure: HouseholdMeasure,
  size: PortionSize | null,
  value: number,
  unit: CriterionUnit,
  note: string | null = null,
): MeasureCriterionRequest {
  return {
    measure,
    size,
    compositionFoodId,
    grams: unit === 'g' ? value : null,
    ml: unit === 'ml' ? value : null,
    note,
  }
}

/** `Afecta a 3 ingredientes y 1 extra en todas las dietas.` */
export function usageText(usage: MeasureUsage): string {
  if (usage.ingredients === 0 && usage.extraFoods === 0) {
    return 'Ahora no pesa ningún ingrediente ni extra.'
  }
  return `Afecta a ${rowsText(usage)} en todas las dietas, al momento.`
}

/** `3 ingredientes y 1 extra`. */
function rowsText(rows: { ingredients: number; extraFoods: number }): string {
  return `${countOf(rows.ingredients, 'ingrediente', 'ingredientes')} y ${countOf(rows.extraFoods, 'extra', 'extras')}`
}

/**
 * What saving a criterion re-chose (FD-054): the ingredients and extras whose
 * measure the rule had chosen and now lands on another row. A new weight on a
 * row already in use is live without changing any row, so it counts 0. A
 * measure the nutritionist picked is never among them, and the sentence says so.
 */
export function reweighedText(reweighed: MeasureReweigh): string {
  const total = reweighed.ingredients + reweighed.extraFoods
  const parts = [
    reweighed.ingredients === 0 ? null : countOf(reweighed.ingredients, 'ingrediente', 'ingredientes'),
    reweighed.extraFoods === 0 ? null : countOf(reweighed.extraFoods, 'extra', 'extras'),
  ].filter((part) => part !== null)
  const lead =
    total === 0
      ? 'Ningún ingrediente ni extra cambia de medida.'
      : `${total === 1 ? 'Cambia' : 'Cambian'} de medida ${parts.join(' y ')} con medida automática.`
  return `${lead} Las medidas que elegiste tú no cambian.`
}

/** `1 ingrediente`, `3 ingredientes`. */
function countOf(count: number, singular: string, plural: string): string {
  return `${count} ${count === 1 ? singular : plural}`
}
