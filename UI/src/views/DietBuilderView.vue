<script setup lang="ts">
import { computed, onMounted } from 'vue'
import EditorHeader from '@/components/EditorHeader.vue'
import GoalStrip from '@/components/GoalStrip.vue'
import WeekGrid from '@/components/WeekGrid.vue'
import DishEditPanel from '@/components/DishEditPanel.vue'
import { useDietDraft } from '@/stores/dietDraft'
import type { DishTotals } from '@/domain/nutrition'

/**
 * Where a diet is written. One week, one cell per dish slot per day; a cell is
 * selected in the grid and edited in the panel, and nothing reaches the patient
 * until it is published.
 */

const draft = useDietDraft()

onMounted(() => {
  if (draft.status.value === 'idle') {
    void draft.load()
  }
})

/** Every ingredient of the week, so the goal strip can show what it counted. */
const week = computed<DishTotals>(() =>
  draft.days.value.reduce<DishTotals>(
    (total, day) => ({
      kcal: null,
      proteinG: null,
      carbohydratesG: null,
      fatG: null,
      ingredients: total.ingredients + day.totals.ingredients,
      counted: total.counted + day.totals.counted,
      unmatched: total.unmatched + day.totals.unmatched,
      unmeasured: total.unmeasured + day.totals.unmeasured,
    }),
    {
      kcal: null,
      proteinG: null,
      carbohydratesG: null,
      fatG: null,
      ingredients: 0,
      counted: 0,
      unmatched: 0,
      unmeasured: 0,
    },
  ),
)
</script>

<template>
  <div class="screen">
    <EditorHeader
      :diet-name="draft.diet.value?.name ?? ''"
      :monday="draft.monday.value"
      :dirty-count="draft.dirtyCount.value"
      :publishing="draft.publishing.value"
      @discard="draft.discardAll()"
      @publish="draft.publish()"
    />

    <GoalStrip
      v-model:goal-note="draft.goalNote.value"
      :week-average-kcal="draft.weekAverageKcal.value"
      :week="week"
    />

    <p v-if="draft.error.value && draft.status.value === 'ready'" class="banner">
      {{ draft.error.value }}
    </p>

    <div v-if="draft.status.value === 'loading'" class="state">Cargando la dieta…</div>

    <div v-else-if="draft.status.value === 'error'" class="state">
      <p>{{ draft.error.value }}</p>
      <button class="retry" type="button" @click="draft.load()">Reintentar</button>
    </div>

    <div v-else class="body">
      <div class="grid-area scroll">
        <WeekGrid
          :rows="draft.rows.value"
          :days="draft.days.value"
          :target-kcal="draft.targetKcal.value"
          :selection="draft.selection.value"
          @select="draft.select"
        />
      </div>
      <DishEditPanel />
    </div>
  </div>
</template>

<style scoped>
.screen {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--paper);
  color: var(--ink);
  overflow: hidden;
}

.body {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
}

.grid-area {
  flex: 1 1 0;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 18px 20px 20px 24px;
}

.state {
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--ink-muted);
}

.retry {
  height: 34px;
  padding: 0 15px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--surface);
  background: var(--sage-700);
}

.banner {
  flex: none;
  margin: 0;
  padding: 9px 24px;
  background: var(--amber-50);
  color: var(--amber-700);
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}
</style>
