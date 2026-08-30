<script setup lang="ts">
import { computed } from 'vue'
import { integer, NO_VALUE } from '@/domain/format'
import type { DishTotals } from '@/domain/nutrition'

const props = defineProps<{
  /** Placeholder: the nutritionist's own note, held in the browser, never sent. */
  goalNote: string
  weekAverageKcal: number | null
  /** Every ingredient of the week, so the average is read against its coverage. */
  week: DishTotals
  /** How many can still be matched to a food — the queue the review walks. */
  unmatched: number
}>()

defineEmits<{ 'update:goalNote': [value: string]; review: [] }>()

const average = computed(() =>
  props.weekAverageKcal === null ? NO_VALUE : integer(props.weekAverageKcal),
)

/**
 * A weekly average over a third of the ingredients looks exactly like one over
 * all of them, so the count travels beside it rather than under a tooltip.
 */
const coverage = computed(() => `${integer(props.week.counted)} / ${integer(props.week.ingredients)}`)

const partial = computed(() => props.week.ingredients > 0 && props.week.counted < props.week.ingredients)
</script>

<template>
  <div class="strip">
    <span class="legend">
      <svg width="13" height="13" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
        <circle cx="7" cy="7" r="5.5" />
        <circle cx="7" cy="7" r="2" />
      </svg>
      Objetivo
    </span>

    <input
      class="srf goal"
      :value="goalNote"
      aria-label="Objetivo de la dieta"
      @input="$emit('update:goalNote', ($event.target as HTMLInputElement).value)"
    />

    <span class="figure">
      <span class="figure-label">Media semanal</span>
      <span class="srf num figure-value">{{ average }}</span>
      <span class="figure-label">kcal</span>
    </span>

    <span class="divider" />

    <span class="figure">
      <span class="figure-label">Ingredientes contados</span>
      <span class="srf num figure-value" :class="{ partial }">{{ coverage }}</span>
    </span>

    <!-- What is missing from the average is mostly ingredients nobody has
         matched yet, so the count is also the way into fixing them. -->
    <button
      v-if="unmatched > 0"
      class="review"
      type="button"
      :title="`Vincular los ${unmatched} ingredientes que aún no están en el catálogo`"
      @click="$emit('review')"
    >
      Repasar <span class="num">{{ integer(unmatched) }}</span> sin vincular
    </button>
  </div>
</template>

<style scoped>
.strip {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 54px;
  flex: none;
  padding: 0 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.legend {
  display: flex;
  align-items: center;
  gap: 7px;
  flex: none;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.goal {
  flex: 1 1 0;
  min-width: 0;
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 14.5px;
  color: var(--ink-strong);
  background: var(--surface);
}

.figure {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex: none;
}

.figure-label {
  font-size: 11.5px;
  color: var(--ink-muted);
}

.figure-value {
  font-size: 16px;
  font-weight: 500;
  color: var(--ink-strong);
}

.figure-value.partial {
  color: var(--amber-700);
}

.divider {
  width: 1px;
  height: 20px;
  background: var(--line);
}

.review {
  flex: none;
  height: 28px;
  padding: 0 11px;
  border: 1px solid var(--amber-400);
  border-radius: var(--radius);
  font-size: 11.5px;
  font-weight: 500;
  color: var(--amber-700);
  background: var(--surface);
}

.review:hover {
  background: var(--amber-50);
}
</style>
