<script setup lang="ts">
import { computed } from 'vue'
import type { DayOfWeek } from '@/api/types'
import type { MealRow } from '@/domain/slots'
import { useDietDraft } from '@/stores/dietDraft'
import { integer } from '@/domain/format'
import { complete } from '@/domain/nutrition'

const props = defineProps<{
  row: MealRow
  day: DayOfWeek
  selected: boolean
}>()

defineEmits<{ select: [] }>()

const draft = useDietDraft()

const text = computed(() => draft.textFor(props.row, props.day))
const totals = computed(() => draft.totalsFor(props.row, props.day))
const edited = computed(() => draft.isEdited(props.row, props.day))

/**
 * The kcal of a cell whose ingredients are not all counted is a partial figure.
 * It is shown, because a partial total still tells the nutritionist something,
 * but it is marked so it is never read as the whole dish.
 */
const kcal = computed(() => {
  if (totals.value.ingredients === 0) {
    return ''
  }
  if (totals.value.kcal === null) {
    return 'sin datos'
  }
  return complete(totals.value) ? `${integer(totals.value.kcal)} kcal` : `${integer(totals.value.kcal)} kcal · parcial`
})
</script>

<template>
  <button
    class="cell"
    :class="{ selected }"
    type="button"
    role="gridcell"
    :aria-selected="selected"
    @click="$emit('select')"
  >
    <span class="text">{{ text }}</span>
    <span class="foot">
      <span class="num kcal" :class="{ partial: totals.kcal !== null && !complete(totals) }">
        {{ kcal }}
      </span>
      <span v-if="edited" class="edited">
        <span class="dot" />
        editado
      </span>
    </span>
  </button>
</template>

<style scoped>
.cell {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 6px 9px 5px;
  text-align: left;
  background: var(--surface);
}

.cell:hover {
  background: #f4f7f5;
}

.cell.selected {
  background: var(--sage-50);
  box-shadow: inset 0 0 0 2px var(--sage-700);
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

.edited {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  font-size: 10px;
  font-weight: 500;
  color: var(--amber-700);
}

.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--amber-400);
}
</style>
