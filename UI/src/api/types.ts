/**
 * The transport shapes of the Java backend, one record per DTO.
 * `BigDecimal` crosses as a JSON number; a figure the backend could not work
 * out crosses as null, which is not the same as zero and is never coerced to it.
 */

export type DietStatus = 'ACTIVE' | 'ARCHIVED'

export type MealType =
  | 'BREAKFAST'
  | 'MORNING_SNACK'
  | 'LUNCH'
  | 'AFTERNOON_SNACK'
  | 'DINNER'

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

export interface Nutrition {
  energyKcal: number | null
  proteinG: number | null
  fatG: number | null
  saturatedFatG: number | null
  carbohydratesG: number | null
  sugarsG: number | null
  fiberG: number | null
  sodiumMg: number | null
}

/** A total never travels without the counts it was worked out over. */
export interface NutritionSummary {
  totals: Nutrition
  ingredients: number
  counted: number
  unmatched: number
  unmeasured: number
}

export interface FoodSuggestion {
  bedcaFoodId: number
  name: string
  foodGroup: string | null
  score: number
}

export interface DishIngredient {
  id: number | null
  /** What the diet calls the food, exactly as written. */
  name: string
  quantity: number
  unit: string
  foodItemId: number | null
  bedcaFoodId: number | null
  /** The catalogue's own name for the food, once matched. */
  matchedName: string | null
  nutrition: Nutrition | null
  suggestions: FoodSuggestion[] | null
}

export interface Dish {
  name: string
  ingredients: DishIngredient[]
}

export interface Meal {
  type: MealType
  /** The label the diet was written with — "Desayuno", "Comida". */
  name: string
  dishes: Dish[]
  nutrition: NutritionSummary | null
}

export interface DietDay {
  day: DayOfWeek
  meals: Meal[]
  nutrition: NutritionSummary | null
}

export interface Diet {
  id: number
  name: string
  status: DietStatus
  startedOn: string
  endedOn: string | null
  days: DietDay[]
  nutrition: NutritionSummary | null
}

export interface DietSummary {
  id: number
  name: string
  status: DietStatus
  startedOn: string
  endedOn: string | null
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
