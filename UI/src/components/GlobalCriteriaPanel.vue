<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import type { CompositionFood } from '@/api/compositionTypes'
import { referenceApi } from '@/api/reference'
import type { FoodMeasure, MeasureCriterionSaved } from '@/api/types'
import { reweighedText } from '@/domain/measureCriteria'
import { compositionFoodName } from '@/domain/compositionFood'
import CompositionFoodSearch from './CompositionFoodSearch.vue'
import MeasureCriteriaList from './MeasureCriteriaList.vue'
import MeasureCriterionForm from './MeasureCriterionForm.vue'

/**
 * The nutritionist's global criteria ("tus criterios") for one CIQUAL / BLS
 * food: pick the food, see the criteria she already holds for it, change or
 * remove one, or write a new one.
 *
 * A criterion names one composition food, chosen here — the search starts
 * from `hint` (the composer's food) and nothing is picked on its own. With `from`, the form
 * opens on that published range as soon as the food is chosen.
 */
const props = defineProps<{
  hint: string
  from: FoodMeasure | null
}>()

const emit = defineEmits<{ saved: [saved: MeasureCriterionSaved]; close: [] }>()

const food = shallowRef<CompositionFood | null>(null)
const criteria = shallowRef<FoodMeasure[]>([])
const loading = ref(false)
// Opened to write one: the form shows first, the criteria already held beside it.
const adding = ref(true)
const error = ref<string | null>(null)
/** What the last save weighed again, in every diet (FD-054). */
const done = ref<string | null>(null)

async function load(chosen: CompositionFood): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const rows = await referenceApi.criteria(chosen.id)
    if (food.value === chosen) {
      criteria.value = rows
    }
  } catch (cause) {
    if (food.value === chosen) {
      error.value = `No se pudieron leer tus criterios${cause instanceof Error ? `: ${cause.message}` : ''}`
    }
  } finally {
    if (food.value === chosen) {
      loading.value = false
    }
  }
}

function pick(chosen: CompositionFood): void {
  food.value = chosen
  criteria.value = []
  void load(chosen)
}

function change(): void {
  food.value = null
  criteria.value = []
  error.value = null
}

function onSaved(saved: MeasureCriterionSaved): void {
  done.value = reweighedText(saved.reweighed)
  adding.value = false
  if (food.value) {
    void load(food.value)
  }
  emit('saved', saved)
}
</script>

<template>
  <div class="panel">
    <div class="head">
      <span class="title">Tus criterios</span>
      <button class="link" type="button" @click="emit('close')">Cerrar</button>
    </div>

    <CompositionFoodSearch v-if="!food" :initial-term="hint" @pick="pick" />

    <template v-else>
      <div class="food" :title="food.attribution">
        <span class="food-name">{{ compositionFoodName(food) }}</span>
        <span class="food-source">{{ food.sourceLabel }}</span>
        <button class="link" type="button" @click="change()">Cambiar</button>
      </div>

      <p v-if="loading && criteria.length === 0" class="hint">Leyendo tus criterios…</p>
      <MeasureCriteriaList
        v-else-if="criteria.length > 0"
        :criteria="criteria"
        @changed="load(food)"
      />
      <p v-else class="hint">Todavía no tienes criterios para este alimento.</p>

      <MeasureCriterionForm
        v-if="adding"
        :key="`${food.id}-${from?.id ?? 'new'}`"
        :composition-food-id="food.id"
        :from="from"
        @saved="onSaved"
        @cancel="adding = false"
      />
      <button v-else class="add" type="button" @click="adding = true; done = null">+ Nueva unidad</button>
      <p v-if="done" class="done">{{ done }}</p>
    </template>

    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 9px;
  border: 1px solid var(--sage-200);
  border-radius: var(--radius);
  background: var(--surface);
}

.head,
.food {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.title {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-strong);
}

.link {
  margin-left: auto;
  font-size: 11px;
  color: var(--sage-700);
}

.food-name {
  font-weight: 600;
  font-size: 12px;
  color: var(--ink-strong);
}

.food-source {
  font-size: 10.5px;
  color: var(--ink-muted);
}

.hint {
  margin: 0;
  font-size: 11px;
  color: var(--ink-faint);
}

.add {
  height: 30px;
  border: 1px dashed var(--line-input);
  border-radius: var(--radius);
  font-size: 12px;
  font-weight: 500;
  color: var(--sage-700);
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
