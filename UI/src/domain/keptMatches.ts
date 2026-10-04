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
 * Nothing here reads text: the backend pairs the names, and the matches carried
 * next are simply the ones its answer comes back with, so a name that is gone
 * from the text drops out on its own.
 *
 * What dropped out is remembered beside the cell until the draft is published or
 * thrown away (FD-056), and sent again behind what the cell holds: a name half
 * typed loses its match, and typing it back exactly brings the match back, its
 * picked measure included. A name changed for good never pairs with the entry,
 * so the old food reaches nothing. Entries are told apart by the name as the
 * backend wrote it, character for character — a stricter test than its own
 * pairing, so the worst a near miss costs is an entry sent twice, which the
 * backend pairs in order and leaves the spare unused.
 *
 * A measure goes along only when a person picked it (FD-054). One the rule
 * chose is left out, so the rule chooses again on the re-read and follows any
 * criterion written since.
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
        ...(ingredient.foodMeasureId !== null && ingredient.measurePicked
          ? { foodMeasureId: ingredient.foodMeasureId }
          : {}),
      })
    }
  }
  return matches
}

/** The same name on the same food. */
function sameEntry(a: KeptMatch, b: KeptMatch): boolean {
  return (
    a.name === b.name && a.compositionFoodId === b.compositionFoodId && a.foodItemId === b.foodItemId
  )
}

/**
 * What a read of the cell sends (FD-056): what it holds, then what earlier reads
 * dropped. An entry held without a measure takes the one its remembered twin was
 * picked with — the measure dropped while the unit was half typed — and the
 * backend drops it again unless the unit is once more the word it measures.
 */
export function toSend(keep: readonly KeptMatch[], dropped: readonly KeptMatch[]): KeptMatch[] {
  const rest = [...dropped]
  const held = keep.map((entry) => {
    if (entry.foodMeasureId !== undefined) {
      return entry
    }
    const at = rest.findIndex((old) => sameEntry(old, entry) && old.foodMeasureId !== undefined)
    return at === -1 ? entry : { ...entry, foodMeasureId: rest.splice(at, 1)[0].foodMeasureId }
  })
  return [...held, ...rest]
}

/**
 * The entries a read was sent and did not come back with — the ones to remember
 * for the next read. One that came back with its food but without its measure is
 * remembered too, for the measure. Each is remembered once.
 */
export function droppedFrom(sent: readonly KeptMatch[], kept: readonly KeptMatch[]): KeptMatch[] {
  const answered = [...kept]
  const dropped: KeptMatch[] = []
  for (const entry of sent) {
    const at = answered.findIndex(
      (back) =>
        sameEntry(back, entry) &&
        (entry.foodMeasureId === undefined || back.foodMeasureId === entry.foodMeasureId),
    )
    if (at !== -1) {
      answered.splice(at, 1)
    } else if (!dropped.some((seen) => sameEntry(seen, entry))) {
      dropped.push(entry)
    }
  }
  return dropped
}
