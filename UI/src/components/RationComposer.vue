<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { dietsApi } from '@/api/diets'
import { referenceApi } from '@/api/reference'
import type { BedcaFood, FoodMeasure, FoodState, Ration } from '@/api/types'
import {
  asksForWeight as needsWeight,
  choiceTotal,
  GRAMS_CHOICE,
  rationRange,
  rationStart,
  weightHint as hintFor,
  writesMl,
  type ComposerChoice,
} from '@/domain/composerChoice'
import { criterionFor, unitsWeight, weighs } from '@/domain/measureCriteria'
import { amount, stateWord } from '@/domain/rations'
import BedcaFoodSearch from './BedcaFoodSearch.vue'
import ComposerChoices from './ComposerChoices.vue'
import MeasureCriteriaList from './MeasureCriteriaList.vue'
import MeasureCriterionForm from './MeasureCriterionForm.vue'

/**
 * Writes a food into the cell from a ration or a household measure instead of
 * by hand: pick a food, pick "1 ración · 60–80 g en seco · AESAN 2022" or
 * "1 unidad mediana · 58 g · Tu criterio", say how many, and the backend
 * writes the text.
 *
 * **It appends text, nothing else.** The fragment is the backend's own writing
 * of the choice, and it is read back by the same parser as anything typed; the
 * editor never builds an ingredient of its own. A ration that is a range asks
 * for a value, and so does a gross weight, since what the diet weighs is what
 * is eaten. A measure that is a range asks once for the nutritionist's weight
 * per unit, kept as her criterion; from then on it is written in units.
 *
 * `dietId` is null for a diet not yet saved and in the recipe library: the
 * global criteria weigh there too.
 */

const props = defineProps<{
  dietId: number | null
  profileCode: string | null
}>()

const emit = defineEmits<{ append: [fragment: string] }>()

/** What the criterion form is open for: a published range, or a new unit (`from` null). */
type Asking = { from: FoodMeasure | null } | null

const open = ref(false)
const food = shallowRef<BedcaFood | null>(null)
const rations = shallowRef<Ration[]>([])
const measures = shallowRef<FoodMeasure[]>([])
const loadingOptions = ref(false)
const choice = shallowRef<ComposerChoice>(GRAMS_CHOICE)
const asking = shallowRef<Asking>(null)
const count = ref(1)
const grams = ref<number | null>(null)
/** The weight shown is the middle of a published range, not yet one she typed. */
const proposed = ref(false)
const state = ref<FoodState | ''>('')
const busy = ref(false)
const error = ref<string | null>(null)
const added = ref<string | null>(null)

const STATES: { value: FoodState | ''; label: string }[] = [
  { value: '', label: 'Sin indicar' },
  { value: 'RAW', label: 'En crudo' },
  { value: 'DRY', label: 'En seco' },
  { value: 'COOKED', label: 'Cocinado' },
  { value: 'CANNED', label: 'En conserva' },
  { value: 'DRAINED', label: 'Escurrido' },
]

const globalCriteria = computed(() => measures.value.filter((measure) => measure.globalOwn))

function loadMeasures(chosen: BedcaFood): Promise<FoodMeasure[]> {
  return referenceApi.measures(chosen.id, {
    dietId: props.dietId ?? undefined,
    profile: props.profileCode,
  })
}

async function pick(chosen: BedcaFood): Promise<void> {
  food.value = chosen
  error.value = null
  added.value = null
  rations.value = []
  measures.value = []
  asking.value = null
  choice.value = GRAMS_CHOICE
  loadingOptions.value = true
  try {
    const [rationRows, measureRows] = await Promise.all([
      referenceApi.rations(chosen.id, props.profileCode),
      loadMeasures(chosen),
    ])
    // Another food was picked, or the composer reset, while this one loaded.
    if (food.value !== chosen) {
      return
    }
    // Only the rations this food can still be asked of after a click.
    rations.value = rationRows.filter((ration) => (ration.gramsMin ?? ration.mlMin) !== null)
    measures.value = measureRows
    const first = rations.value[0]
    const weighing = measureRows.find(weighs)
    if (first) {
      select({ kind: 'ration', ration: first })
    } else if (weighing) {
      select({ kind: 'measure', measure: weighing })
    }
  } catch (cause) {
    if (food.value === chosen) {
      error.value = `No se pudieron leer sus raciones${cause instanceof Error ? `: ${cause.message}` : ''}`
    }
  } finally {
    if (food.value === chosen) {
      loadingOptions.value = false
    }
  }
}

/**
 * A range measure is answered by the criterion that already covers it, or asks
 * for one; anything else is chosen as it is.
 */
function select(next: ComposerChoice): void {
  if (next.kind === 'measure' && !weighs(next.measure)) {
    const criterion = criterionFor(measures.value, next.measure)
    if (!criterion) {
      asking.value = { from: next.measure }
      return
    }
    next = { kind: 'measure', measure: criterion }
  }
  asking.value = null
  choice.value = next
  count.value = 1
  grams.value = null
  proposed.value = false
  if (next.kind === 'ration') {
    state.value = next.ration.state === 'UNSPECIFIED' ? '' : next.ration.state
    const start = rationStart(next.ration)
    grams.value = start.grams
    proposed.value = start.proposed
  } else if (next.kind === 'measure') {
    state.value = next.measure.state === 'UNSPECIFIED' ? '' : next.measure.state
  }
}

/**
 * Reads the measures again after a criterion was written or changed, and
 * selects `selectId` — or keeps the chosen measure, by id, while it exists.
 */
async function refreshMeasures(selectId: number | null): Promise<void> {
  const chosen = food.value
  if (!chosen) {
    return
  }
  try {
    const rows = await loadMeasures(chosen)
    if (food.value !== chosen) {
      return
    }
    measures.value = rows
  } catch (cause) {
    if (food.value !== chosen) {
      return
    }
    error.value = `No se pudieron leer sus medidas${cause instanceof Error ? `: ${cause.message}` : ''}`
    return
  }
  const current = choice.value
  const wanted = selectId ?? (current.kind === 'measure' ? current.measure.id : null)
  if (wanted === null) {
    return
  }
  const found = measures.value.find((measure) => measure.id === wanted)
  if (found) {
    select({ kind: 'measure', measure: found })
  } else if (current.kind === 'measure') {
    select(GRAMS_CHOICE)
  }
}

function onCriterionSaved(criterion: FoodMeasure): void {
  asking.value = null
  void refreshMeasures(criterion.id)
}

function reset(): void {
  food.value = null
  rations.value = []
  measures.value = []
  asking.value = null
  choice.value = GRAMS_CHOICE
  error.value = null
  loadingOptions.value = false
}

const asksForWeight = computed(() => needsWeight(choice.value))
const weightHint = computed(() => hintFor(choice.value, proposed.value))
const isMl = computed(() => writesMl(choice.value))
const total = computed(() => choiceTotal(choice.value, count.value, grams.value))

/** `2 × unidad mediana ≈ 116 g`: the nutritionist's side only; the cell is written in units. */
const unitsLine = computed(() =>
  choice.value.kind === 'measure' ? unitsWeight(count.value, choice.value.measure) : null,
)

/** `120 g en seco`: what the cell will say for a weight. */
const writtenLine = computed(() =>
  total.value === null
    ? null
    : [amount(total.value, 1), isMl.value ? 'ml (leídos como g)' : 'g', stateWord(state.value || null)]
        .filter(Boolean)
        .join(' '),
)

const canAdd = computed(
  () =>
    food.value !== null &&
    !busy.value &&
    asking.value === null &&
    total.value !== null &&
    count.value > 0,
)

async function add(): Promise<void> {
  const chosen = food.value
  const current = choice.value
  if (!chosen || !canAdd.value) {
    return
  }
  busy.value = true
  error.value = null
  const common = {
    bedcaFoodId: chosen.id,
    state: state.value || null,
    dietId: props.dietId ?? undefined,
  }
  try {
    const composed = await dietsApi.compose(
      current.kind === 'measure'
        ? { ...common, foodMeasureId: current.measure.id, count: count.value }
        : { ...common, grams: Math.round(total.value! * 100) / 100 },
    )
    emit('append', composed.fragment)
    added.value = unitsLine.value ? `${composed.fragment} · ${unitsLine.value}` : composed.fragment
  } catch (cause) {
    error.value = `No se pudo añadir${cause instanceof Error ? `: ${cause.message}` : ''}`
  } finally {
    busy.value = false
  }
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

      <BedcaFoodSearch v-if="!food" @pick="pick" />

      <template v-else>
        <div class="food">
          <span class="food-name">{{ food.name }}</span>
          <button class="link" type="button" @click="reset()">Cambiar</button>
        </div>

        <p v-if="loadingOptions" class="hint">Leyendo raciones y medidas…</p>

        <ComposerChoices
          v-else
          :rations="rations"
          :measures="measures"
          :choice="choice"
          @select="select"
          @new-unit="asking = { from: null }"
        />

        <p v-if="!loadingOptions && rations.length === 0 && measures.length === 0" class="hint">
          Ninguna fuente cargada publica una ración o una medida para este alimento. Puedes crear
          tu propia unidad.
        </p>

        <MeasureCriterionForm
          v-if="asking"
          :key="asking.from?.id ?? 'new'"
          :bedca-food-id="food.id"
          :from="asking.from"
          @saved="onCriterionSaved"
          @cancel="asking = null"
        />

        <template v-else>
          <div class="line">
            <label v-if="choice.kind !== 'grams'" class="mini">
              <span>Cuántas</span>
              <input v-model.number="count" class="input num" type="number" min="0.25" step="0.25" />
            </label>
            <label v-if="asksForWeight" class="mini grow">
              <span>{{ weightHint }}</span>
              <input
                v-model.number="grams"
                class="input num"
                :class="{ proposal: proposed }"
                type="number"
                min="1"
                step="1"
                @input="proposed = false"
              />
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
            <template v-if="unitsLine">
              {{ unitsLine }}. Se escribe en unidades; el paciente no ve los gramos.
            </template>
            <template v-else-if="proposed && total !== null && choice.kind === 'ration'">
              Propuesta: el punto medio de {{ rationRange(choice.ration) }} por ración. Cámbiala si
              pesas otra cosa; se escribirá {{ amount(total, 1) }} {{ isMl ? 'ml' : 'g' }}.
            </template>
            <template v-else-if="writtenLine">Se escribirá {{ writtenLine }}.</template>
            <template v-else>Indica un peso para poder añadirlo.</template>
          </p>

          <button class="add" type="button" :disabled="!canAdd" @click="add()">
            {{ busy ? 'Añadiendo…' : 'Añadir al plato' }}
          </button>
        </template>

        <MeasureCriteriaList
          v-if="globalCriteria.length > 0"
          :criteria="globalCriteria"
          @changed="refreshMeasures(null)"
        />
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

.input.proposal {
  font-style: italic;
  color: var(--ink-muted);
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
  background: var(--ink-disabled);
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
