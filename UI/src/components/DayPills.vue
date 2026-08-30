<script setup lang="ts">
import type { DayOfWeek } from '@/api/types'
import type { PatientDay } from '@/stores/patientWeek'
import { integer } from '@/domain/format'

/**
 * Which day the day view is showing. Every day of the week carries its own
 * total, so choosing one is also reading the week at a glance.
 */

withDefaults(
  defineProps<{
    days: PatientDay[]
    selected: DayOfWeek | null
    /** The mobile strip: fixed-width pills that scroll sideways. */
    scrolling?: boolean
  }>(),
  { scrolling: false },
)

defineEmits<{ pick: [day: DayOfWeek] }>()
</script>

<template>
  <div class="pills scroll" :class="{ scrolling }" role="tablist">
    <button
      v-for="day in days"
      :key="day.day"
      class="pill"
      :class="{ current: day.day === selected }"
      type="button"
      role="tab"
      :aria-selected="day.day === selected"
      @click="$emit('pick', day.day)"
    >
      <span class="top">
        <span class="name">{{ scrolling ? day.abbr : day.name }}</span>
        <span class="num date">{{ day.date }}</span>
      </span>
      <span class="srf num total">{{ integer(day.kcal) }}</span>
    </button>
  </div>
</template>

<style scoped>
.pills {
  display: flex;
  gap: 6px;
  flex: none;
}

.pills.scrolling {
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.pill {
  flex: 1 1 0;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 5px;
  text-align: left;
  background: var(--surface);
}

.pills.scrolling .pill {
  flex: none;
  width: 62px;
  padding: 8px 0 9px;
  border-radius: 8px;
  text-align: center;
}

.pill.current {
  border-color: var(--sage-200);
  background: var(--sage-50);
}

.top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.pills.scrolling .top {
  display: block;
}

.name {
  font-weight: 600;
  font-size: 12px;
  color: var(--ink-strong);
}

.pill.current .name {
  color: var(--sage-700);
}

.date {
  font-size: 11px;
  color: var(--ink-faint);
}

.pills.scrolling .date {
  display: block;
  margin-top: 1px;
}

.total {
  display: block;
  margin-top: 2px;
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-strong);
}

.pills.scrolling .total {
  margin-top: 4px;
  font-size: 13px;
}
</style>
