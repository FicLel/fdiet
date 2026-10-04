import { dietsApi } from '@/api/diets'
import type { MeasureReweigh } from '@/api/types'
import { criterionRequest, type CriterionUnit } from '@/domain/measureCriteria'
import { useDietDraft } from './dietDraft'
import { useFoodLink } from './foodLink'

/**
 * One diet's own weights for a household measure — "criterio para esta
 * dieta" — written from the fix-up drawer, for the measure the open ingredient
 * is written in.
 *
 * A criterion names a CIQUAL / BLS food, chosen by the nutritionist, and
 * re-weighs only ingredients and extras matched to that food whose measure the
 * rule chose (FD-054): nothing re-weighed is an honest answer (another food was
 * picked, or every measure was the nutritionist's own pick), not a failure.
 */

const draft = useDietDraft()
const link = useFoodLink()

/**
 * Writes (or rewrites) the criterion for the open ingredient's measure and
 * size. Every ingredient of the week whose measure the rule chose is weighed
 * again, so the week is read again rather than patched one row at a time.
 * Answers what was re-weighed, or null when nothing was saved.
 */
async function saveDietCriterion(
  compositionFoodId: number,
  value: number,
  unit: CriterionUnit,
  note: string,
): Promise<MeasureReweigh | null> {
  const at = link.target.value
  const plan = draft.diet.value
  const word = link.measureWord.value
  if (!at || !plan || !word || link.savingMeasure.value || !(value > 0)) {
    return null
  }
  link.savingMeasure.value = true
  link.error.value = null
  try {
    const saved = await dietsApi.saveMeasure(
      plan.id,
      criterionRequest(compositionFoodId, word, at.size, value, unit, note.trim() || null),
    )
    const id = at.id
    await draft.refresh()
    const fresh = draft.findIngredient(id)
    if (fresh) {
      link.target.value = fresh
    }
    await link.loadMeasures()
    return saved.reweighed
  } catch (cause) {
    link.error.value = `No se pudo guardar el criterio${cause instanceof Error ? `: ${cause.message}` : ''}`
    return null
  } finally {
    link.savingMeasure.value = false
  }
}

export function useDietCriteria() {
  return { saveDietCriterion }
}
