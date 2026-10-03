import type { FoodMeasure, Ration, WeightBasis } from '@/api/types'
import { midpointProposal } from './measureCriteria'
import { range, rationIsFixed, rationWeight } from './rations'

/**
 * What "Añadir por raciones" is about to write: a count of a published ration,
 * a count of a household measure, or a weight typed by hand.
 */
export type ComposerChoice =
  | { kind: 'ration'; ration: Ration }
  | { kind: 'measure'; measure: FoodMeasure }
  | { kind: 'grams' }

export const GRAMS_CHOICE: ComposerChoice = { kind: 'grams' }

/** A gross ration is weighed with the inedible part; the diet weighs what is eaten. */
const GROSS: WeightBasis = 'GROSS'

export function sameChoice(current: ComposerChoice, candidate: ComposerChoice): boolean {
  if (current.kind === 'ration' && candidate.kind === 'ration') {
    return current.ration.id === candidate.ration.id
  }
  if (current.kind === 'measure' && candidate.kind === 'measure') {
    return current.measure.id === candidate.measure.id
  }
  return current.kind === candidate.kind
}

/**
 * The weight of one ration the composer starts from. A fixed ration is its own
 * figure. A range (`1 huevo · 53–63 g`) starts from its middle, flagged as a
 * **proposal** the nutritionist sees and may change — never applied unseen. A
 * gross weight starts empty: what is eaten of it is hers to say.
 */
export interface WeightStart {
  grams: number | null
  proposed: boolean
}

export function rationStart(ration: Ration): WeightStart {
  if (ration.weightBasis === GROSS) {
    return { grams: null, proposed: false }
  }
  const min = ration.gramsMin ?? ration.mlMin
  const max = ration.gramsMax ?? ration.mlMax
  if (rationIsFixed(ration)) {
    return { grams: min, proposed: false }
  }
  const middle = midpointProposal(min, max)
  return { grams: middle, proposed: middle !== null }
}

/** Plain grams, and a ration that is a range or a gross weight, need a weight typed. */
export function asksForWeight(choice: ComposerChoice): boolean {
  return (
    choice.kind === 'grams' ||
    (choice.kind === 'ration' &&
      (!rationIsFixed(choice.ration) || choice.ration.weightBasis === GROSS))
  )
}

/** The label over the weight field. */
export function weightHint(choice: ComposerChoice, proposed: boolean): string {
  if (choice.kind !== 'ration') {
    return 'g'
  }
  const published = rationWeight(choice.ration)
  if (choice.ration.weightBasis === GROSS) {
    return `g comestibles por ración (publicado: ${published})`
  }
  return proposed ? `g por ración · propuesta (${published})` : `g por ración (${published})`
}

/** `53–63`: the published range a proposal was taken from. */
export function rationRange(ration: Ration): string {
  return ration.gramsMin !== null || ration.gramsMax !== null
    ? `${range(ration.gramsMin, ration.gramsMax)} g`
    : `${range(ration.mlMin, ration.mlMax)} ml`
}

/** A ration published only as a volume: written in ml, read as grams. */
export function writesMl(choice: ComposerChoice): boolean {
  return choice.kind === 'ration' && choice.ration.gramsMin === null
}

/** What the choice weighs in all, or null while it cannot be weighed yet. */
export function choiceTotal(
  choice: ComposerChoice,
  count: number,
  grams: number | null,
): number | null {
  if (choice.kind === 'measure') {
    return choice.measure.gramsPerMeasure === null ? null : choice.measure.gramsPerMeasure * count
  }
  if (grams === null || grams <= 0) {
    return null
  }
  return choice.kind === 'ration' ? grams * count : grams
}
