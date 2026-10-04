import type { KeptMatch } from '@/api/diets'
import type { DishIngredient } from '@/api/types'

/**
 * The matches a cell carries through a re-read of its text (FD-048).
 *
 * Parse matches by exact Spanish name only, so a food picked in the composer or
 * by hand in the fix-up list would be lost — or swapped for the name's preferred
 * row — the next time the text is read. The editor hands back what it holds, and
 * the backend keeps it on every ingredient whose name is unchanged.
 *
 * Nothing here compares names or reads text: the backend pairs the names, and
 * the matches carried next are simply the ones its answer comes back with, so a
 * name that is gone from the text drops out on its own.
 */

/** The matched ingredients, as matches to carry. An unmatched one carries nothing. */
export function matchesOf(ingredients: readonly DishIngredient[]): KeptMatch[] {
  const matches: KeptMatch[] = []
  for (const ingredient of ingredients) {
    const food =
      ingredient.compositionFoodId !== null
        ? { compositionFoodId: ingredient.compositionFoodId }
        : ingredient.foodItemId !== null
          ? { foodItemId: ingredient.foodItemId }
          : null
    if (food) {
      matches.push({
        name: ingredient.name,
        ...food,
        ...(ingredient.foodMeasureId !== null ? { foodMeasureId: ingredient.foodMeasureId } : {}),
      })
    }
  }
  return matches
}
