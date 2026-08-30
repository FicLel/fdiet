<script setup lang="ts">
import { computed } from 'vue'
import DayTotalCard from './DayTotalCard.vue'
import MacroBars from './MacroBars.vue'
import ExtraFoodRow from './ExtraFoodRow.vue'
import type { PatientDay } from '@/stores/patientWeek'
import { usePatientWeek } from '@/stores/patientWeek'
import { grams } from '@/domain/format'

/**
 * The desktop right rail: what the day came to, what it was made of, and what
 * was eaten beside the plan.
 *
 * The macros are the day's own — plan and extras together — because that is
 * what was actually eaten. The plan alone is one segment of the bar above.
 */

const props = defineProps<{
  day: PatientDay
  targetKcal: number
  targetProteinG: number
  isToday: boolean
}>()

defineEmits<{ add: [] }>()

const week = usePatientWeek()

/** The one goal the design states in grams; a placeholder until the backend has one. */
const proteinNote = computed(() => {
  const protein = props.day.proteinG
  if (protein === null) {
    return 'Sin figuras de proteína todavía para este día'
  }
  const missing = props.targetProteinG - protein
  return missing <= 0
    ? `Proteína del día cumplida (${grams(protein)} g)`
    : `Faltan ${grams(missing)} g de proteína para el objetivo`
})

const count = computed(() =>
  props.day.extras.length === 0
    ? ''
    : props.day.extras.length === 1
      ? '1 este día'
      : `${props.day.extras.length} este día`,
)
</script>

<template>
  <aside class="rail">
    <DayTotalCard :day="day" :target-kcal="targetKcal" :is-today="isToday" />

    <div class="card">
      <div class="card-title">Reparto del día</div>
      <MacroBars
        class="card-body"
        :protein-g="day.proteinG"
        :carbohydrates-g="day.carbohydratesG"
        :fat-g="day.fatG"
      />
      <p class="card-note">{{ proteinNote }}</p>
    </div>

    <div class="card extras">
      <div class="card-head">
        <span class="card-title">Fuera del plan</span>
        <span class="num card-count">{{ count }}</span>
      </div>

      <div class="list scroll">
        <ExtraFoodRow
          v-for="extra in day.extras"
          :key="extra.id"
          :extra="extra"
          :disabled="week.saving.value"
          @remove="week.removeExtra(extra.id)"
        />
        <p v-if="day.extras.length === 0" class="none">
          Nada fuera del plan este día. Si comes algo distinto, añádelo y se suma al total.
        </p>
      </div>

      <button class="add" type="button" @click="$emit('add')">
        <svg
          width="13"
          height="13"
          viewBox="0 0 14 14"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M7 3v8M3 7h8" />
        </svg>
        Añadir comida extra
      </button>
    </div>
  </aside>
</template>

<style scoped>
.rail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1 1 0;
  min-height: 0;
}

.card {
  flex: none;
  padding: 14px 16px 15px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
}

.card.extras {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex: none;
}

.card-title {
  font-weight: 600;
  font-size: 12.5px;
  color: var(--ink-strong);
}

.card-count {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-faint);
}

.card-body {
  margin-top: 12px;
}

.card-note {
  margin: 12px 0 0;
  padding-top: 11px;
  border-top: 1px solid var(--line-soft);
  font-size: 11.5px;
  color: var(--ink-muted);
}

.list {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.none {
  margin: 2px 0 0;
  font-size: 11.5px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.add {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin-top: 11px;
  height: 34px;
  border: 1px dashed var(--extra-dash);
  border-radius: var(--radius);
  font-weight: 500;
  font-size: 12px;
  color: var(--extra-ink);
}

.add:hover {
  background: var(--extra-surface);
}
</style>
