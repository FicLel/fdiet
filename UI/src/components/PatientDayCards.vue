<script setup lang="ts">
import { computed } from 'vue'
import StarRating from './StarRating.vue'
import MacroBars from './MacroBars.vue'
import ExtraFoodRow from './ExtraFoodRow.vue'
import type { PatientDay } from '@/stores/patientWeek'
import { slotKey, usePatientWeek } from '@/stores/patientWeek'
import type { GridRow } from '@/domain/slots'
import { integer } from '@/domain/format'
import { complete } from '@/domain/nutrition'

/**
 * The day on a phone: a card per plate, opened by pressing it.
 *
 * **There is no hover here**, so the nutrition card of the wider screens
 * becomes the body of an accordion — a press opens it, and the stars are inside
 * where a finger can reach them. Targets are 44 px, which is why the stars are
 * drawn larger than anywhere else.
 */

const props = defineProps<{
  rows: GridRow[]
  day: PatientDay
}>()

defineEmits<{ add: [] }>()

const week = usePatientWeek()

const lines = computed(() =>
  props.rows.map((row) => {
    if (row.kind === 'band') {
      return { kind: 'band' as const, key: row.key, label: row.label }
    }
    const totals = week.totalsFor(row, props.day.day)
    const key = slotKey(row, props.day.day)
    return {
      kind: 'meal' as const,
      key: row.key,
      slot: key,
      row,
      label: row.label,
      sub: row.sub,
      text: week.textFor(row, props.day.day),
      score: week.scoreFor(row, props.day.day),
      kcal: totals.ingredients === 0 ? '' : integer(totals.kcal),
      partial: totals.kcal !== null && !complete(totals),
      counted: totals.counted,
      ingredients: totals.ingredients,
      proteinG: totals.proteinG,
      carbohydratesG: totals.carbohydratesG,
      fatG: totals.fatG,
      open: week.openCell.value === key,
    }
  }),
)
</script>

<template>
  <div class="cards">
    <template v-for="line in lines" :key="line.key">
      <div v-if="line.kind === 'band'" class="band">
        <span class="band-label">{{ line.label }}</span>
        <span class="band-rule" />
      </div>

      <div v-else class="card" :class="{ open: line.open }">
        <button
          class="face"
          type="button"
          :aria-expanded="line.open"
          @click="week.toggleCell(line.slot)"
        >
          <span class="body">
            <span class="head">
              <span class="label">{{ line.label }}</span>
              <span v-if="line.sub" class="sub">{{ line.sub }}</span>
              <span v-if="line.score > 0" class="given">
                <svg
                  width="11"
                  height="11"
                  viewBox="0 0 12 12"
                  fill="currentColor"
                  stroke="currentColor"
                  stroke-width="1.2"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <path d="M6 1.4l1.4 3 3.2.4-2.4 2.2.6 3.2L6 8.7l-2.8 1.5.6-3.2L1.4 4.8l3.2-.4z" />
                </svg>
                <span class="num">{{ line.score }}</span>
              </span>
            </span>
            <span class="text" :class="{ full: line.open }">{{ line.text }}</span>
          </span>

          <span class="figure">
            <span class="srf num kcal" :class="{ partial: line.partial }">{{ line.kcal }}</span>
            <span class="unit">kcal</span>
            <span class="chevron" :class="{ up: line.open }">
              <svg
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M3.5 5.5 7 9l3.5-3.5" />
              </svg>
            </span>
          </span>
        </button>

        <div v-if="line.open" class="detail">
          <MacroBars
            :protein-g="line.proteinG"
            :carbohydrates-g="line.carbohydratesG"
            :fat-g="line.fatG"
          />
          <p v-if="line.partial" class="partial-note">
            Cuenta {{ line.counted }} de {{ line.ingredients }} ingredientes.
          </p>
          <div class="score">
            <span class="score-label">Puntúa el plato</span>
            <StarRating
              :score="line.score"
              :size="20"
              :disabled="week.saving.value"
              @pick="week.score(line.row, day.day, $event)"
            />
          </div>
        </div>
      </div>
    </template>

    <div class="band">
      <span class="band-label off">Fuera del plan</span>
      <span class="band-rule" />
    </div>

    <ExtraFoodRow
      v-for="extra in day.extras"
      :key="extra.id"
      large
      :extra="extra"
      :disabled="week.saving.value"
      @remove="week.removeExtra(extra.id)"
    />

    <p v-if="day.extras.length === 0" class="none">
      Nada fuera del plan este día. Si comes algo distinto, añádelo y se suma al total.
    </p>

    <button class="add" type="button" @click="$emit('add')">
      <svg
        width="15"
        height="15"
        viewBox="0 0 14 14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
        aria-hidden="true"
      >
        <path d="M7 3v8M3 7h8" />
      </svg>
      Añadir comida extra
    </button>
  </div>
</template>

<style scoped>
.cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.band {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 2px 2px;
}

.band-label {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.band-label.off {
  color: var(--extra-ink);
}

.band-rule {
  flex: 1 1 0;
  height: 1px;
  background: var(--line);
}

.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
}

.card.open {
  border-color: var(--sage-200);
}

.face {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  min-height: 68px;
  padding: 12px 14px;
  text-align: left;
}

.body {
  flex: 1 1 0;
  min-width: 0;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-strong);
}

.sub {
  font-size: 11px;
  color: var(--ink-faint);
}

.given {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--sage-700);
}

.text {
  display: -webkit-box;
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--ink);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.text.full {
  -webkit-line-clamp: 12;
}

.figure {
  flex: none;
  text-align: right;
}

.kcal {
  display: block;
  font-size: 17px;
  font-weight: 500;
  color: var(--ink-strong);
}

.kcal.partial {
  color: var(--ink-muted);
}

.unit {
  display: block;
  font-size: 10.5px;
  color: var(--ink-faint);
}

.chevron {
  display: flex;
  justify-content: flex-end;
  margin-top: 5px;
  color: var(--ink-disabled);
}

.chevron.up {
  transform: rotate(180deg);
}

.detail {
  padding: 12px 14px 14px;
  border-top: 1px solid var(--line-soft);
}

.partial-note {
  margin: 10px 0 0;
  font-size: 11.5px;
  color: var(--ink-faint);
}

/* Five 44 px targets and the label come to within a few pixels of a 390 px
   screen. Rather than clip the label, the stars drop to a line of their own on
   the narrowest phones — the target is the thing that must not shrink. */
.score {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 13px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}

.score-label {
  flex: none;
  font-size: 12.5px;
  color: var(--ink-muted);
  white-space: nowrap;
}

.score :deep(.stars) {
  flex: none;
}

.score :deep(.stars) {
  margin-left: auto;
}

/* 20 px of star inside 12 px of padding is a 44 px target — the smallest a
   finger can be asked to hit, and the reason these stars are drawn larger
   here than anywhere else. */
.score :deep(.star) {
  padding: 12px;
}

.score :deep(.stars) {
  margin-right: -12px;
}

.none {
  margin: 0;
  padding: 2px 2px 0;
  font-size: 12.5px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 48px;
  margin-top: 2px;
  border: 1px dashed var(--extra-dash);
  border-radius: 8px;
  color: var(--extra-ink);
  font-weight: 500;
  font-size: 13.5px;
}
</style>
