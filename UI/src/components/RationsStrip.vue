<script setup lang="ts">
import { computed, ref } from 'vue'
import type { DayOfWeek, DayRations, DietRations, RecommendationCheck } from '@/api/types'
import { integer, NO_VALUE } from '@/domain/format'
import {
  amount,
  range,
  recommendationText,
  STATUS_HINTS,
  STATUS_LABELS,
} from '@/domain/rations'
import { dayName } from '@/domain/week'
import { MEAL_ORDER } from '@/domain/slots'
import { useRations } from '@/stores/rations'

/**
 * The week read against its reference profile: rations per group for the day in
 * the rail, the week's frequencies, the day's energy by meal, and — on a clinical
 * diet only — carbohydrate rations.
 *
 * **Orientative, and always labelled so.** Every count is the backend's; ranges
 * stay ranges, a ceiling reads as a ceiling, and what could not be counted is
 * named with its reason beside the count rather than hidden under it.
 */

const props = defineProps<{
  rations: DietRations | null
  loading: boolean
  error: string | null
  day: DayOfWeek | null
  /** Cells with unpublished changes: the count is of the stored week. */
  dirtyCount: number
  profileLabel: string
}>()

const open = ref(false)

const store = useRations()

/** Which systems each system belongs to, read off the week's own definitions. */
const clinicalCodes = computed(
  () => new Set((props.rations?.exchangeSystems ?? []).filter((s) => s.clinical).map((s) => s.code)),
)

const today = computed<DayRations | null>(() => {
  if (!props.rations || !props.day) {
    return null
  }
  return props.rations.days.find((candidate) => candidate.day === props.day) ?? null
})

const coverage = computed(() => today.value?.coverage ?? null)

const counted = computed(() => {
  const of = coverage.value
  return of ? `${integer(of.counted)} / ${integer(of.ingredients)}` : NO_VALUE
})

/** The reasons an ingredient was left out, with how many share each. */
const reasons = computed(() => {
  const tally = new Map<string, string[]>()
  for (const item of today.value?.uncounted ?? []) {
    const names = tally.get(item.reason) ?? []
    names.push(item.name)
    tally.set(item.reason, names)
  }
  return [...tally.entries()].map(([reason, names]) => ({ reason, names }))
})

const meals = computed(() =>
  [...(today.value?.meals ?? [])].sort(
    (a, b) => MEAL_ORDER.indexOf(a.mealType) - MEAL_ORDER.indexOf(b.mealType),
  ),
)

/** The segments of the energy bar, each meal as a share of the day. */
const segments = computed(() =>
  meals.value
    .filter((meal) => meal.pct !== null && meal.pct > 0)
    .map((meal) => ({ ...meal, width: `${meal.pct}%` })),
)

/**
 * The clinical ration shows on a clinical diet regardless; the general 10 g
 * exchanges only while the exchanges view is on.
 */
const exchanges = computed(() =>
  (today.value?.exchanges ?? []).filter(
    (system) => clinicalCodes.value.has(system.code) || store.showExchanges.value,
  ),
)

function checkTitle(check: RecommendationCheck): string {
  const parts = [STATUS_HINTS[check.status]]
  if (check.note) {
    parts.push(check.note)
  }
  if (check.pageRef) {
    parts.push(check.pageRef)
  }
  return parts.join(' · ')
}

function mealName(type: string): string {
  return meals.value.find((meal) => meal.mealType === type)?.name ?? type
}
</script>

<template>
  <section class="rations" :class="{ open }">
    <div class="bar">
      <span class="legend">Raciones</span>
      <span class="badge" title="Recuento orientativo, no un diagnóstico">orientativo</span>
      <span class="profile" :title="rations?.profile?.context ?? ''">{{ profileLabel }}</span>

      <span v-if="loading && !rations" class="quiet">Contando…</span>
      <span v-else-if="error" class="quiet warn">{{ error }}</span>
      <span v-else-if="rations && !rations.profile" class="quiet">
        Elige una población de referencia para contar raciones.
      </span>

      <template v-else-if="rations">
        <span class="day">{{ day ? dayName(day) : '' }}</span>
        <span v-if="!today" class="quiet">Este día no tiene platos publicados.</span>
        <ul v-else class="checks">
          <li
            v-for="check in today.daily"
            :key="check.code"
            class="check"
            :class="check.status.toLowerCase()"
            :title="checkTitle(check)"
          >
            <span class="check-label">{{ check.label }}</span>
            <span class="num">{{ range(check.actualMin, check.actualMax) }}</span>
            <span class="check-target">/ {{ recommendationText(check) }}</span>
          </li>
        </ul>
        <span class="figure" title="Ingredientes del día que entran en el recuento">
          <span class="figure-label">Cuentan</span>
          <span
            class="num figure-value"
            :class="{ partial: coverage && coverage.counted < coverage.ingredients }"
          >
            {{ counted }}
          </span>
        </span>
      </template>

      <button
        class="toggle"
        type="button"
        :aria-pressed="store.showExchanges.value"
        :disabled="!rations"
        title="Leer cada día y cada plato también en intercambios de 10 g de hidratos, proteína y grasa"
        @click="store.toggleExchanges()"
      >
        {{ store.showExchanges.value ? 'Sin intercambios' : 'Intercambios' }}
      </button>
      <button
        class="toggle"
        type="button"
        :aria-expanded="open"
        :disabled="!rations"
        @click="open = !open"
      >
        {{ open ? 'Ocultar' : 'Detalle' }}
      </button>
    </div>

    <div v-if="open && rations" class="detail scroll">
      <p v-if="dirtyCount > 0" class="note warn">
        Se cuenta la semana publicada: {{ dirtyCount }}
        {{ dirtyCount === 1 ? 'plato con cambios no entra' : 'platos con cambios no entran' }} hasta
        publicar.
      </p>

      <div class="columns">
        <div v-if="rations.profile" class="column">
          <h3 class="title">Grupos · {{ day ? dayName(day) : '' }}</h3>
          <p v-if="!today || today.groups.length === 0" class="note">Nada contado este día.</p>
          <table v-else class="table">
            <tbody>
              <tr v-for="group in today.groups" :key="group.groupCode">
                <td>{{ group.groupLabel }}</td>
                <td class="num right">{{ integer(group.grams) }} g</td>
                <td class="num right strong">
                  {{ range(group.rationsMin, group.rationsMax) }}
                  {{ group.rationsMax === 1 && group.rationsMin === 1 ? 'ración' : 'raciones' }}
                </td>
              </tr>
            </tbody>
          </table>

          <h3 class="title">Semana</h3>
          <ul class="checks stacked">
            <li
              v-for="check in rations.weekly"
              :key="check.code"
              class="check"
              :class="check.status.toLowerCase()"
              :title="checkTitle(check)"
            >
              <span class="check-label">{{ check.label }}</span>
              <span class="num">{{ range(check.actualMin, check.actualMax) }}</span>
              <span class="check-target">/ {{ recommendationText(check) }}</span>
              <span class="check-status">{{ STATUS_LABELS[check.status] }}</span>
            </li>
          </ul>
          <p class="note">
            Días con platos: {{ rations.daysInWeek }} de 7. Una frecuencia semanal se lee sobre
            esos días.
          </p>
        </div>

        <div class="column">
          <h3 class="title">Energía por comida · {{ day ? dayName(day) : '' }}</h3>
          <p v-if="segments.length === 0" class="note">Sin energía contada este día.</p>
          <template v-else>
            <div class="energy" role="img" aria-label="Reparto de la energía del día por comidas">
              <span
                v-for="segment in segments"
                :key="segment.mealType"
                class="segment"
                :style="{ width: segment.width }"
                :title="`${segment.name}: ${integer(segment.kcal)} kcal, ${amount(segment.pct, 1)} %`"
              />
            </div>
            <table class="table">
              <tbody>
                <tr v-for="meal in meals" :key="meal.mealType">
                  <td>{{ meal.name }}</td>
                  <td class="num right">{{ integer(meal.kcal) }} kcal</td>
                  <td class="num right strong">{{ meal.pct === null ? NO_VALUE : `${amount(meal.pct, 1)} %` }}</td>
                  <td class="num right faint">
                    <template v-if="meal.targetPctMin !== null">
                      ref. {{ range(meal.targetPctMin, meal.targetPctMax, 1) }} %
                    </template>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="rations.mealShares" class="note">
              Referencia: {{ rations.mealShares.sourceShortName }}
              <template v-if="rations.mealShares.pageRef">({{ rations.mealShares.pageRef }})</template>.
              <strong v-if="rations.mealShares.borrowed" class="borrowed">
                Tomado de «{{ rations.mealShares.fromProfileLabel }}».
              </strong>
              {{ rations.mealShares.note }}
            </p>
          </template>

          <template v-if="exchanges.length > 0">
            <h3 class="title">Intercambios · {{ day ? dayName(day) : '' }}</h3>
            <div v-for="system in exchanges" :key="system.code" class="exchange">
              <div class="exchange-head">
                <span>
                  {{ system.name }} ({{ amount(system.gramsPerUnit) }} g)
                  <span v-if="clinicalCodes.has(system.code)" class="faint">· clínico</span>
                </span>
                <span class="num strong">{{ amount(system.dayUnits, 1) }} al día</span>
              </div>
              <ul class="exchange-meals">
                <li v-for="meal in system.meals" :key="meal.mealType">
                  {{ mealName(meal.mealType) }}
                  <span class="num">{{ amount(meal.units, 1) }}</span>
                </li>
              </ul>
            </div>
            <p class="note">
              Sobre los gramos de lo que cuenta en kcal, calculados con CIQUAL y BLS; lo que no cuenta, no
              suma. Por plato, en el panel del plato.
            </p>
          </template>
        </div>

        <div v-if="reasons.length > 0" class="column">
          <h3 class="title">Sin contar · {{ day ? dayName(day) : '' }}</h3>
          <div v-for="group in reasons" :key="group.reason" class="reason">
            <span class="reason-head">
              {{ group.reason }} <span class="num faint">{{ group.names.length }}</span>
            </span>
            <span class="reason-names">{{ group.names.join(' · ') }}</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.rations {
  flex: none;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.bar {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 6px 24px;
  flex-wrap: wrap;
}

.legend {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.badge {
  padding: 1px 6px;
  border: 1px solid var(--line);
  border-radius: 3px;
  font-size: 10px;
  color: var(--ink-muted);
}

.profile {
  font-size: 11.5px;
  color: var(--ink-muted);
  white-space: nowrap;
}

.day {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-strong);
}

.quiet {
  font-size: 11.5px;
  color: var(--ink-faint);
}

.warn {
  color: var(--amber-700);
}

.checks {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.checks.stacked {
  flex-direction: column;
  gap: 4px;
}

.check {
  display: flex;
  align-items: baseline;
  gap: 5px;
  padding: 3px 8px;
  border: 1px solid var(--line-soft);
  border-radius: 3px;
  font-size: 11.5px;
  color: var(--ink);
  background: var(--surface-muted);
}

.check-label {
  color: var(--ink-strong);
}

.check-target {
  color: var(--ink-faint);
}

.check-status {
  margin-left: auto;
  font-size: 10.5px;
}

.check.within {
  border-color: var(--sage-200);
  background: var(--sage-50);
}

.check.within .check-status {
  color: var(--sage-700);
}

.check.below,
.check.above {
  border-color: var(--extra-line);
  background: var(--amber-50);
}

.check.below .check-status,
.check.above .check-status {
  color: var(--amber-700);
}

.check.uncertain .check-status {
  color: var(--ink-muted);
}

.figure {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-left: auto;
}

.figure-label {
  font-size: 11px;
  color: var(--ink-muted);
}

.figure-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-strong);
}

.figure-value.partial {
  color: var(--amber-700);
}

.toggle {
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 11.5px;
  color: var(--ink-muted);
}

.toggle:disabled {
  color: var(--ink-disabled);
}

.detail {
  max-height: 300px;
  overflow-y: auto;
  padding: 4px 24px 14px;
  border-top: 1px solid var(--line-soft);
}

.columns {
  display: flex;
  flex-wrap: wrap;
  gap: 18px 32px;
}

.column {
  flex: 1 1 260px;
  min-width: 0;
}

.title {
  margin: 12px 0 6px;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11.5px;
}

.table td {
  padding: 3px 0;
  border-bottom: 1px solid var(--line-soft);
  color: var(--ink);
}

.right {
  text-align: right;
  padding-left: 10px !important;
  white-space: nowrap;
}

.strong {
  color: var(--ink-strong) !important;
  font-weight: 500;
}

.faint {
  color: var(--ink-faint) !important;
}

.note {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.borrowed {
  font-weight: 500;
  color: var(--amber-700);
}

.energy {
  display: flex;
  height: 10px;
  margin-bottom: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--line-soft);
}

.segment {
  height: 100%;
  border-right: 2px solid var(--surface);
  background: var(--macro-carbs);
}

.segment:nth-child(2n) {
  background: var(--sage-600);
}

.exchange {
  font-size: 11.5px;
  color: var(--ink);
}

.exchange-head {
  display: flex;
  justify-content: space-between;
}

.exchange-meals {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
  color: var(--ink-muted);
}

.reason {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
  font-size: 11.5px;
}

.reason-head {
  color: var(--ink-strong);
}

.reason-names {
  color: var(--ink-faint);
  line-height: 1.4;
}
</style>
