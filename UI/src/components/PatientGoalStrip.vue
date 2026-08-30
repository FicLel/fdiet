<script setup lang="ts">
import { computed } from 'vue'
import { decimal, integer, NO_VALUE } from '@/domain/format'
import type { DishTotals } from '@/domain/nutrition'

/**
 * The goal, the week's average, and what the patient has made of it so far.
 *
 * The goal line is a **placeholder**: the backend carries no goal field and no
 * patient to hang one on, so it is a fixed sentence rather than something read.
 * The two figures beside it are not — the average is the week's own arithmetic
 * and the score is the journal's.
 *
 * Both travel with their counts. An average over the third of the week that has
 * been matched to foods looks exactly like one over all of it, and a 5,0 from a
 * single rated plate is not a verdict on the diet.
 */

const props = defineProps<{
  goal: string
  goalNote: string
  weekAverageKcal: number | null
  /** Every ingredient of the week, so the average is read against its coverage. */
  week: DishTotals
  averageScore: number | null
  scored: number
  compact: boolean
}>()

const average = computed(() =>
  props.weekAverageKcal === null ? NO_VALUE : integer(props.weekAverageKcal),
)

const partial = computed(
  () => props.week.ingredients > 0 && props.week.counted < props.week.ingredients,
)

const coverage = computed(() =>
  partial.value ? `sobre ${props.week.counted} de ${props.week.ingredients} ingredientes` : '',
)

const scoreNote = computed(() => {
  if (props.scored === 0) {
    return 'aún sin puntuar'
  }
  return props.scored === 1 ? 'sobre 1 plato' : `sobre ${props.scored} platos`
})
</script>

<template>
  <div class="strip" :class="{ compact }">
    <span class="legend">
      <svg
        width="13"
        height="13"
        viewBox="0 0 14 14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        aria-hidden="true"
      >
        <circle cx="7" cy="7" r="5.5" />
        <circle cx="7" cy="7" r="2" />
      </svg>
      <span v-if="!compact">Objetivo</span>
    </span>

    <!-- Placeholder: no goal field on the backend, and no patient to hang one on. -->
    <span class="srf goal placeholder-data">{{ goal }}</span>

    <template v-if="!compact">
      <span class="dot">·</span>
      <span class="note placeholder-data">{{ goalNote }}</span>

      <span class="figure">
        <span class="figure-label">Media semanal</span>
        <span class="srf num figure-value" :class="{ partial }">{{ average }}</span>
        <span class="figure-label">kcal</span>
        <span v-if="coverage" class="figure-count">{{ coverage }}</span>
      </span>

      <span class="divider" />

      <span class="figure">
        <span class="figure-label">Tu puntuación</span>
        <span class="srf num figure-value">{{ decimal(averageScore) }}</span>
        <span class="figure-label">/ 5</span>
        <span class="figure-count">{{ scoreNote }}</span>
      </span>
    </template>
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

.strip.compact {
  gap: 8px;
  height: auto;
  padding: 10px 16px;
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
  flex: none;
  font-size: 15px;
  color: var(--ink-strong);
}

.compact .goal {
  flex: 1 1 0;
  min-width: 0;
  font-size: 13.5px;
}

.dot {
  flex: none;
  color: #cfd8d2;
}

.note {
  flex: 1 1 0;
  min-width: 0;
  color: var(--ink-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

.figure-count {
  font-size: 10.5px;
  color: var(--ink-faint);
}

.divider {
  width: 1px;
  height: 20px;
  background: var(--line);
}

/* Tablet: the note is the first thing to go — the goal itself is the point. */
@media (max-width: 1239px) {
  .strip {
    gap: 12px;
    padding: 0 18px;
    height: 48px;
  }

  .note,
  .dot {
    display: none;
  }

  .goal {
    flex: 1 1 0;
    min-width: 0;
    font-size: 14px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
