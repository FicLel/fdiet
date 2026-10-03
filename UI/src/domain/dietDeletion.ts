import type { JournalCounts } from '@/api/types'

/**
 * What a confirm says before a diet is deleted. The counts are the backend's,
 * read just before asking; nothing here counts anything itself.
 */

/** `1 plato valorado`, `5 platos valorados`. */
function scoredPhrase(scored: number): string {
  return scored === 1 ? '1 plato valorado' : `${scored} platos valorados`
}

/** `1 extra`, `2 extras`. */
function extrasPhrase(extras: number): string {
  return extras === 1 ? '1 extra' : `${extras} extras`
}

/**
 * `Se eliminará «Semana 1» con 5 platos valorados y 2 extras. No se puede deshacer.`
 * Both counts are always named, zeros included: "nothing goes with it" is
 * information too, and a missing number reads as an unknown one.
 */
export function deletionWarning(name: string, counts: JournalCounts): string {
  return `Se eliminará «${name}» con ${scoredPhrase(counts.scored)} y ${extrasPhrase(counts.extras)}. No se puede deshacer.`
}
