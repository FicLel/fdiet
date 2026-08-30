<script setup lang="ts">
import { computed } from 'vue'
import GridCell from './GridCell.vue'
import type { DayColumn } from '@/stores/dietDraft'
import type { GridRow, MealRow } from '@/domain/slots'
import type { DayOfWeek } from '@/api/types'
import { integer, signed } from '@/domain/format'

const props = defineProps<{
  rows: GridRow[]
  days: DayColumn[]
  targetKcal: number
  selection: { day: DayOfWeek; mealType: string; dishIndex: number } | null
}>()

defineEmits<{ select: [row: MealRow, day: DayOfWeek] }>()

/** More than this off the goal is worth colouring; less is rounding. */
const TOLERANCE_KCAL = 60

const columns = computed(() =>
  props.days.map((day) => {
    const kcal = day.totals.kcal
    const off = kcal === null ? null : kcal - props.targetKcal
    return {
      ...day,
      kcal: integer(kcal),
      delta: off === null ? '' : signed(off),
      wide: off !== null && Math.abs(off) > TOLERANCE_KCAL,
      barWidth: kcal === null ? '0%' : `${Math.min(112, Math.round((kcal / props.targetKcal) * 100))}%`,
    }
  }),
)

function isSelected(row: MealRow, day: DayOfWeek): boolean {
  const at = props.selection
  return at !== null && at.day === day && at.mealType === row.mealType && at.dishIndex === row.dishIndex
}
</script>

<template>
  <div class="grid" role="grid">
    <div class="head" role="row">
      <div class="rail" />
      <div v-for="column in columns" :key="column.day" class="day" role="columnheader">
        <div class="day-top">
          <span class="day-name">{{ column.name }}</span>
          <span class="num day-date">{{ column.date }}</span>
        </div>
        <div class="day-figures">
          <span class="srf num day-kcal">{{ column.kcal }}</span>
          <span class="day-unit">kcal</span>
          <span class="num day-delta" :class="{ wide: column.wide }">{{ column.delta }}</span>
        </div>
        <div class="bar">
          <div class="bar-fill" :class="{ wide: column.wide }" :style="{ width: column.barWidth }" />
        </div>
      </div>
    </div>

    <template v-for="row in rows" :key="row.key">
      <div v-if="row.kind === 'band'" class="band" role="row">
        <div class="rail band-rail">
          <span class="band-label">{{ row.label }}</span>
        </div>
        <div class="band-rest" />
      </div>

      <div v-else class="row" role="row">
        <div class="rail row-rail">
          <span class="row-label">{{ row.label }}</span>
          <span v-if="row.sub" class="row-sub">{{ row.sub }}</span>
        </div>
        <GridCell
          v-for="column in days"
          :key="column.day"
          :row="row"
          :day="column.day"
          :selected="isSelected(row, column.day)"
          @select="$emit('select', row, column.day)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
/* The hairlines are the 1px gaps letting the background through, so a cell
   never carries a border of its own and nothing doubles up at a join. */
.grid {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: 5px;
  overflow: hidden;
}

.head,
.band,
.row {
  display: flex;
  gap: 1px;
  flex: none;
}

.head {
  height: 60px;
}

.rail {
  width: 124px;
  flex: none;
  background: var(--surface-muted);
}

.day {
  flex: 1 1 0;
  min-width: 0;
  padding: 7px 10px;
  background: var(--surface-muted);
}

.day-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.day-name {
  font-weight: 600;
  font-size: 12px;
  color: var(--ink-strong);
}

.day-date {
  font-size: 11px;
  color: var(--ink-faint);
}

.day-figures {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 2px;
}

.day-kcal {
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-strong);
}

.day-unit {
  font-size: 10.5px;
  color: var(--ink-faint);
}

.day-delta {
  margin-left: auto;
  font-size: 10.5px;
  font-weight: 500;
  color: var(--ink-disabled);
}

.day-delta.wide {
  color: var(--amber-700);
}

.bar {
  margin-top: 5px;
  height: 3px;
  border-radius: 2px;
  background: var(--line-soft);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: var(--sage-600);
}

.bar-fill.wide {
  background: var(--amber-400);
}

.band {
  height: 24px;
}

.band-rail {
  display: flex;
  align-items: center;
  padding: 0 12px;
  background: var(--sage-100);
}

.band-label {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.band-rest {
  flex: 1 1 0;
  background: var(--sage-50);
}

.row {
  height: 78px;
}

.row-rail {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 12px;
}

.row-label {
  font-weight: 500;
  font-size: 12px;
  color: var(--ink-strong);
}

.row-sub {
  margin-top: 1px;
  font-size: 10.5px;
  color: var(--ink-faint);
}
</style>
