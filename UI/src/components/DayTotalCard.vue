<script setup lang="ts">
import { computed } from 'vue'
import type { PatientDay } from '@/stores/patientWeek'
import { TOLERANCE_KCAL } from '@/stores/patientWeek'
import { integer, NO_VALUE } from '@/domain/format'

/**
 * What the day came to, against the goal.
 *
 * **The bar has two segments and they are not interchangeable.** The plan is
 * what the nutritionist prescribed; the extra is what was eaten beside it. A
 * day 300 kcal over because the week is heavy and a day 300 kcal over because
 * of an ice cream want different answers, and one bar of one colour would hide
 * which of the two happened.
 */

const props = withDefaults(
  defineProps<{
    day: PatientDay
    targetKcal: number
    /** True where the day is "today"; the design says so beside the name. */
    isToday?: boolean
    /** The tablet strip lays the same figures out along a row. */
    strip?: boolean
  }>(),
  { isToday: false, strip: false },
)

const planShare = computed(() =>
  Math.min(100, Math.round(((props.day.plan.kcal ?? 0) / props.targetKcal) * 100)),
)

const extraShare = computed(() =>
  Math.min(
    100 - planShare.value,
    Math.round(((props.day.extraKcal ?? 0) / props.targetKcal) * 100),
  ),
)

const off = computed(() => (props.day.kcal === null ? null : props.day.kcal - props.targetKcal))

const wide = computed(() => off.value !== null && Math.abs(off.value) > TOLERANCE_KCAL)

const verdict = computed(() => {
  if (off.value === null) {
    return 'Todavía no hay figuras para este día'
  }
  if (off.value > TOLERANCE_KCAL) {
    return `${integer(off.value)} kcal por encima del objetivo`
  }
  if (off.value < -TOLERANCE_KCAL) {
    return `${integer(-off.value)} kcal por debajo del objetivo`
  }
  return 'Dentro del objetivo del día'
})

/**
 * A total over part of the week is not the week's total. Most of a freshly
 * imported diet is unmatched, so this says how much of the day it stands on.
 */
const coverage = computed(() => {
  const plan = props.day.plan
  if (plan.ingredients === 0 || plan.counted === plan.ingredients) {
    return ''
  }
  return `Cuenta ${plan.counted} de ${plan.ingredients} ingredientes del plan`
})
</script>

<template>
  <div class="total" :class="{ strip }">
    <div class="headline">
      <div class="who">
        <span class="name">{{ day.name }}</span>
        <span class="sub">{{ isToday ? `hoy · ${day.date}` : day.longDate }}</span>
      </div>
      <div class="figures">
        <span class="srf num value">{{ integer(day.kcal) }}</span>
        <span class="num target">/ {{ integer(targetKcal) }} kcal</span>
      </div>
    </div>

    <div class="rest">
      <div class="bar">
        <div class="bar-plan" :style="{ width: `${planShare}%` }" />
        <div class="bar-extra" :style="{ width: `${extraShare}%` }" />
      </div>

      <div class="legend">
        <span class="key">
          <span class="swatch plan" />
          <span class="key-label">Plan</span>
          <span class="num key-value">{{ integer(day.plan.kcal) }}</span>
        </span>
        <span class="key">
          <span class="swatch extra" />
          <span class="key-label">Extra</span>
          <span class="num key-value">
            {{ day.extraKcal === null ? NO_VALUE : `+${integer(day.extraKcal)}` }}
          </span>
        </span>
        <span v-if="strip" class="verdict" :class="{ wide }">{{ verdict }}</span>
      </div>

      <p v-if="!strip" class="verdict block" :class="{ wide }">{{ verdict }}</p>
      <p v-if="!strip && coverage" class="coverage">{{ coverage }}</p>
    </div>
  </div>
</template>

<style scoped>
.total {
  flex: none;
  padding: 15px 16px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
}

.total.strip {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 12px 15px;
}

.strip .headline {
  flex: none;
}

.who {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.name {
  font-weight: 600;
  font-size: 12.5px;
  color: var(--ink-strong);
}

.sub {
  font-size: 11px;
  color: var(--ink-faint);
}

.figures {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.strip .figures {
  margin-top: 4px;
}

.value {
  font-size: 38px;
  line-height: 0.9;
  font-weight: 500;
  color: var(--ink-strong);
}

.strip .value {
  font-size: 26px;
}

.target {
  padding-bottom: 3px;
  font-size: 12px;
  color: var(--ink-muted);
}

.rest {
  margin-top: 12px;
}

.strip .rest {
  flex: 1 1 0;
  min-width: 0;
  margin-top: 0;
}

.bar {
  display: flex;
  gap: 2px;
  height: 7px;
  border-radius: 4px;
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

.legend {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 10px;
  font-size: 11.5px;
}

.key {
  display: flex;
  align-items: center;
  gap: 6px;
}

.swatch {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.swatch.plan {
  background: var(--sage-600);
}

.swatch.extra {
  background: var(--amber-400);
}

.key-label {
  color: var(--ink-muted);
}

.key-value {
  font-weight: 500;
  color: var(--ink);
}

.verdict {
  color: var(--ink-muted);
}

.verdict.wide {
  color: var(--amber-700);
}

.verdict:not(.block) {
  margin-left: auto;
}

.verdict.block {
  margin: 11px 0 0;
  padding-top: 11px;
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
}

.coverage {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--ink-faint);
}
</style>
