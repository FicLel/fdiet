<script setup lang="ts">
import { computed } from 'vue'
import AppLogo from './AppLogo.vue'
import PatientSelect from './PatientSelect.vue'
import type { PatientView } from '@/stores/patientWeek'
import { weekLabel } from '@/domain/week'

/**
 * The patient's chrome: whose diet, which diet, which week, and which of the
 * two views.
 *
 * The Semana/Día switch is absent on a phone rather than disabled — there is no
 * week grid to switch to at that width, so offering the choice would be
 * offering nothing.
 *
 * The selector is the same one the builder carries, and it is not a login: with
 * no security layer anywhere, this screen shows whichever patient is picked and
 * every patient can be picked. A real patient-facing deployment would fix the
 * selection to the person logged in, and there is nobody logged in yet.
 */

const props = defineProps<{
  dietName: string
  dayCount: number
  monday: Date
  view: PatientView
  /** Below the tablet breakpoint there is no week grid, so no switch either. */
  showSwitch: boolean
  compact: boolean
}>()

defineEmits<{ show: [view: PatientView] }>()

const label = computed(() => `Semana del ${weekLabel(props.monday)}`)

const days = computed(() => (props.dayCount === 1 ? '1 día' : `${props.dayCount} días`))
</script>

<template>
  <header class="header" :class="{ compact }">
    <AppLogo v-if="!compact" />
    <span v-if="!compact" class="divider" />

    <span class="diet">
      <span class="diet-name">{{ dietName }}</span>
      <span class="num diet-days">{{ days }}</span>
    </span>

    <span v-if="!compact" class="num week">{{ label }}</span>

    <span class="spacer" />

    <div v-if="showSwitch" class="switch" role="tablist">
      <button
        class="tab"
        :class="{ current: view === 'week' }"
        type="button"
        role="tab"
        :aria-selected="view === 'week'"
        @click="$emit('show', 'week')"
      >
        Semana
      </button>
      <button
        class="tab"
        :class="{ current: view === 'day' }"
        type="button"
        role="tab"
        :aria-selected="view === 'day'"
        @click="$emit('show', 'day')"
      >
        Día
      </button>
    </div>

    <PatientSelect :compact="compact" />

    <RouterLink v-if="!compact" class="to-builder" to="/dieta" title="Vista del nutricionista">
      Editar la dieta
    </RouterLink>
  </header>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 60px;
  flex: none;
  padding: 0 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.header.compact {
  gap: 10px;
  height: 56px;
  padding: 0 16px;
}

.divider {
  width: 1px;
  height: 22px;
  background: var(--line);
}

.diet {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.diet-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--ink-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.compact .diet-name {
  font-size: 15px;
}

.diet-days {
  font-size: 11px;
  color: var(--ink-muted);
  white-space: nowrap;
}

.week {
  color: var(--ink-muted);
  white-space: nowrap;
}

.spacer {
  flex: 1 1 0;
}

.switch {
  display: flex;
  gap: 2px;
  flex: none;
  padding: 2px;
  background: #f1f4f2;
  border-radius: 5px;
}

.tab {
  height: 28px;
  padding: 0 14px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--ink-muted);
}

.tab.current {
  background: var(--surface);
  color: var(--ink-strong);
  box-shadow: 0 1px 2px rgba(22, 28, 24, 0.1);
}

.to-builder {
  flex: none;
  height: 28px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 11.5px;
  color: var(--ink-muted);
  white-space: nowrap;
}

.to-builder:hover {
  border-color: var(--line-input);
  color: var(--sage-700);
}
</style>
