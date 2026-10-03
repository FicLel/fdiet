<script setup lang="ts">
import type { FoodMeasure, Ration } from '@/api/types'
import { sameChoice, GRAMS_CHOICE, type ComposerChoice } from '@/domain/composerChoice'
import { criterionFor, isOwnCriterion, weighs } from '@/domain/measureCriteria'
import { amount, measureSource, measureText, rationWeight } from '@/domain/rations'

/**
 * The ways one food can be counted into a cell: its rations, its household
 * measures (the nutritionist's own criteria first, as the backend lists them),
 * a new unit of her own, or plain grams.
 *
 * A published range is a choice like any other. Picking it is the composer's
 * business: it uses the criterion that already answers it, or asks for one.
 */
const props = defineProps<{
  rations: Ration[]
  measures: FoodMeasure[]
  choice: ComposerChoice
}>()

const emit = defineEmits<{ select: [choice: ComposerChoice]; newUnit: [] }>()

function rangeMeta(measure: FoodMeasure): string {
  const criterion = criterionFor(props.measures, measure)
  return criterion
    ? `rango · se usa tu criterio, ${amount(criterion.gramsPerMeasure, 1)} g`
    : 'rango · te pedirá el peso por unidad'
}
</script>

<template>
  <div class="choices">
    <button
      v-for="ration in rations"
      :key="`r-${ration.id}`"
      class="choice"
      :class="{ on: sameChoice(choice, { kind: 'ration', ration }) }"
      type="button"
      :title="[ration.note, ration.pageRef].filter(Boolean).join(' · ')"
      @click="emit('select', { kind: 'ration', ration })"
    >
      <span class="choice-main">1 ración · {{ rationWeight(ration) }}</span>
      <span class="choice-meta">{{ ration.groupLabel }} · {{ ration.sourceShortName }}</span>
    </button>

    <button
      v-for="measure in measures"
      :key="`m-${measure.id}`"
      class="choice"
      :class="{ on: sameChoice(choice, { kind: 'measure', measure }), own: isOwnCriterion(measure) }"
      type="button"
      :title="[measure.note, measure.pageRef].filter(Boolean).join(' · ')"
      @click="emit('select', { kind: 'measure', measure })"
    >
      <span class="choice-main">{{ measureText(measure) }}</span>
      <span class="choice-meta">
        {{ measureSource(measure) }}
        <template v-if="!weighs(measure)"> · {{ rangeMeta(measure) }}</template>
      </span>
    </button>

    <button class="choice new" type="button" @click="emit('newUnit')">
      <span class="choice-main">+ Nueva unidad</span>
      <span class="choice-meta">Tu peso para una rebanada, una loncha, una unidad…</span>
    </button>

    <button
      class="choice"
      :class="{ on: choice.kind === 'grams' }"
      type="button"
      @click="emit('select', GRAMS_CHOICE)"
    >
      <span class="choice-main">Gramos</span>
      <span class="choice-meta">Escribir el peso</span>
    </button>
  </div>
</template>

<style scoped>
.choices {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.choice {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 6px 8px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  background: var(--surface);
  text-align: left;
}

.choice.on {
  border-color: var(--sage-600);
  background: var(--sage-50);
}

.choice.own .choice-meta {
  color: var(--sage-700);
}

.choice.new {
  border-style: dashed;
  border-color: var(--line-input);
}

.choice.new .choice-main {
  color: var(--sage-700);
}

.choice-main {
  font-size: 11.5px;
  color: var(--ink-strong);
}

.choice-meta {
  font-size: 10.5px;
  color: var(--ink-faint);
}
</style>
