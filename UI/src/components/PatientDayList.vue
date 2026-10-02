<script setup lang="ts">
import { computed } from 'vue'
import StarRating from './StarRating.vue'
import ExtraFoodRow from './ExtraFoodRow.vue'
import RecipeDetail from './RecipeDetail.vue'
import type { PatientDay } from '@/stores/patientWeek'
import { slotKey, usePatientWeek } from '@/stores/patientWeek'
import type { GridRow } from '@/domain/slots'
import { grams, integer, NO_VALUE } from '@/domain/format'
import { complete } from '@/domain/nutrition'

/**
 * One day read down the page: every plate in the order it is eaten, then what
 * was eaten beside them.
 *
 * The off-plan entries sit under a band of their own rather than mixed in with
 * the meals. They are the same day and a different thing — the plan is what was
 * prescribed, these are what happened — and the day's bar keeps them apart for
 * the same reason.
 */

const props = defineProps<{
  rows: GridRow[]
  day: PatientDay
}>()

defineEmits<{ add: [] }>()

const week = usePatientWeek()

function macro(value: number | null): string {
  return value === null ? NO_VALUE : `${grams(value)} g`
}

/** The day's rows read once, rather than four lookups per cell in the template. */
const lines = computed(() =>
  props.rows.map((row) => {
    if (row.kind === 'band') {
      return { kind: 'band' as const, key: row.key, label: row.label }
    }
    const totals = week.totalsFor(row, props.day.day)
    return {
      kind: 'meal' as const,
      key: row.key,
      row,
      label: row.label,
      sub: row.sub,
      text: week.textFor(row, props.day.day),
      slot: slotKey(row, props.day.day),
      dish: week.dishFor(row, props.day.day),
      open: week.openCell.value === slotKey(row, props.day.day),
      score: week.scoreFor(row, props.day.day),
      kcal: totals.ingredients === 0 ? '' : integer(totals.kcal),
      partial: !complete(totals),
      macros: [
        { abbr: 'Prot', value: macro(totals.proteinG) },
        { abbr: 'HC', value: macro(totals.carbohydratesG) },
        { abbr: 'Grasa', value: macro(totals.fatG) },
      ],
    }
  }),
)
</script>

<template>
  <div class="list">
    <template v-for="line in lines" :key="line.key">
      <div v-if="line.kind === 'band'" class="band">
        <span class="band-label">{{ line.label }}</span>
      </div>

      <div v-else class="line" :class="{ open: line.open }">
        <div class="row">
          <div class="slot">
            <div class="slot-label">{{ line.label }}</div>
            <div v-if="line.sub" class="slot-sub">{{ line.sub }}</div>
          </div>

          <button
            class="text"
            type="button"
            :aria-expanded="line.open"
            :title="line.dish?.recipe ? 'Ver la receta' : undefined"
            @click="week.toggleCell(line.slot)"
          >
            {{ line.text }}
          </button>

          <div class="macros">
            <span v-for="macroOf in line.macros" :key="macroOf.abbr" class="macro">
              <span class="macro-abbr">{{ macroOf.abbr }}</span>
              <span class="num macro-value">{{ macroOf.value }}</span>
            </span>
          </div>

          <div class="srf num kcal" :class="{ partial: line.partial }">{{ line.kcal }}</div>

          <StarRating
            :score="line.score"
            :size="15"
            :disabled="week.saving.value"
            @pick="week.score(line.row, day.day, $event)"
          />
        </div>
        <div v-if="line.open && line.dish?.recipe" class="detail">
          <RecipeDetail :dish="line.dish" />
        </div>
      </div>
    </template>

    <div class="band off">
      <span class="band-label off">Fuera del plan</span>
    </div>

    <div v-if="day.extras.length > 0" class="extras">
      <ExtraFoodRow
        v-for="extra in day.extras"
        :key="extra.id"
        :extra="extra"
        :disabled="week.saving.value"
        @remove="week.removeExtra(extra.id)"
      />
    </div>

    <div class="add-row">
      <p v-if="day.extras.length === 0" class="none">
        Nada fuera del plan este día.
      </p>
      <button class="add" type="button" @click="$emit('add')">
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
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: 5px;
  overflow: hidden;
}

.band {
  flex: none;
  padding: 5px 16px;
  background: var(--sage-100);
}

.band.off {
  background: var(--extra-surface);
}

.band-label {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.band-label.off {
  color: var(--extra-ink);
}

.line {
  flex: none;
  background: var(--surface);
}

.detail {
  padding: 0 16px 12px;
}

.row {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 62px;
  flex: none;
  padding: 0 16px;
  background: var(--surface);
}

.slot {
  width: 112px;
  flex: none;
}

.slot-label {
  font-weight: 500;
  font-size: 12.5px;
  color: var(--ink-strong);
}

.slot-sub {
  font-size: 10.5px;
  color: var(--ink-faint);
}

.text {
  flex: 1 1 0;
  min-width: 0;
  margin: 0;
  padding: 0;
  text-align: left;
  background: none;
  cursor: pointer;
  display: -webkit-box;
  font-size: 12.5px;
  line-height: 1.4;
  color: var(--ink);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.macros {
  display: flex;
  gap: 12px;
  flex: none;
  width: 190px;
}

.macro {
  flex: 1 1 0;
  text-align: right;
}

.macro-abbr {
  display: block;
  font-size: 9.5px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.macro-value {
  display: block;
  margin-top: 1px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ink);
}

.kcal {
  width: 62px;
  flex: none;
  text-align: right;
  font-size: 17px;
  font-weight: 500;
  color: var(--ink-strong);
}

.kcal.partial {
  color: var(--ink-muted);
}

.extras {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 10px 16px;
  background: var(--surface);
}

.add-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
  padding: 10px 16px 12px;
  background: var(--surface);
}

.none {
  margin: 0;
  font-size: 11.5px;
  color: var(--ink-faint);
}

.add {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 12px;
  border: 1px dashed var(--extra-dash);
  border-radius: var(--radius);
  color: var(--extra-ink);
  font-weight: 500;
  font-size: 12px;
}

.add:hover {
  background: var(--extra-surface);
}

/* Tablet: the macro column is the first thing to go, then the slot rail
   narrows. The sentence and the figure are what the row is for. */
@media (max-width: 1100px) {
  .macros {
    display: none;
  }

  .row {
    gap: 14px;
    min-height: 68px;
    padding: 0 14px;
  }

  .slot {
    width: 104px;
  }

  .text {
    -webkit-line-clamp: 3;
    font-size: 12px;
  }

  .kcal {
    width: 56px;
    font-size: 16px;
  }
}
</style>
