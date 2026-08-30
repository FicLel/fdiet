<script setup lang="ts">
import { computed } from 'vue'
import PatientCell from './PatientCell.vue'
import type { PatientDay } from '@/stores/patientWeek'
import { TOLERANCE_KCAL } from '@/stores/patientWeek'
import type { DayOfWeek } from '@/api/types'
import type { GridRow, MealRow } from '@/domain/slots'
import { integer, signed } from '@/domain/format'

/**
 * The dense week: seven days across, one row per dish slot down.
 *
 * The day header carries the total the patient actually ate — the plan plus
 * whatever was logged beside it — against the goal, because that is the number
 * the day is judged by. The bar underneath splits it back into its two parts,
 * so a day over the goal says which half put it there.
 */

const props = defineProps<{
  rows: GridRow[]
  days: PatientDay[]
  targetKcal: number
  /** Tablet from 834 px up: smaller type, four lines a cell, a narrower rail. */
  dense: boolean
  /** Whose summary the rail is showing; picking a header moves it. */
  selected: DayOfWeek | null
}>()

defineEmits<{ pick: [day: DayOfWeek] }>()

const columns = computed(() =>
  props.days.map((day, index) => {
    const off = day.kcal === null ? null : day.kcal - props.targetKcal
    const planShare = Math.min(100, Math.round(((day.plan.kcal ?? 0) / props.targetKcal) * 100))
    const extraShare = Math.min(
      100 - planShare,
      Math.round(((day.extraKcal ?? 0) / props.targetKcal) * 100),
    )
    return {
      ...day,
      total: integer(day.kcal),
      delta: off === null ? '' : signed(off),
      wide: off !== null && Math.abs(off) > TOLERANCE_KCAL,
      planWidth: `${planShare}%`,
      extraWidth: `${extraShare}%`,
      /** The last two columns open their cards leftwards, or off the screen. */
      alignRight: index >= props.days.length - 2,
    }
  }),
)

/**
 * How far down a row sits. The last few hang their cards from the bottom, or a
 * card opened on the last supper would run off the foot of the page.
 */
const lowRows = computed(() => {
  const meals = props.rows.filter((row): row is MealRow => row.kind === 'meal')
  return new Set(meals.slice(-3).map((row) => row.key))
})

function alignBottom(row: GridRow): boolean {
  return lowRows.value.has(row.key)
}
</script>

<template>
  <div class="grid" :class="{ dense }" role="grid">
    <div class="head" role="row">
      <div class="rail" />
      <!-- The header is the way to move the day summary in the week view; the
           day view has its own pills, and here the column itself is the pill. -->
      <button
        v-for="column in columns"
        :key="column.day"
        class="day"
        :class="{ current: column.day === selected }"
        type="button"
        role="columnheader"
        :aria-pressed="column.day === selected"
        :title="`Ver el resumen de ${column.name}`"
        @click="$emit('pick', column.day)"
      >
        <div class="day-top">
          <span class="day-name">{{ dense ? column.abbr : column.name }}</span>
          <span class="num day-date">{{ column.date }}</span>
        </div>
        <div class="day-figures">
          <span class="srf num day-kcal">{{ column.total }}</span>
          <span v-if="!dense" class="day-unit">kcal</span>
          <span class="num day-delta" :class="{ wide: column.wide }">{{ column.delta }}</span>
        </div>
        <!-- Two segments, never one: the plan and what was eaten beside it are
             different answers to why a day came out where it did. -->
        <div class="bar">
          <div class="bar-plan" :style="{ width: column.planWidth }" />
          <div class="bar-extra" :style="{ width: column.extraWidth }" />
        </div>
      </button>
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
        <PatientCell
          v-for="column in columns"
          :key="column.day"
          :row="(row as MealRow)"
          :day="column.day"
          :day-name="column.name"
          :day-date="column.date"
          :align-right="column.alignRight"
          :align-bottom="alignBottom(row)"
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

.dense .head {
  height: 56px;
}

.rail {
  width: 124px;
  flex: none;
  background: var(--surface-muted);
}

.dense .rail {
  width: 88px;
}

.day {
  flex: 1 1 0;
  min-width: 0;
  padding: 7px 10px;
  text-align: left;
  background: var(--surface-muted);
  border-top: 2px solid transparent;
}

.day:hover {
  background: #eef2ef;
}

.day.current {
  background: var(--sage-50);
  border-top-color: var(--sage-700);
}

.day.current .day-name {
  color: var(--sage-700);
}

.dense .day {
  padding: 6px 7px;
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

.dense .day-kcal {
  font-size: 13px;
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
  display: flex;
  gap: 2px;
  margin-top: 5px;
  height: 3px;
  border-radius: 2px;
  background: var(--line-soft);
  overflow: hidden;
}

.bar-plan {
  height: 100%;
  background: var(--sage-600);
}

.bar-extra {
  height: 100%;
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

.dense .band-rail {
  padding: 0 10px;
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

.dense .row {
  height: 86px;
}

.row-rail {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 12px;
  background: var(--surface-muted);
}

.dense .row-rail {
  padding: 0 10px;
}

.row-label {
  font-weight: 500;
  font-size: 12px;
  color: var(--ink-strong);
}

.dense .row-label {
  font-size: 11.5px;
  line-height: 1.25;
}

.row-sub {
  margin-top: 1px;
  font-size: 10.5px;
  color: var(--ink-faint);
}

.dense .row-sub {
  font-size: 10px;
}

/* Four lines of smaller type on a tablet, the way the artboard has it. */
.dense :deep(.text) {
  font-size: 10.5px;
  line-height: 1.3;
  -webkit-line-clamp: 4;
}
</style>
