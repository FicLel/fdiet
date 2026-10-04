<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import type { CompositionFood } from '@/api/compositionTypes'
import { dietsApi } from '@/api/diets'
import type { FoodMeasure } from '@/api/types'
import { compositionFoodName } from '@/domain/compositionFood'
import { CRITERION_UNITS, type CriterionUnit } from '@/domain/measureCriteria'
import { useDietCriteria } from '@/stores/dietCriteria'
import { useDietDraft } from '@/stores/dietDraft'
import { useFoodLink } from '@/stores/foodLink'
import CompositionFoodSearch from './CompositionFoodSearch.vue'
import MeasureCriteriaList from './MeasureCriteriaList.vue'

/**
 * The nutritionist's own weight for the open ingredient's measure, for this
 * diet only, and the diet's criteria already written.
 *
 * A criterion weighs a CIQUAL / BLS food, chosen here: the search starts from
 * the ingredient's name and nothing is picked on its own. It attaches only to
 * ingredients matched to that same food.
 */
const link = useFoodLink()
const draft = useDietDraft()
const { saveDietCriterion } = useDietCriteria()

const at = computed(() => link.target.value)
const dietId = computed(() => draft.diet.value?.id ?? null)
const hint = computed(() => at.value?.matchedName ?? at.value?.name ?? '')

const food = shallowRef<CompositionFood | null>(null)
const value = ref<number | null>(null)
const unit = ref<CriterionUnit>('g')
const note = ref('')
const saved = ref<string | null>(null)
const criteria = shallowRef<FoodMeasure[]>([])
const listError = ref<string | null>(null)

const canSave = computed(
  () => food.value !== null && value.value !== null && value.value > 0 && !link.savingMeasure.value,
)

async function loadCriteria(): Promise<void> {
  const id = dietId.value
  listError.value = null
  if (id === null) {
    criteria.value = []
    return
  }
  try {
    const rows = await dietsApi.measures(id)
    if (dietId.value === id) {
      criteria.value = rows
    }
  } catch (cause) {
    listError.value = `No se pudieron leer los criterios de la dieta${cause instanceof Error ? `: ${cause.message}` : ''}`
  }
}

async function save(): Promise<void> {
  saved.value = null
  if (!canSave.value) {
    return
  }
  const attached = await saveDietCriterion(food.value!.id, value.value!, unit.value, note.value)
  if (attached === null) {
    return
  }
  saved.value =
    attached === 0
      ? 'Guardado para esta dieta. Todavía no pesa ningún ingrediente (ver la nota de arriba).'
      : attached === 1
        ? 'Guardado para esta dieta y aplicado a 1 ingrediente.'
        : `Guardado para esta dieta y aplicado a ${attached} ingredientes.`
  value.value = null
  note.value = ''
  await loadCriteria()
}

watch(
  () => at.value?.id,
  () => {
    food.value = null
    saved.value = null
    value.value = null
    note.value = ''
  },
)

watch(dietId, () => void loadCriteria(), { immediate: true })
</script>

<template>
  <div v-if="at" class="own">
    <form class="fields" @submit.prevent="save()">
      <span class="own-title">Criterio para esta dieta</span>

      <CompositionFoodSearch v-if="!food" :key="at.id ?? 'new'" :initial-term="hint" @pick="food = $event" />
      <div v-else class="food" :title="food.attribution">
        <span class="food-name">{{ compositionFoodName(food) }}</span>
        <span class="note inline">{{ food.sourceLabel }}</span>
        <button class="link" type="button" @click="food = null">Cambiar</button>
      </div>

      <div class="own-line">
        <span class="own-lead">1 {{ at.unit }} =</span>
        <input
          v-model.number="value"
          class="own-input num"
          type="number"
          min="0.1"
          step="0.1"
          aria-label="Peso de una medida"
        />
        <select v-model="unit" class="own-input unit" aria-label="Unidad">
          <option v-for="option in CRITERION_UNITS" :key="option" :value="option">{{ option }}</option>
        </select>
        <button class="own-save" type="submit" :disabled="!canSave">Guardar</button>
      </div>
      <input v-model="note" class="own-input" type="text" maxlength="500" placeholder="Nota (opcional)" />
      <p class="note">
        Sólo para esta dieta, y para todos sus «{{ at.unit }}» de este alimento. Queda anotado como
        criterio tuyo, no como dato publicado.
      </p>
      <p v-if="saved" class="note ok">{{ saved }}</p>
    </form>

    <MeasureCriteriaList
      v-if="criteria.length > 0 && dietId !== null"
      :criteria="criteria"
      :diet-id="dietId"
      show-food
      @changed="loadCriteria()"
    />
    <p v-if="listError" class="note error">{{ listError }}</p>
  </div>
</template>

<style scoped>
.own {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

/* The list below edits its own rows: kept out of the form so Enter there never saves this one. */
.fields {
  display: contents;
}

.own-title {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.food {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.food-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-strong);
}

.link {
  margin-left: auto;
  font-size: 11px;
  color: var(--sage-700);
}

.own-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.own-lead {
  font-size: 11.5px;
  color: var(--ink);
  white-space: nowrap;
}

.own-input {
  min-width: 0;
  height: 28px;
  padding: 0 7px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 12px;
}

.own-line .own-input {
  width: 70px;
}

.own-line .own-input.unit {
  width: 56px;
}

.own-save {
  margin-left: auto;
  height: 28px;
  padding: 0 10px;
  border-radius: var(--radius);
  font-size: 11.5px;
  font-weight: 500;
  color: var(--surface);
  background: var(--sage-700);
}

.own-save:disabled {
  background: var(--ink-disabled);
}

.note {
  margin: 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.note.inline {
  font-size: 10.5px;
}

.note.ok {
  color: var(--sage-700);
}

.note.error {
  color: var(--amber-700);
}
</style>
