<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import PatientHeader from '@/components/PatientHeader.vue'
import PatientGoalStrip from '@/components/PatientGoalStrip.vue'
import PatientWeekGrid from '@/components/PatientWeekGrid.vue'
import PatientDayList from '@/components/PatientDayList.vue'
import PatientDayCards from '@/components/PatientDayCards.vue'
import DayPills from '@/components/DayPills.vue'
import DaySummary from '@/components/DaySummary.vue'
import DayTotalCard from '@/components/DayTotalCard.vue'
import ExtraFoodPanel from '@/components/ExtraFoodPanel.vue'
import { usePatientWeek } from '@/stores/patientWeek'
import { useViewport } from '@/composables/useViewport'
import { integer } from '@/domain/format'

/**
 * What the patient sees. **The plan is read-only here** — the nutritionist
 * writes the week and publishes it, and this shows whatever was last published.
 *
 * Three layouts, one rule behind them:
 *
 * - **Desktop** — the dense week beside the day's own summary.
 * - **Tablet (834 px)** — the same grid, tighter, with that summary laid across
 *   the top instead of down the side.
 * - **Phone** — no grid at all, and so no Semana/Día switch: seven columns of a
 *   sentence each cannot be read at that width. The day is the view, the total
 *   is pinned to the bottom, and the card that opens on hover elsewhere opens
 *   on a press here.
 */

const week = usePatientWeek()
const { mobile, tablet, desktop } = useViewport()

/** Whether the "add an extra" panel is open, and never on both sides at once. */
const adding = ref(false)

onMounted(() => {
  if (week.status.value === 'idle') {
    void week.load()
  }
})

/** A phone has no week grid, so it can never be left showing one. */
watch(mobile, (isMobile) => {
  if (isMobile) {
    week.show('day')
  }
})

const showsWeek = computed(() => week.view.value === 'week' && !mobile.value)

/**
 * Which day the summary is about. In the day view it is the day being read; in
 * the week view it is the one selected, so the rail always has a subject.
 */
const day = computed(() => week.today.value)

const isToday = computed(() => day.value?.isToday ?? false)

function openAdd(): void {
  adding.value = true
}
</script>

<template>
  <div class="screen">
    <PatientHeader
      :diet-name="week.diet.value?.name ?? ''"
      :day-count="week.days.value.length"
      :monday="week.monday.value"
      :view="week.view.value"
      :show-switch="!mobile"
      :compact="mobile"
      @show="week.show($event)"
    />

    <PatientGoalStrip
      :goal="week.goal.value"
      :goal-note="week.goalNote.value"
      :week-average-kcal="week.weekAverageKcal.value"
      :week="week.weekCoverage.value"
      :average-score="week.journal.value?.averageScore ?? null"
      :scored="week.journal.value?.scored ?? 0"
      :compact="mobile"
    />

    <p v-if="week.error.value && week.status.value === 'ready'" class="banner">
      {{ week.error.value }}
    </p>

    <div v-if="week.status.value === 'loading'" class="state">Cargando tu dieta…</div>

    <div v-else-if="week.status.value === 'error'" class="state">
      <p>{{ week.error.value }}</p>
      <button class="retry" type="button" @click="week.load()">Reintentar</button>
    </div>

    <!-- ---------------------------------------------------------------- phone -->
    <template v-else-if="mobile">
      <div class="strip">
        <DayPills
          scrolling
          :days="week.days.value"
          :selected="week.selectedDay.value"
          @pick="week.selectDay($event)"
        />
      </div>

      <div class="phone-body scroll">
        <PatientDayCards
          v-if="day"
          :rows="week.rows.value"
          :day="day"
          @add="openAdd()"
        />
      </div>

      <!-- The total stays in sight while the day is scrolled: it is the one
           figure the whole screen is about. -->
      <div v-if="day" class="bottom">
        <div class="bottom-top">
          <span class="srf num bottom-total">{{ integer(day.kcal) }}</span>
          <span class="num bottom-target">/ {{ integer(week.targetKcal.value) }} kcal</span>
          <button class="bottom-add" type="button" @click="openAdd()">
            <svg
              width="15"
              height="15"
              viewBox="0 0 14 14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              aria-hidden="true"
            >
              <path d="M7 3v8M3 7h8" />
            </svg>
            Añadir
          </button>
        </div>
        <DayTotalCard
          class="bottom-bar"
          strip
          :day="day"
          :target-kcal="week.targetKcal.value"
          :is-today="isToday"
        />
      </div>

      <!-- A sheet rather than a rail: there is no room beside anything here. -->
      <div v-if="adding && day" class="sheet-scrim" @click.self="adding = false">
        <ExtraFoodPanel
          sheet
          :day="day.day"
          :day-name="day.name"
          @close="adding = false"
        />
      </div>
    </template>

    <!-- --------------------------------------------------------- tablet / desktop -->
    <template v-else>
      <!-- Tablet has no room for a rail, so the day's summary runs across the top. -->
      <div v-if="tablet && day" class="tablet-strip">
        <DayTotalCard
          strip
          :day="day"
          :target-kcal="week.targetKcal.value"
          :is-today="isToday"
        />
        <button class="strip-add" type="button" @click="openAdd()">
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

      <div class="body">
        <div class="main scroll">
          <PatientWeekGrid
            v-if="showsWeek"
            :rows="week.rows.value"
            :days="week.days.value"
            :target-kcal="week.targetKcal.value"
            :dense="tablet"
            :selected="week.selectedDay.value"
            @pick="week.selectDay($event)"
          />

          <div v-else class="day-view">
            <DayPills
              :days="week.days.value"
              :selected="week.selectedDay.value"
              @pick="week.selectDay($event)"
            />
            <PatientDayList
              v-if="day"
              :rows="week.rows.value"
              :day="day"
              @add="openAdd()"
            />
          </div>
        </div>

        <!-- One rail, two jobs: the day's summary, or the catalogue while
             something is being added to it. -->
        <aside v-if="desktop && day" class="rail">
          <ExtraFoodPanel
            v-if="adding"
            :day="day.day"
            :day-name="day.name"
            @close="adding = false"
          />
          <DaySummary
            v-else
            :day="day"
            :target-kcal="week.targetKcal.value"
            :target-protein-g="week.targetProteinG.value"
            :is-today="isToday"
            @add="openAdd()"
          />
        </aside>
      </div>

      <!-- Tablet keeps the grid full width, so the catalogue arrives over it. -->
      <div v-if="tablet && adding && day" class="sheet-scrim tablet" @click.self="adding = false">
        <div class="floating">
          <ExtraFoodPanel :day="day.day" :day-name="day.name" @close="adding = false" />
        </div>
      </div>
    </template>
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

.main {
  flex: 1 1 0;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 18px 20px 20px 24px;
}

.rail {
  width: 312px;
  flex: none;
  display: flex;
  min-height: 0;
  padding: 18px 24px 20px 4px;
}

.day-view {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tablet-strip {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: none;
  margin: 14px 18px 0;
}

.tablet-strip > :first-child {
  flex: 1 1 0;
  min-width: 0;
}

.strip-add {
  flex: none;
  display: flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 13px;
  border: 1px dashed var(--extra-dash);
  border-radius: var(--radius);
  color: var(--extra-ink);
  font-weight: 500;
  font-size: 12px;
}

@media (max-width: 1239px) {
  .main {
    padding: 14px 18px 18px;
  }
}

/* ------------------------------------------------------------------ phone */
.strip {
  flex: none;
  padding: 12px 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  overflow: hidden;
}

.phone-body {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 16px 16px;
}

.bottom {
  flex: none;
  padding: 12px 16px 16px;
  background: var(--surface);
  border-top: 1px solid var(--line);
}

.bottom-top {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.bottom-total {
  font-size: 28px;
  line-height: 0.95;
  font-weight: 500;
  color: var(--ink-strong);
}

.bottom-target {
  padding-bottom: 2px;
  font-size: 12.5px;
  color: var(--ink-muted);
}

.bottom-add {
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 44px;
  padding: 0 16px;
  border-radius: 8px;
  background: var(--sage-700);
  color: var(--surface);
  font-weight: 500;
  font-size: 13.5px;
}

/* Only the bar and its verdict are wanted down here; the headline is already
   the two lines above it. */
.bottom-bar {
  margin-top: 10px;
  padding: 0;
  border: 0;
  background: none;
}

.bottom-bar :deep(.headline) {
  display: none;
}

.sheet-scrim {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background: rgba(22, 28, 24, 0.34);
}

.sheet-scrim.tablet {
  justify-content: center;
  align-items: center;
  padding: 24px;
}

.floating {
  display: flex;
  width: 420px;
  max-width: 100%;
  height: min(620px, 100%);
}

/* ------------------------------------------------------------------ states */
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
