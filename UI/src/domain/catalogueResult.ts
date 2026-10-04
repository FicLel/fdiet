import type { CompositionFood } from '@/api/compositionTypes'
import type { FoodItem, FoodSuggestion } from '@/api/types'
import { compositionFoodName } from './compositionFood'

/**
 * A food to match against, from either half of the catalogue, in the one shape
 * the pickers draw: the fix-up drawer and the patient's extras.
 *
 * Exactly one of `compositionFoodId` (CIQUAL / BLS) and `foodItemId` (a
 * branded product) is set, the same rule the backend keeps for an ingredient.
 */
export interface CatalogueResult {
  key: string
  compositionFoodId: number | null
  foodItemId: number | null
  name: string
  /** The source table (CIQUAL 2025 / BLS 4.0) for a generic food, the brand for a product. */
  note: string | null
  /** The EAN, which only a branded product has. */
  code: string | null
  /** Energy per 100 g, or null where the source published none. */
  kcalPer100: number | null
  /** 0–100, how much of the name the ingredient's words account for; null for a search hit. */
  score: number | null
}

/** Which half of the catalogue is being searched. They answer different questions. */
export type CatalogueHalf = 'composition' | 'branded'

/** One key per CIQUAL / BLS food, so a ranked candidate and a search hit draw as the same row. */
function compositionKey(id: number): string {
  return `composition-${id}`
}

export function compositionResult(food: CompositionFood): CatalogueResult {
  return {
    key: compositionKey(food.id),
    compositionFoodId: food.id,
    foodItemId: null,
    name: compositionFoodName(food),
    note: food.sourceLabel,
    code: null,
    kcalPer100: food.nutrition?.energyKcal ?? null,
    score: null,
  }
}

/** A candidate ranked by name alone: it carries no figures, so it shows none. */
export function suggestionResult(suggestion: FoodSuggestion): CatalogueResult {
  return {
    key: compositionKey(suggestion.compositionFoodId),
    compositionFoodId: suggestion.compositionFoodId,
    foodItemId: null,
    name: suggestion.name,
    note: suggestion.sourceLabel,
    code: null,
    kcalPer100: null,
    score: suggestion.score,
  }
}

export function brandedResult(item: FoodItem): CatalogueResult {
  return {
    key: `item-${item.id}`,
    compositionFoodId: null,
    foodItemId: item.id,
    name: item.commercialName ?? item.legalName ?? `EAN ${item.ean ?? item.id}`,
    note: item.brand,
    code: item.ean,
    kcalPer100: item.energyKcal,
    score: null,
  }
}
