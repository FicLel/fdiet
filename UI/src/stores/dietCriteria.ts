import { dietsApi } from '@/api/diets'
import { criterionRequest, type CriterionUnit } from '@/domain/measureCriteria'
import { useDietDraft } from './dietDraft'
import { useFoodLink } from './foodLink'

/**
 * One diet's own weights for a household measure — "criterio para esta
 * dieta" — written from the fix-up drawer, for the measure the open ingredient
 * is written in.
 *
 * A criterion names a CIQUAL / BLS food, chosen by the nutritionist; the
 * ingredient's own match is still BEDCA's until the foods are re-matched
 * (FD-033 phase D), so for now it attaches to nothing and `attached` comes back
 * 0. That is the honest answer, not a failure.
 */

const draft = useDietDraft()
const link = useFoodLink()

/**
 * Writes (or rewrites) the criterion for the open ingredient's measure and
 * size. It is attached to every ingredient of the week it now weighs, so the
 * week is read again rather than patched one row at a time. Answers how many
 * ingredients it weighs, or null when nothing was saved.
 */
async function saveDietCriterion(
  compositionFoodId: number,
  value: number,
  unit: CriterionUnit,
  note: string,
): Promise<number | null> {
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
    return saved.attached
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
