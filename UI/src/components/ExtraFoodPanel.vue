<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { catalogueApi } from '@/api/catalogue'
import { referenceApi } from '@/api/reference'
import type { DayOfWeek, FoodMeasure } from '@/api/types'
import { usePatientWeek } from '@/stores/patientWeek'
import { useReference } from '@/stores/reference'
import { integer, NO_VALUE } from '@/domain/format'
import { measureSource, perMeasure } from '@/domain/rations'
import {
  brandedResult,
  compositionResult,
  type CatalogueHalf,
  type CatalogueResult,
} from '@/domain/catalogueResult'

/**
 * Adding something the plan did not prescribe.
 *
 * **The branded half is asked first here, the reverse of the week.** A diet says
 * "lechuga" and the composition database is full of exactly that; a patient
 * logging an extra is usually holding a wrapper with an EAN on it. Both halves
 * are offered, because an apple has no barcode either.
 *
 * A food may also be logged with nothing matched at all. The entry is kept as
 * written and counts towards nothing, which is the honest answer for a slice of
 * someone's birthday cake — and the same rule the week already follows for an
 * ingredient nobody could match.
 */

const props = defineProps<{
  day: DayOfWeek
  dayName: string
  /** The mobile bottom sheet, where the targets have to be fingers. */
  sheet?: boolean
}>()

const emit = defineEmits<{ close: [] }>()

const week = usePatientWeek()
const reference = useReference()
void reference.ensure().catch(() => undefined)

/** How long a pause in the typing means the query is ready to be run. */
const SEARCH_DEBOUNCE_MS = 300

/** Enough of the catalogue to choose from without becoming a list to read. */
const SEARCH_SIZE = 20

/** What a portion is when nobody said — the figure every label is written for. */
const DEFAULT_GRAMS = 100

const half = ref<CatalogueHalf>('branded')
const term = ref('')
const results = ref<CatalogueResult[]>([])
const searching = ref(false)
const searchError = ref<string | null>(null)

/** The food picked, waiting for a quantity before it is logged. */
const chosen = ref<CatalogueResult | null>(null)
const amount = ref(String(DEFAULT_GRAMS))
const unit = ref('g')

let timer: ReturnType<typeof setTimeout> | undefined
let token = 0

const trimmed = computed(() => term.value.trim())

const count = computed(() => {
  if (trimmed.value === '') {
    return 'Escribe para buscar en el catálogo'
  }
  if (searching.value) {
    return 'Buscando…'
  }
  return results.value.length === 1
    ? '1 resultado'
    : `${results.value.length} resultados en el catálogo`
})

async function run(query: string): Promise<void> {
  const mine = ++token
  searching.value = true
  searchError.value = null
  try {
    const page =
      half.value === 'branded'
        ? (await catalogueApi.branded(query, 0, SEARCH_SIZE)).content.map(brandedResult)
        : (await catalogueApi.composition(query, 0, SEARCH_SIZE)).content.map(compositionResult)
    // The term may have moved on while the request was in flight.
    if (mine === token) {
      results.value = page
    }
  } catch (cause) {
    if (mine === token) {
      results.value = []
      searchError.value = `No se pudo buscar${cause instanceof Error ? `: ${cause.message}` : ''}`
    }
  } finally {
    if (mine === token) {
      searching.value = false
    }
  }
}

function schedule(): void {
  clearTimeout(timer)
  chosen.value = null
  if (trimmed.value === '') {
    token++
    results.value = []
    searching.value = false
    return
  }
  timer = setTimeout(() => void run(trimmed.value), SEARCH_DEBOUNCE_MS)
}

watch(term, schedule)
watch(half, () => {
  if (trimmed.value !== '') {
    clearTimeout(timer)
    void run(trimmed.value)
  }
})

onBeforeUnmount(() => clearTimeout(timer))

function choose(candidate: CatalogueResult): void {
  chosen.value = candidate
  amount.value = String(DEFAULT_GRAMS)
  unit.value = 'g'
}

const parsedAmount = computed(() => Number(amount.value.replace(',', '.')))

/*
 * A generic food logged in a household measure — "1 cucharada" of oil — is
 * weighed the way the plan weighs the same spoon: by a measure row somebody
 * published, or the diet's own criterion. The rows that fit are offered; one
 * picked is sent, and none picked lets the backend attach one only when the
 * choice is not a judgement.
 */
const measures = ref<FoodMeasure[]>([])
const pickedMeasure = ref<number | null>(null)
let measureToken = 0

const measureWord = computed(() => reference.measureOfUnit(unit.value))

watch([chosen, measureWord], async ([food, word]) => {
  const mine = ++measureToken
  pickedMeasure.value = null
  measures.value = []
  const plan = week.diet.value
  if (!food || food.compositionFoodId === null || !word || !plan) {
    return
  }
  try {
    const rows = await referenceApi.measures({ compositionFoodId: food.compositionFoodId }, {
      unit: unit.value.trim(),
      dietId: plan.id,
      profile: plan.referenceProfileCode,
    })
    if (mine === measureToken) {
      measures.value = rows.filter((row) => row.gramsPerMeasure !== null)
      if (measures.value.length === 1) {
        pickedMeasure.value = measures.value[0].id
      }
    }
  } catch {
    // No measures is an honest answer: the entry is kept, and counts nothing.
  }
})

/** What one measure picked weighs, so the preview can be worked out before logging. */
const gramsPerUnit = computed(() => {
  if (measureWord.value === null) {
    return unit.value.trim().toLowerCase() === 'unidad' ? null : 1
  }
  return measures.value.find((row) => row.id === pickedMeasure.value)?.gramsPerMeasure ?? null
})

const valid = computed(
  () => Number.isFinite(parsedAmount.value) && parsedAmount.value > 0 && unit.value.trim() !== '',
)

/** What this portion comes to, before it is committed — the label figure scaled. */
const preview = computed(() => {
  const food = chosen.value
  if (!food || food.kcalPer100 === null || !valid.value || gramsPerUnit.value === null) {
    return null
  }
  return (food.kcalPer100 * parsedAmount.value * gramsPerUnit.value) / 100
})

async function add(): Promise<void> {
  const food = chosen.value
  if (!food || !valid.value) {
    return
  }
  const done = await week.logExtra({
    day: props.day,
    name: food.name,
    quantity: parsedAmount.value,
    unit: unit.value.trim(),
    compositionFoodId: food.compositionFoodId,
    foodItemId: food.foodItemId,
    foodMeasureId: measureWord.value === null ? null : pickedMeasure.value,
  })
  if (done) {
    chosen.value = null
    term.value = ''
    emit('close')
  }
}

/** Logging something no catalogue carries, exactly as it was typed. */
async function addAsWritten(): Promise<void> {
  const name = trimmed.value
  if (name === '') {
    return
  }
  const done = await week.logExtra({
    day: props.day,
    name,
    quantity: DEFAULT_GRAMS,
    unit: 'g',
  })
  if (done) {
    term.value = ''
    emit('close')
  }
}
</script>

<template>
  <div class="panel" :class="{ sheet }">
    <div class="head">
      <span class="title">Añadir comida extra</span>
      <span class="day">{{ dayName }}</span>
      <button class="close" type="button" aria-label="Cerrar" @click="emit('close')">
        <svg
          width="15"
          height="15"
          viewBox="0 0 14 14"
          fill="none"
          stroke="currentColor"
          stroke-width="1.4"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" />
        </svg>
      </button>
    </div>

    <div class="halves" role="tablist">
      <button
        class="tab"
        :class="{ current: half === 'branded' }"
        type="button"
        role="tab"
        :aria-selected="half === 'branded'"
        @click="half = 'branded'"
      >
        Productos
      </button>
      <button
        class="tab"
        :class="{ current: half === 'composition' }"
        type="button"
        role="tab"
        :aria-selected="half === 'composition'"
        @click="half = 'composition'"
      >
        Genéricos
      </button>
    </div>

    <div class="search">
      <div class="field">
        <svg
          width="14"
          height="14"
          viewBox="0 0 14 14"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <circle cx="6.2" cy="6.2" r="4" />
          <path d="M9.2 9.2l2.6 2.6" />
        </svg>
        <input
          v-model="term"
          type="search"
          placeholder="Buscar en el catálogo"
          aria-label="Buscar un alimento"
        />
      </div>
      <p class="count">{{ searchError ?? count }}</p>
    </div>

    <div class="results scroll">
      <button
        v-for="candidate in results"
        :key="candidate.key"
        class="result"
        :class="{ current: chosen?.key === candidate.key }"
        type="button"
        @click="choose(candidate)"
      >
        <span class="result-top">
          <span class="result-note">{{ candidate.note ?? '' }}</span>
          <span v-if="candidate.code" class="num result-code">EAN {{ candidate.code }}</span>
        </span>
        <span class="result-name">{{ candidate.name }}</span>
        <span class="result-foot">
          <span class="num result-kcal">
            {{ candidate.kcalPer100 === null ? NO_VALUE : integer(candidate.kcalPer100) }}
            kcal / 100 g
          </span>
        </span>
      </button>

      <p v-if="trimmed !== '' && !searching && results.length === 0" class="empty">
        El catálogo no tiene nada con ese nombre. Puedes añadirlo tal cual: quedará en el registro
        del día, sin figuras, hasta que se vincule a un alimento.
      </p>
    </div>

    <!-- The quantity is asked for rather than assumed: 100 g is the figure a
         label is written for, not the amount anyone ate. -->
    <div v-if="chosen" class="commit">
      <span class="commit-name">{{ chosen.name }}</span>
      <div class="commit-row">
        <input
          v-model="amount"
          class="num amount"
          inputmode="decimal"
          aria-label="Cantidad"
          @keyup.enter="add()"
        />
        <input v-model="unit" class="unit" aria-label="Unidad" @keyup.enter="add()" />
        <span class="preview num">
          {{ preview === null ? '' : `${integer(preview)} kcal` }}
        </span>
        <button class="add" type="button" :disabled="!valid || week.saving.value" @click="add()">
          Añadir
        </button>
      </div>
      <!-- Written in a household measure: which published weight stands for it. -->
      <div v-if="chosen.compositionFoodId !== null && measureWord !== null" class="measures">
        <button
          v-for="measure in measures"
          :key="measure.id"
          class="measure"
          :class="{ on: pickedMeasure === measure.id }"
          type="button"
          :title="[measure.note, measure.pageRef].filter(Boolean).join(' · ')"
          @click="pickedMeasure = pickedMeasure === measure.id ? null : measure.id"
        >
          {{ perMeasure(measure) }}
          <span class="measure-source">{{ measureSource(measure) }}</span>
        </button>
        <p v-if="measures.length === 0" class="measure-note">
          Ninguna fuente publica cuánto pesa «{{ unit.trim() }}» de este alimento: se guardará sin
          cifras. Escríbelo en gramos si los sabes.
        </p>
        <p v-else-if="pickedMeasure === null" class="measure-note">
          Elige cuál pesa esta medida, o se guardará sin cifras.
        </p>
      </div>
    </div>

    <button
      v-else-if="trimmed !== '' && !searching"
      class="as-written"
      type="button"
      :disabled="week.saving.value"
      @click="addAsWritten()"
    >
      Añadir «{{ trimmed }}» sin vincular
    </button>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.measures {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.measure {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  padding: 5px 9px;
  font-size: 11.5px;
  color: var(--ink);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
}

.measure.on {
  border-color: var(--sage-600);
  background: var(--sage-50);
}

.measure-source,
.measure-note {
  font-size: 10.5px;
  color: var(--ink-muted);
}

.measure-note {
  margin: 0;
  width: 100%;
}

.panel.sheet {
  border: 0;
  border-radius: 14px 14px 0 0;
  max-height: 78vh;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex: none;
  padding: 13px 15px;
  border-bottom: 1px solid var(--line-soft);
}

.sheet .head {
  padding: 16px 16px 12px;
}

.title {
  font-weight: 600;
  font-size: 12.5px;
  color: var(--ink-strong);
}

.sheet .title {
  font-size: 15px;
}

.day {
  font-size: 11px;
  color: var(--ink-faint);
}

.close {
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  color: var(--ink-faint);
}

.sheet .close {
  width: 44px;
  height: 44px;
  margin-right: -10px;
  padding: 0;
}

.halves {
  display: flex;
  gap: 2px;
  flex: none;
  margin: 12px 15px 0;
  padding: 2px;
  background: #f1f4f2;
  border-radius: 5px;
}

.tab {
  flex: 1 1 0;
  height: 28px;
  border-radius: var(--radius);
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ink-muted);
}

.sheet .tab {
  height: 36px;
  font-size: 13px;
}

.tab.current {
  background: var(--surface);
  color: var(--ink-strong);
  box-shadow: 0 1px 2px rgba(22, 28, 24, 0.1);
}

.search {
  flex: none;
  padding: 10px 15px 8px;
}

.field {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
  color: var(--ink-muted);
}

.sheet .field {
  height: 44px;
  border-radius: 8px;
}

.field input {
  flex: 1 1 0;
  min-width: 0;
  border: 0;
  outline: none;
  font-size: 12.5px;
  background: transparent;
}

.sheet .field input {
  font-size: 14px;
}

.count {
  margin: 8px 0 0;
  font-size: 10.5px;
  color: var(--ink-faint);
}

.results {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 0 15px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.result {
  display: block;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  text-align: left;
  background: var(--surface);
}

.sheet .result {
  padding: 11px 13px;
  border-radius: 8px;
}

.result:hover {
  border-color: var(--line-input);
  background: var(--sage-50);
}

.result.current {
  border-color: var(--sage-200);
  background: var(--sage-50);
}

.result-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.result-note {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--extra-ink);
}

.result-code {
  margin-left: auto;
  font-size: 10px;
  color: var(--ink-faint);
}

.result-name {
  display: -webkit-box;
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.3;
  color: var(--ink-strong);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sheet .result-name {
  font-size: 12.5px;
}

.result-foot {
  display: flex;
  align-items: center;
  margin-top: 6px;
}

.result-kcal {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ink);
}

.empty {
  margin: 4px 2px;
  font-size: 11.5px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.commit {
  flex: none;
  padding: 11px 15px 13px;
  border-top: 1px solid var(--line-soft);
  background: var(--surface-muted);
}

.commit-name {
  display: block;
  font-size: 11.5px;
  line-height: 1.3;
  color: var(--ink-strong);
}

.commit-row {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 9px;
}

.amount,
.unit {
  height: 32px;
  padding: 0 9px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 12.5px;
}

.amount {
  width: 68px;
  text-align: right;
}

.unit {
  width: 62px;
}

.preview {
  flex: 1 1 0;
  min-width: 0;
  font-size: 11.5px;
  color: var(--ink-muted);
}

.add {
  flex: none;
  height: 32px;
  padding: 0 14px;
  border-radius: var(--radius);
  font-weight: 500;
  font-size: 12px;
  color: var(--surface);
  background: var(--sage-700);
}

.sheet .add {
  height: 44px;
  border-radius: 8px;
  font-size: 13.5px;
}

.add:disabled {
  background: #c2ccc6;
}

.as-written {
  flex: none;
  margin: 0 15px 14px;
  height: 34px;
  border: 1px dashed var(--extra-dash);
  border-radius: var(--radius);
  font-size: 11.5px;
  font-weight: 500;
  color: var(--extra-ink);
}

.as-written:disabled {
  color: var(--ink-disabled);
  border-color: var(--line);
}
</style>
