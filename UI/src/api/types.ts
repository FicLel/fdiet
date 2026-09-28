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

/**
 * Somebody a diet is written for.
 *
 * **Not an account.** The backend has no security layer, so this authenticates
 * nothing and hides nothing: switching patient changes whose week is on screen,
 * and every patient's week is reachable from every screen. It is a division of
 * data, not of access.
 */
export interface Patient {
  id: number
  name: string
  notes: string | null
  /**
   * Optional, and only ever used to *suggest* which reference profile a new
   * diet is read against. Null is a patient nobody gave one, not an error.
   */
  birthDate: string | null
  sex: Sex | null
  createdAt: string
}

export type Sex = 'FEMALE' | 'MALE'

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
  /** Of `counted`, how many were weighed through a household measure rather than written in grams. */
  countedByMeasure: number
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
  /**
   * The upper end when the text gave a range — `2-3 nueces`, `(40-60 gr)` —
   * with `quantity` the lower one; null for a single value. A range is counted
   * nowhere until a person confirms one value.
   */
  quantityMax: number | null
  unit: string
  /** The state the text says the food is weighed in — `lentejas cocidas` — or null when it says none. */
  state: FoodState | null
  /** The size the text names — `1 kiwi mediano` — or null. */
  size: PortionSize | null
  foodItemId: number | null
  bedcaFoodId: number | null
  /** The household measure that weighs `1 cdta`, when one is attached. */
  foodMeasureId: number | null
  /** The catalogue's own name for the food, once matched. */
  matchedName: string | null
  measure: FoodMeasure | null
  /** Written raw and matched to a cooked food, or the other way round. */
  stateMismatch: boolean
  /** On a state mismatch, what the quantity weighs in the food's state by a published yield. Offered, never applied. */
  yieldHint: YieldHint | null
  nutrition: Nutrition | null
  suggestions: FoodSuggestion[] | null
}

/**
 * A published cooking yield read against one ingredient: `150 g en crudo ≈ 108 g
 * cocinado`. `methodNamed` false means the source has no row for the method
 * written and this is the nearest it publishes.
 */
export interface YieldHint {
  writtenState: FoodState
  foodState: FoodState
  writtenGrams: number | null
  equivalentGrams: number | null
  yieldPct: number
  foodLabel: string
  method: string
  methodNamed: boolean
  sourceShortName: string
  pageRef: string
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
  /** Whose week this is. Every diet belongs to exactly one patient. */
  patientId: number
  /** Their name, so a heading never has to fetch a patient to write itself. */
  patientName: string
  name: string
  status: DietStatus
  startedOn: string
  endedOn: string | null
  /** The population the week is read against, or null for none. It never rewrites a gram. */
  referenceProfileCode: string | null
  /** Clinical diets also count carbohydrate rations (10 g). */
  clinical: boolean
  days: DietDay[]
  nutrition: NutritionSummary | null
}

export interface DietSummary {
  id: number
  patientId: number
  patientName: string
  name: string
  status: DietStatus
  startedOn: string
  endedOn: string | null
  referenceProfileCode: string | null
  clinical: boolean
}

/** What an import read out of a workbook, and how much of it matched a food. */
export interface DietImportSummary {
  dietId: number
  sheet: string
  name: string
  days: number
  meals: number
  dishes: number
  ingredients: number
  resolved: number
  unresolved: number
}

/* ---------------------------------------------------------------------------
 * Reference data — rations, household measures and recommendations, each row
 * with the source and page it was taken from.
 *
 * Everything here is orientative and derived on read. The UI formats what the
 * backend sends and never computes a ration count of its own.
 * ------------------------------------------------------------------------- */

export type FoodState = 'RAW' | 'DRY' | 'COOKED' | 'CANNED' | 'DRAINED' | 'UNSPECIFIED'

export type PortionSize = 'SMALL' | 'MEDIUM' | 'LARGE'

export type WeightBasis = 'NET_EDIBLE' | 'GROSS' | 'UNSPECIFIED'

export type HouseholdMeasure =
  | 'UNIDAD'
  | 'CUCHARADA_SOPERA'
  | 'CUCHARADA_POSTRE'
  | 'CUCHARADITA'
  | 'VASO'
  | 'TAZA'
  | 'TAZON'
  | 'PLATO'
  | 'CAZO'
  | 'LONCHA'
  | 'REBANADA'
  | 'RODAJA'
  | 'FILETE'
  | 'PUNADO'
  | 'DIENTE'
  | 'PORCION'
  | 'LATA'
  | 'RACION'

export type RecommendationPeriod = 'PER_DAY' | 'PER_WEEK'

export type RecommendationStatus = 'WITHIN' | 'BELOW' | 'ABOVE' | 'UNCERTAIN'

export interface ReferenceSource {
  code: string
  shortName: string
  title: string
  institution: string | null
  country: string | null
  tier: number
  year: number | null
  url: string | null
  licenceClass: 'A' | 'B' | 'C' | 'D' | 'E'
  licence: string | null
  /** The line a figure from this source may not be shown without. */
  attribution: string | null
  clinical: boolean
  retrievedOn: string | null
  notes: string | null
}

export interface ReferenceProfile {
  code: string
  label: string
  sourceCode: string
  sourceShortName: string
  ageMinMonths: number | null
  ageMaxMonths: number | null
  context: string | null
  selectable: boolean
  /** The one that fits the age asked about. An offer, never applied on its own. */
  suggested: boolean
}

/**
 * A household measure and what it weighs: `1 cucharada sopera · 10 ml · AESAN 2022`.
 * A range (`53–63 g`) weighs nothing and is never attached on its own.
 */
export interface FoodMeasure {
  id: number
  code: string | null
  measure: HouseholdMeasure
  measureLabel: string
  size: PortionSize | null
  count: number
  foodLabel: string | null
  bedcaFoodId: number | null
  foodCategory: string | null
  keywords: string | null
  gramsMin: number | null
  gramsMax: number | null
  mlMin: number | null
  mlMax: number | null
  /** Null for a range: nothing picks a midpoint. */
  gramsPerMeasure: number | null
  state: FoodState
  weightBasis: WeightBasis
  grossGrams: number | null
  householdText: string | null
  pageRef: string | null
  note: string | null
  sourceCode: string | null
  sourceShortName: string | null
  sourceTier: number | null
  dietId: number | null
  /** The nutritionist's own criterion for this diet, not a published row. */
  dietOwn: boolean
}

export interface Ration {
  id: number
  code: string
  profileCode: string
  profileLabel: string
  sourceCode: string
  sourceShortName: string
  groupCode: string
  groupLabel: string
  foodCategory: string | null
  keywords: string | null
  foodLabel: string | null
  bedcaFoodId: number | null
  role: string | null
  gramsMin: number | null
  gramsMax: number | null
  mlMin: number | null
  mlMax: number | null
  unitsMin: number | null
  unitsMax: number | null
  state: FoodState
  weightBasis: WeightBasis
  householdText: string | null
  grossGrams: number | null
  pageRef: string | null
  note: string | null
}

export interface MealShare {
  mealType: MealType
  pctMin: number
  pctMax: number
  note: string | null
}

/** A profile's energy split between meals, and where it was taken from when borrowed. */
export interface MealShares {
  fromProfileCode: string
  fromProfileLabel: string
  sourceCode: string
  sourceShortName: string
  /** Taken from another population's document, and labelled as such. */
  borrowed: boolean
  note: string | null
  pageRef: string | null
  shares: MealShare[]
}

export interface ExchangeSystem {
  code: string
  name: string
  nutrient: 'CARBOHYDRATE' | 'PROTEIN' | 'FAT'
  gramsPerUnit: number
  clinical: boolean
  sourceCode: string
  sourceShortName: string
  note: string | null
}

export interface GroupCount {
  groupCode: string
  groupLabel: string
  grams: number
  rationsMin: number
  rationsMax: number
  ingredients: number
}

export interface RecommendationCheck {
  code: string
  label: string
  groupCodes: string[]
  rationsMin: number | null
  rationsMax: number | null
  period: RecommendationPeriod
  actualMin: number
  actualMax: number
  status: RecommendationStatus
  note: string | null
  pageRef: string | null
}

export interface MealEnergy {
  mealType: MealType
  name: string
  kcal: number | null
  pct: number | null
  targetPctMin: number | null
  targetPctMax: number | null
  carbohydratesG: number | null
}

export interface ExchangeCount {
  code: string
  name: string
  gramsPerUnit: number
  dayUnits: number | null
  meals: { mealType: MealType; units: number | null }[]
  /** Per dish, addressed the way the journal addresses a plate. `complete` is false while a part of it was not counted. */
  dishes: { mealType: MealType; dishIndex: number; name: string; units: number | null; complete: boolean }[]
}

/** `counted + unmatched + unweighed + noRation + stateMismatch === ingredients`. */
export interface RationCoverage {
  ingredients: number
  counted: number
  unmatched: number
  unweighed: number
  noRation: number
  stateMismatch: number
}

export interface DayRations {
  day: DayOfWeek
  groups: GroupCount[]
  daily: RecommendationCheck[]
  meals: MealEnergy[]
  exchanges: ExchangeCount[]
  coverage: RationCoverage
  uncounted: { name: string; reason: string }[]
}

export interface DietRations {
  dietId: number
  profile: {
    code: string
    label: string
    sourceCode: string
    sourceShortName: string
    context: string | null
  } | null
  mealShares: MealShares | null
  exchangeSystems: ExchangeSystem[]
  days: DayRations[]
  weekly: RecommendationCheck[]
  daysInWeek: number
  sources: ReferenceSource[]
}

/** A food written as text the parser reads back, and what it read. */
export interface ComposedFragment {
  fragment: string
  ingredient: DishIngredient
}

export interface DietMeasureSaved {
  measure: FoodMeasure
  /** How many of the diet's ingredients now weigh through it. */
  attached: number
}

export interface HouseholdMeasureWord {
  code: HouseholdMeasure
  label: string
  aliases: string[]
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
  state: FoodState | null
  size: PortionSize | null
  bedcaFoodId: number | null
  foodItemId: number | null
  /** What the catalogue calls the food, once matched. */
  matchedName: string | null
  /** The maker, when the match came from the branded half. */
  brand: string | null
  /** The household measure that weighed `1 cucharada`, the same rule the week weighs by. */
  foodMeasureId: number | null
  measure: FoodMeasure | null
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
