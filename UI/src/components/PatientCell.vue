<script setup lang="ts">
import { computed } from 'vue'
import StarRating from './StarRating.vue'
import MacroBars from './MacroBars.vue'
import RecipeDetail from './RecipeDetail.vue'
import type { DayOfWeek } from '@/api/types'
import type { MealRow } from '@/domain/slots'
import { usePatientWeek, slotKey } from '@/stores/patientWeek'
import { integer, percent } from '@/domain/format'
import { complete } from '@/domain/nutrition'

/**
 * One plate of the week as the patient reads it.
 *
 * The cell itself is the plate's description and a figure; everything else —
 * the recipe (what goes in it, at this plate's servings, and how it is made),
 * the macros, the stars — is in a card that opens over it. The card
 * opens on hover where there is a cursor and on a press everywhere, and the
 * press is what pins it: a touch screen has no hover, and a card that vanished
 * the moment a finger moved could not be scored from.
 */

const props = defineProps<{
  row: MealRow
  day: DayOfWeek
  dayName: string
  dayDate: string
  /** Near the right edge, so the card opens to the left instead. */
  alignRight: boolean
  /** Low in the grid, so the card hangs from the bottom instead. */
  alignBottom: boolean
}>()

const week = usePatientWeek()

const key = computed(() => slotKey(props.row, props.day))
const text = computed(() => week.textFor(props.row, props.day))
const dish = computed(() => week.dishFor(props.row, props.day))
const totals = computed(() => week.totalsFor(props.row, props.day))
const score = computed(() => week.scoreFor(props.row, props.day))
const pinned = computed(() => week.openCell.value === key.value)

/**
 * A partial figure is still worth reading, so it is shown — and marked, so it
 * is never taken for the whole plate. An ingredient nobody has matched yet is
 * the usual reason, and that is the nutritionist's queue, not the patient's.
 */
const kcal = computed(() => {
  if (totals.value.ingredients === 0) {
    return ''
  }
  if (totals.value.kcal === null) {
    return 'sin datos'
  }
  return integer(totals.value.kcal)
})

const partial = computed(() => totals.value.kcal !== null && !complete(totals.value))

const share = computed(() =>
  totals.value.kcal === null
    ? ''
    : `${percent(totals.value.kcal, week.targetKcal.value)} del objetivo`,
)

const slotLabel = computed(() =>
  props.row.sub ? `${props.row.sub} · ${props.row.label}` : props.row.label,
)
</script>

<template>
  <div class="cell" :class="{ pinned, empty: text === '' }">
    <button
      class="face"
      type="button"
      :aria-expanded="pinned"
      :aria-label="`${slotLabel}, ${dayName} ${dayDate}`"
      @click="week.toggleCell(key)"
    >
      <span class="text">{{ text }}</span>
      <span class="foot">
        <span class="num kcal" :class="{ partial }">{{ kcal }}</span>
        <span class="score" :class="{ given: score > 0 }">
          <svg
            width="10"
            height="10"
            viewBox="0 0 12 12"
            :fill="score > 0 ? 'currentColor' : 'none'"
            stroke="currentColor"
            stroke-width="1.2"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M6 1.4l1.4 3 3.2.4-2.4 2.2.6 3.2L6 8.7l-2.8 1.5.6-3.2L1.4 4.8l3.2-.4z" />
          </svg>
          <span class="num">{{ score > 0 ? score : '—' }}</span>
        </span>
      </span>
    </button>

    <div
      v-if="text !== ''"
      class="card"
      :class="{ left: alignRight, bottom: alignBottom, pinned }"
    >
      <div class="card-head">
        <span class="card-slot">{{ slotLabel }}</span>
        <span class="card-day">{{ dayName }} {{ dayDate }}</span>
      </div>

      <p class="card-text">{{ text }}</p>

      <RecipeDetail :dish="dish" />

      <div v-if="totals.ingredients > 0" class="card-figures">
        <span class="srf num card-kcal">{{ kcal }}</span>
        <span class="card-unit">kcal</span>
        <span class="num card-share">{{ share }}</span>
      </div>

      <p v-if="partial" class="card-partial">
        Cuenta {{ totals.counted }} de {{ totals.ingredients }} ingredientes; el resto está
        pendiente de vincular.
      </p>

      <MacroBars
        v-if="totals.ingredients > 0"
        class="card-macros"
        compact
        :protein-g="totals.proteinG"
        :carbohydrates-g="totals.carbohydratesG"
        :fat-g="totals.fatG"
      />

      <div class="card-score">
        <span class="card-score-label">Puntúa el plato</span>
        <StarRating
          :score="score"
          :disabled="week.saving.value"
          @pick="week.score(row, day, $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.cell {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  background: var(--surface);
}

.cell:hover,
.cell.pinned {
  z-index: 50;
}

.face {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 6px 9px 5px;
  text-align: left;
}

.cell:not(.empty) .face:hover {
  background: #f4f7f5;
}

.cell.pinned .face {
  background: var(--sage-50);
}

.text {
  display: -webkit-box;
  flex: 1 1 0;
  min-height: 0;
  font-size: 11.5px;
  line-height: 1.32;
  color: var(--ink);
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.kcal {
  font-size: 10.5px;
  font-weight: 500;
  color: var(--ink-muted);
}

.kcal.partial {
  color: var(--ink-faint);
}

.score {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  font-size: 10.5px;
  color: #c2ccc6;
}

.score.given {
  color: var(--sage-700);
}

/* The card. Hidden until the cell is hovered or pressed; `visibility` rather
   than `display` so the fade has something to animate. */
.card {
  position: absolute;
  z-index: 60;
  top: -12px;
  left: calc(100% + 9px);
  width: 320px;
  padding: 14px 15px 12px;
  background: var(--surface);
  border: 1px solid #dfe6e1;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  opacity: 0;
  visibility: hidden;
  transform: translateY(3px);
  transition: opacity 0.12s ease, transform 0.12s ease;
  pointer-events: none;
}

.card.left {
  left: auto;
  right: calc(100% + 9px);
}

.card.bottom {
  top: auto;
  bottom: -12px;
}

.cell:hover .card,
.card.pinned {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
  pointer-events: auto;
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.card-slot {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.card-day {
  font-size: 11px;
  color: var(--ink-faint);
}

.card-text {
  margin: 8px 0 0;
  font-size: 12.5px;
  line-height: 1.45;
  color: var(--ink-strong);
}

.card-figures {
  display: flex;
  align-items: flex-end;
  gap: 9px;
  margin-top: 13px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}

.card-kcal {
  font-size: 27px;
  line-height: 0.95;
  font-weight: 500;
  color: var(--ink-strong);
}

.card-unit {
  font-size: 11.5px;
  color: var(--ink-muted);
  padding-bottom: 2px;
}

.card-share {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--ink-muted);
  padding-bottom: 2px;
}

.card-partial {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.card-macros {
  margin-top: 12px;
}

.card-score {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px solid var(--line-soft);
}

.card-score-label {
  font-size: 11.5px;
  color: var(--ink-muted);
}

.card-score :deep(.stars) {
  margin-left: auto;
}
</style>
