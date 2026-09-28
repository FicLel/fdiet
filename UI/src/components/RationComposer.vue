<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { catalogueApi } from '@/api/catalogue'
import { dietsApi } from '@/api/diets'
import { referenceApi } from '@/api/reference'
import type { BedcaFood, FoodMeasure, FoodState, Ration } from '@/api/types'
import {
  amount,
  measureSource,
  measureText,
  rationIsFixed,
  rationWeight,
  stateWord,
} from '@/domain/rations'

/**
 * Writes a food into the cell from a ration or a household measure instead of
 * by hand: pick a food, pick "1 ración · 60–80 g en seco · AESAN 2022" or
 * "1 cucharada sopera · 10 ml", say how many, and the backend writes the text.
 *
 * **It appends text, nothing else.** The fragment is the backend's own writing
 * of the choice, and it is read back by the same parser as anything typed; the
 * editor never builds an ingredient of its own. A range asks for a value —
 * nothing picks the midpoint — and so does a gross weight, since what the diet
 * weighs is what is eaten.
 */

const props = defineProps<{
  dietId: number | null
  profileCode: string | null
}>()

const emit = defineEmits<{ append: [fragment: string] }>()

type Choice =
  | { kind: 'ration'; ration: Ration }
  | { kind: 'measure'; measure: FoodMeasure }
  | { kind: 'grams' }

const open = ref(false)
const term = ref('')
const results = shallowRef<BedcaFood[]>([])
const searching = ref(false)
const food = shallowRef<BedcaFood | null>(null)
const rations = shallowRef<Ration[]>([])
const measures = shallowRef<FoodMeasure[]>([])
const loadingOptions = ref(false)
const choice = shallowRef<Choice>({ kind: 'grams' })
const count = ref(1)
const grams = ref<number | null>(null)
const state = ref<FoodState | ''>('')
const busy = ref(false)
const error = ref<string | null>(null)
const added = ref<string | null>(null)

let timer: ReturnType<typeof setTimeout> | undefined
let token = 0

const STATES: { value: FoodState | ''; label: string }[] = [
  { value: '', label: 'Sin indicar' },
  { value: 'RAW', label: 'En crudo' },
  { value: 'DRY', label: 'En seco' },
  { value: 'COOKED', label: 'Cocinado' },
  { value: 'CANNED', label: 'En conserva' },
  { value: 'DRAINED', label: 'Escurrido' },
]

function search(value: string): void {
  term.value = value
  if (timer !== undefined) {
    clearTimeout(timer)
  }
  const query = value.trim()
  if (query === '') {
    token++
    results.value = []
    searching.value = false
    return
  }
  searching.value = true
  timer = setTimeout(async () => {
    const mine = ++token
    try {
      const page = await catalogueApi.bedca(query, 0, 8)
      if (mine === token) {
        results.value = page.content
      }
    } catch {
      if (mine === token) {
        results.value = []
      }
    } finally {
      if (mine === token) {
        searching.value = false
      }
    }
  }, 300)
}

async function pick(chosen: BedcaFood): Promise<void> {
  food.value = chosen
  results.value = []
  term.value = ''
  error.value = null
  added.value = null
  rations.value = []
  measures.value = []
  choice.value = { kind: 'grams' }
  loadingOptions.value = true
  try {
    const [rationRows, measureRows] = await Promise.all([
      referenceApi.rations(chosen.id, props.profileCode),
      referenceApi.measures(chosen.id, {
        dietId: props.dietId ?? undefined,
        profile: props.profileCode,
      }),
    ])
    // Only the choices this food can still be asked of after a click.
    rations.value = rationRows.filter(
      (ration) => (ration.gramsMin ?? ration.mlMin) !== null,
    )
    measures.value = measureRows
    const first = rations.value[0]
    if (first) {
      select({ kind: 'ration', ration: first })
    } else if (measures.value.some((measure) => measure.gramsPerMeasure !== null)) {
      select({
        kind: 'measure',
        measure: measures.value.find((measure) => measure.gramsPerMeasure !== null)!,
      })
    }
  } catch (cause) {
    error.value = `No se pudieron leer sus raciones${cause instanceof Error ? `: ${cause.message}` : ''}`
  } finally {
    loadingOptions.value = false
  }
}

function select(next: Choice): void {
  choice.value = next
  count.value = 1
  grams.value = null
  if (next.kind === 'ration') {
    state.value = next.ration.state === 'UNSPECIFIED' ? '' : next.ration.state
    if (rationIsFixed(next.ration) && next.ration.weightBasis !== 'GROSS') {
      grams.value = next.ration.gramsMin ?? next.ration.mlMin
    }
  } else if (next.kind === 'measure') {
    state.value = next.measure.state === 'UNSPECIFIED' ? '' : next.measure.state
  }
}

function reset(): void {
  food.value = null
  rations.value = []
  measures.value = []
  choice.value = { kind: 'grams' }
  error.value = null
}

/** A ration that is a range or a gross weight needs the weight of one ration typed. */
const asksForWeight = computed(() => {
  const current = choice.value
  return (
    current.kind === 'grams' ||
    (current.kind === 'ration' &&
      (!rationIsFixed(current.ration) || current.ration.weightBasis === 'GROSS'))
  )
})

const weightHint = computed(() => {
  const current = choice.value
  if (current.kind !== 'ration') {
    return 'g'
  }
  const published = rationWeight(current.ration)
  return current.ration.weightBasis === 'GROSS'
    ? `g comestibles por ración (publicado: ${published})`
    : `g por ración (${published})`
})

const isMl = computed(
  () => choice.value.kind === 'ration' && choice.value.ration.gramsMin === null,
)

const total = computed<number | null>(() => {
  const current = choice.value
  if (current.kind === 'measure') {
    return current.measure.gramsPerMeasure === null
      ? null
      : current.measure.gramsPerMeasure * count.value
  }
  if (grams.value === null || grams.value <= 0) {
    return null
  }
  return current.kind === 'ration' ? grams.value * count.value : grams.value
})

const canAdd = computed(() => food.value !== null && !busy.value && total.value !== null && count.value > 0)

async function add(): Promise<void> {
  const chosen = food.value
  const current = choice.value
  if (!chosen || !canAdd.value) {
    return
  }
  busy.value = true
  error.value = null
  try {
    const composed = await dietsApi.compose(
      current.kind === 'measure'
        ? {
            bedcaFoodId: chosen.id,
            foodMeasureId: current.measure.id,
            count: count.value,
            state: state.value || null,
            dietId: props.dietId ?? undefined,
          }
        : {
            bedcaFoodId: chosen.id,
            grams: Math.round(total.value! * 100) / 100,
            state: state.value || null,
            dietId: props.dietId ?? undefined,
          },
    )
    emit('append', composed.fragment)
    added.value = composed.fragment
  } catch (cause) {
    error.value = `No se pudo añadir${cause instanceof Error ? `: ${cause.message}` : ''}`
  } finally {
    busy.value = false
  }
}

function isChosen(candidate: Choice): boolean {
  const current = choice.value
  if (current.kind !== candidate.kind) {
    return false
  }
  if (current.kind === 'ration' && candidate.kind === 'ration') {
    return current.ration.id === candidate.ration.id
  }
  if (current.kind === 'measure' && candidate.kind === 'measure') {
    return current.measure.id === candidate.measure.id
  }
  return true
}
</script>

<template>
  <div class="composer">
    <button v-if="!open" class="opener" type="button" @click="open = true">
      <svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">
        <path d="M7 3v8M3 7h8" />
      </svg>
      Añadir por raciones
    </button>

    <div v-else class="box">
      <div class="head">
        <span class="label">Añadir por raciones</span>
        <button class="link" type="button" @click="open = false; reset()">Cerrar</button>
      </div>

      <template v-if="!food">
        <input
          class="input"
          :value="term"
          placeholder="Buscar un alimento genérico"
          aria-label="Buscar un alimento genérico"
          @input="search(($event.target as HTMLInputElement).value)"
        />
        <p v-if="searching" class="hint">Buscando…</p>
        <ul v-else-if="results.length > 0" class="results">
          <li v-for="result in results" :key="result.id">
            <button class="result" type="button" @click="pick(result)">{{ result.name }}</button>
          </li>
        </ul>
        <p v-else-if="term.trim() !== ''" class="hint">Ningún alimento genérico se llama así.</p>
      </template>

      <template v-else>
        <div class="food">
          <span class="food-name">{{ food.name }}</span>
          <button class="link" type="button" @click="reset()">Cambiar</button>
        </div>

        <p v-if="loadingOptions" class="hint">Leyendo raciones y medidas…</p>

        <div v-else class="choices">
          <button
            v-for="ration in rations"
            :key="`r-${ration.id}`"
            class="choice"
            :class="{ on: isChosen({ kind: 'ration', ration }) }"
            type="button"
            :title="[ration.note, ration.pageRef].filter(Boolean).join(' · ')"
            @click="select({ kind: 'ration', ration })"
          >
            <span class="choice-main">1 ración · {{ rationWeight(ration) }}</span>
            <span class="choice-meta">{{ ration.groupLabel }} · {{ ration.sourceShortName }}</span>
          </button>

          <button
            v-for="measure in measures"
            :key="`m-${measure.id}`"
            class="choice"
            :class="{ on: isChosen({ kind: 'measure', measure }) }"
            type="button"
            :disabled="measure.gramsPerMeasure === null"
            :title="
              measure.gramsPerMeasure === null
                ? 'Un rango no pesa: escribe los gramos'
                : [measure.note, measure.pageRef].filter(Boolean).join(' · ')
            "
            @click="select({ kind: 'measure', measure })"
          >
            <span class="choice-main">{{ measureText(measure) }}</span>
            <span class="choice-meta">
              {{ measureSource(measure) }}
              <template v-if="measure.gramsPerMeasure === null"> · rango, no pesa</template>
            </span>
          </button>

          <button
            class="choice"
            :class="{ on: choice.kind === 'grams' }"
            type="button"
            @click="select({ kind: 'grams' })"
          >
            <span class="choice-main">Gramos</span>
            <span class="choice-meta">Escribir el peso</span>
          </button>
        </div>

        <p v-if="!loadingOptions && rations.length === 0 && measures.length === 0" class="hint">
          Ninguna fuente cargada publica una ración o una medida para este alimento.
        </p>

        <div class="line">
          <label v-if="choice.kind !== 'grams'" class="mini">
            <span>Cuántas</span>
            <input v-model.number="count" class="input num" type="number" min="0.25" step="0.25" />
          </label>
          <label v-if="asksForWeight" class="mini grow">
            <span>{{ weightHint }}</span>
            <input v-model.number="grams" class="input num" type="number" min="1" step="1" />
          </label>
          <label class="mini">
            <span>Estado</span>
            <select v-model="state" class="input">
              <option v-for="option in STATES" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>

        <p class="hint">
          <template v-if="total !== null">
            Se escribirá {{ amount(total, 1) }} {{ isMl ? 'ml (leídos como g)' : 'g' }}
            {{ stateWord(state || null) }}.
          </template>
          <template v-else>Indica un peso para poder añadirlo.</template>
        </p>

        <button class="add" type="button" :disabled="!canAdd" @click="add()">
          {{ busy ? 'Añadiendo…' : 'Añadir al plato' }}
        </button>
      </template>

      <p v-if="added" class="done">Añadido: {{ added }}</p>
      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>
</template>

<style scoped>
.composer {
  margin-top: 8px;
}

.opener {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  height: 34px;
  border: 1px dashed var(--line-input);
  border-radius: var(--radius);
  font-size: 12px;
  font-weight: 500;
  color: var(--sage-700);
}

.box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-muted);
}

.head,
.food {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.link {
  margin-left: auto;
  font-size: 11px;
  color: var(--sage-700);
}

.food-name {
  font-weight: 600;
  font-size: 12.5px;
  color: var(--ink-strong);
}

.input {
  width: 100%;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 12px;
  color: var(--ink-strong);
}

.results {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.result {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  background: var(--surface);
  text-align: left;
  font-size: 11.5px;
  color: var(--ink-strong);
}

.result:hover {
  border-color: var(--sage-200);
  background: var(--sage-50);
}

.choices {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.choice {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 6px 8px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  background: var(--surface);
  text-align: left;
}

.choice.on {
  border-color: var(--sage-600);
  background: var(--sage-50);
}

.choice:disabled {
  opacity: 0.6;
}

.choice-main {
  font-size: 11.5px;
  color: var(--ink-strong);
}

.choice-meta {
  font-size: 10.5px;
  color: var(--ink-faint);
}

.line {
  display: flex;
  gap: 6px;
}

.mini {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 84px;
  font-size: 10.5px;
  color: var(--ink-muted);
}

.mini.grow {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
}

.mini span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.add {
  height: 32px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--surface);
  background: var(--sage-700);
}

.add:disabled {
  background: #c2ccc6;
}

.done {
  margin: 0;
  font-size: 11px;
  color: var(--sage-700);
}

.error {
  margin: 0;
  font-size: 11px;
  color: var(--amber-700);
}
</style>
