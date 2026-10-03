<script setup lang="ts">
import { ref } from 'vue'
import { isRefusal } from '@/api/http'
import { referenceApi } from '@/api/reference'
import type { FoodMeasure, MeasureUsage } from '@/api/types'
import {
  criterionRequest,
  criterionValue,
  criterionWeight,
  unitOf,
  usageText,
} from '@/domain/measureCriteria'

/**
 * The nutritionist's global criteria for one food, with their weight to change
 * and a way to remove them.
 *
 * A criterion is linked, like a library recipe: every ingredient and extra it
 * weighs reads it, in every diet, so a change is live everywhere and the usage
 * is shown before the save. A delete the backend refuses (still in use) shows
 * the backend's own reason.
 */
const props = defineProps<{ criteria: FoodMeasure[] }>()

const emit = defineEmits<{ changed: [] }>()

const editingId = ref<number | null>(null)
const value = ref<number | null>(null)
const usage = ref<MeasureUsage | null>(null)
const busy = ref(false)
const error = ref<string | null>(null)

async function startEdit(criterion: FoodMeasure): Promise<void> {
  editingId.value = criterion.id
  value.value = criterionValue(criterion)
  usage.value = null
  error.value = null
  try {
    const found = await referenceApi.criterionUsage(criterion.id)
    if (editingId.value === criterion.id) {
      usage.value = found
    }
  } catch (cause) {
    error.value = `No se pudo saber a qué afecta${cause instanceof Error ? `: ${cause.message}` : ''}`
  }
}

function cancel(): void {
  editingId.value = null
  usage.value = null
  error.value = null
}

async function save(criterion: FoodMeasure): Promise<void> {
  if (busy.value || value.value === null || value.value <= 0 || criterion.bedcaFoodId === null) {
    return
  }
  busy.value = true
  error.value = null
  try {
    await referenceApi.updateCriterion(
      criterion.id,
      criterionRequest(
        criterion.bedcaFoodId,
        criterion.measure,
        criterion.size,
        value.value,
        unitOf(criterion),
        criterion.note,
      ),
    )
    editingId.value = null
    emit('changed')
  } catch (cause) {
    error.value = `No se pudo guardar${cause instanceof Error ? `: ${cause.message}` : ''}`
  } finally {
    busy.value = false
  }
}

async function remove(criterion: FoodMeasure): Promise<void> {
  if (busy.value) {
    return
  }
  busy.value = true
  error.value = null
  try {
    await referenceApi.deleteCriterion(criterion.id)
    if (editingId.value === criterion.id) {
      editingId.value = null
    }
    emit('changed')
  } catch (cause) {
    error.value = isRefusal(cause)
      ? 'No se puede borrar: este criterio todavía pesa ingredientes o extras. Cambia su peso en lugar de borrarlo.'
      : `No se pudo borrar${cause instanceof Error ? `: ${cause.message}` : ''}`
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="criteria">
    <span class="title">Tus criterios para este alimento</span>
    <ul class="rows">
      <li v-for="criterion in props.criteria" :key="criterion.id" class="row">
        <div class="row-head">
          <span class="weight">{{ criterionWeight(criterion) }}</span>
          <template v-if="editingId !== criterion.id">
            <button class="link" type="button" :disabled="busy" @click="startEdit(criterion)">
              Cambiar peso
            </button>
            <button class="link" type="button" :disabled="busy" @click="remove(criterion)">
              Borrar
            </button>
          </template>
        </div>
        <div v-if="editingId === criterion.id" class="edit">
          <label class="mini">
            <span>Nuevo peso ({{ unitOf(criterion) }})</span>
            <input v-model.number="value" class="input num" type="number" min="0.1" step="0.1" />
          </label>
          <p class="usage">
            <template v-if="usage">{{ usageText(usage) }}</template>
            <template v-else>Comprobando a qué afecta…</template>
          </p>
          <div class="actions">
            <button class="secondary" type="button" :disabled="busy" @click="cancel()">Cancelar</button>
            <button
              class="primary"
              type="button"
              :disabled="busy || usage === null || value === null || value <= 0"
              @click="save(criterion)"
            >
              Guardar en todas las dietas
            </button>
          </div>
        </div>
      </li>
    </ul>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.criteria {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}

.title {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.rows {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.row-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 11.5px;
  color: var(--ink-strong);
}

.weight {
  flex: 1 1 auto;
  min-width: 0;
}

.link {
  font-size: 11px;
  color: var(--sage-700);
}

.link:disabled {
  color: var(--ink-disabled);
}

.edit {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 5px;
}

.mini {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 120px;
  font-size: 10.5px;
  color: var(--ink-muted);
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

.usage {
  margin: 0;
  padding: 5px 8px;
  border-radius: var(--radius);
  background: var(--amber-50);
  font-size: 11px;
  color: var(--amber-700);
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
