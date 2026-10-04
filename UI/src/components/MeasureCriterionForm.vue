<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { isRefusal } from '@/api/http'
import { referenceApi } from '@/api/reference'
import type { FoodMeasure, HouseholdMeasure, MeasureCriterionSaved, PortionSize } from '@/api/types'
import {
  CRITERION_UNITS,
  DEFAULT_MEASURE,
  SIZE_OPTIONS,
  criterionRequest,
  measureName,
  proposedPerUnit,
  publishedFigure,
  unitOf,
  type CriterionUnit,
} from '@/domain/measureCriteria'
import { useReference } from '@/stores/reference'

/**
 * Writes the nutritionist's own weight for one measure of one food — "tu
 * criterio" — reused in every diet, every patient and the library.
 *
 * Two ways in. From a published range (`from`): the measure and size are the
 * row's, and the weight starts at the middle of the range, labelled as a
 * proposal with the range and its source beside it. From nothing ("Nueva
 * unidad"): the measure word comes from the backend's vocabulary.
 */
const props = defineProps<{
  /** The CIQUAL / BLS food the criterion weighs. */
  compositionFoodId: number
  from: FoodMeasure | null
}>()

/** `saved` carries what the save weighed again in every diet (FD-054). */
const emit = defineEmits<{ saved: [saved: MeasureCriterionSaved]; cancel: [] }>()

const reference = useReference()

const proposal = props.from ? proposedPerUnit(props.from) : null
const measure = ref<HouseholdMeasure>(props.from?.measure ?? DEFAULT_MEASURE)
const size = ref<PortionSize | null>(props.from?.size ?? null)
const value = ref<number | null>(proposal)
const unit = ref<CriterionUnit>(props.from ? unitOf(props.from) : 'g')
const busy = ref(false)
const error = ref<string | null>(null)

const label = computed(() => {
  const word = reference.vocabulary.value.find((candidate) => candidate.code === measure.value)
  return measureName({ measureLabel: word?.label ?? props.from?.measureLabel ?? '', size: size.value })
})

const isProposal = computed(() => proposal !== null && value.value === proposal)
const canSave = computed(() => !busy.value && value.value !== null && value.value > 0)

onMounted(() => {
  reference.ensure().catch(() => {
    error.value = 'No se pudo leer el vocabulario de medidas.'
  })
})

async function save(): Promise<void> {
  if (!canSave.value) {
    return
  }
  busy.value = true
  error.value = null
  try {
    const saved = await referenceApi.createCriterion(
      criterionRequest(props.compositionFoodId, measure.value, size.value, value.value!, unit.value),
    )
    emit('saved', saved)
  } catch (cause) {
    // The form sends exactly one weight, so a refusal is a criterion already held for this slot.
    error.value = isRefusal(cause)
      ? 'Ya tienes un criterio para este alimento, medida y tamaño. Cámbialo en «Tus criterios».'
      : `No se pudo guardar${cause instanceof Error ? `: ${cause.message}` : ''}`
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="form">
    <span class="title">{{ from ? 'Peso comestible por unidad' : 'Nueva unidad' }}</span>

    <p v-if="from" class="published">{{ publishedFigure(from) }}</p>

    <div v-else class="line">
      <label class="mini grow">
        <span>Medida</span>
        <select v-model="measure" class="input">
          <option v-for="word in reference.vocabulary.value" :key="word.code" :value="word.code">
            {{ word.label }}
          </option>
        </select>
      </label>
      <label class="mini">
        <span>Tamaño</span>
        <select v-model="size" class="input">
          <option v-for="option in SIZE_OPTIONS" :key="option.label" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </div>

    <div class="line">
      <label class="mini grow">
        <span>Peso comestible de 1 {{ label }}</span>
        <input v-model.number="value" class="input num" type="number" min="0.1" step="0.1" />
      </label>
      <label class="mini">
        <span>En</span>
        <select v-model="unit" class="input">
          <option v-for="option in CRITERION_UNITS" :key="option" :value="option">{{ option }}</option>
        </select>
      </label>
    </div>

    <p v-if="isProposal" class="proposal">
      Propuesta: el punto medio del rango publicado. Confírmala o escribe tu peso.
    </p>
    <p class="hint">
      Se guarda como tu criterio y se usa en todas las dietas, pacientes y recetas del recetario.
    </p>

    <div class="actions">
      <button class="secondary" type="button" :disabled="busy" @click="emit('cancel')">Cancelar</button>
      <button class="primary" type="button" :disabled="!canSave" @click="save()">
        {{ busy ? 'Guardando…' : 'Guardar criterio' }}
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 9px;
  border: 1px solid var(--sage-200);
  border-radius: var(--radius);
  background: var(--surface);
}

.title {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-strong);
}

.published {
  margin: 0;
  font-size: 11px;
  color: var(--ink-muted);
}

.line {
  display: flex;
  gap: 6px;
}

.mini {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 92px;
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

.proposal {
  margin: 0;
  padding: 5px 8px;
  border-radius: var(--radius);
  background: var(--amber-50);
  font-size: 11px;
  color: var(--amber-700);
}

.hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.actions {
  display: flex;
  gap: 6px;
}

.primary,
.secondary {
  flex: 1 1 0;
  height: 30px;
  border-radius: var(--radius);
  font-size: 12px;
  font-weight: 500;
}

.primary {
  color: var(--surface);
  background: var(--sage-700);
}

.primary:disabled {
  background: var(--ink-disabled);
}

.secondary {
  border: 1px solid var(--line);
  color: var(--ink);
}

.error {
  margin: 0;
  font-size: 11px;
  color: var(--amber-700);
}
</style>
