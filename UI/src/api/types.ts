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

/** One published figure as it left the source: the number and its own unit. */
export interface Nutrient {
  value: number
  unit: string
}

/**
 * A generic food of the composition database — the half a diet is normally
 * written in. `nutrition` is the published figures converted to one set of
 * units, per 100 g; it is derived on the way out and never stored, which is why
 * `nutrients` keeps each figure beside the unit it was published in.
 */
export interface BedcaFood {
  id: number
  name: string
  englishName: string | null
  scientificName: string | null
  foodGroup: string | null
  foodSubgroup: string | null
  origin: string | null
  ediblePortion: number | null
  nutrients: Record<string, Nutrient>
  nutrition: Nutrition
}

/**
 * A branded product of the other half of the catalogue, per 100 g.
 *
 * Only the fields the catalogue search shows are declared. `FoodItemDto` carries
 * twenty more, most of them null until a richer export of fooddata.csv is
 * imported, and none of them are read here.
 */
export interface FoodItem {
  id: number
  ean: string | null
  commercialName: string | null
  brand: string | null
  legalName: string | null
  energyKcal: number | null
  proteinsG: number | null
  fatG: number | null
  carbohydratesG: number | null
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
  /**
   * The cell as it was written, when the backend has it. Reading a sentence
   * into a name and quantities cannot be undone, so this is the only faithful
   * text there is; null for a dish stored before the column existed, and the
   * grid falls back to rebuilding one.
   */
  rawText: string | null
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

/* ---------------------------------------------------------------------------
 * The journal — the patient's side of the plan.
 *
 * The week says what to eat. These say what was thought of it and what was
 * eaten beside it, and they are the only rows a patient writes.
 * ------------------------------------------------------------------------- */

/**
 * What the patient thought of one plate, 1–5.
 *
 * **It names a slot, not a dish.** `PUT /api/diets/{id}` replaces the whole
 * week, so every dish id is new after any publish; a score keyed on one would
 * be lost each time the nutritionist edited a single cell. The place in the
 * week is what survives.
 */
export interface DishScore {
  day: DayOfWeek
  mealType: MealType
  dishIndex: number
  score: number
  scoredAt: string
}

/** Something eaten that the plan did not prescribe. */
export interface ExtraFood {
  id: number
  day: DayOfWeek
  /** What the patient wrote, kept whether or not anything matched it. */
  name: string
  quantity: number
  unit: string
  bedcaFoodId: number | null
  foodItemId: number | null
  /** What the catalogue calls the food, once matched. */
  matchedName: string | null
  /** The maker, when the match came from the branded half. */
  brand: string | null
  /** Scaled to the quantity logged; null when unmatched or unweighable. */
  nutrition: Nutrition | null
  loggedAt: string
}

export interface DayExtras {
  day: DayOfWeek
  extras: ExtraFood[]
  nutrition: NutritionSummary
}

/**
 * A whole week's journal in one answer.
 *
 * `averageScore` is null while nothing has been scored — nobody rated the week
 * badly, they have not rated it — and travels with `scored`, the number of
 * plates it was worked out over.
 */
export interface DietJournal {
  dietId: number
  scores: DishScore[]
  days: DayExtras[]
  averageScore: number | null
  scored: number
}
