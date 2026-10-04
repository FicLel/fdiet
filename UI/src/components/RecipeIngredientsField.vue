<script setup lang="ts">
import { computed } from 'vue'
import type { Recipe } from '@/api/types'
import { amountText, joinFragment } from '@/domain/dishText'
import { complete, recipeTotals } from '@/domain/nutrition'
import { integer } from '@/domain/format'
import RationComposer from './RationComposer.vue'

/**
 * A library recipe's ingredients: the text as typed, what the backend read out
 * of it, and "Añadir por raciones" to append a food by its rations or units.
 *
 * A library recipe belongs to no diet, so the composer runs without one: the
 * nutritionist's global criteria weigh here as in any diet, and nothing else.
 */
const props = defineProps<{
  text: string
  read: Recipe | null
  reading: boolean
}>()

const emit = defineEmits<{ input: [value: string] }>()

const totals = computed(() => recipeTotals(props.read?.ingredients ?? []))
const ingredients = computed(() => props.read?.ingredients ?? [])

/** Enter in a composer field would submit the dialog's form; a button keeps its Enter. */
function keepFormUnsent(event: KeyboardEvent): void {
  if (event.target instanceof HTMLInputElement) {
    event.preventDefault()
  }
}
</script>

<template>
  <div class="ingredients">
    <label class="field">
      <span class="label">Ingredientes <span class="optional">(una ración)</span></span>
      <textarea
        class="input area"
        rows="3"
        :value="text"
        placeholder="2 huevos + aceite de oliva (5 ml) + sal"
        @input="emit('input', ($event.target as HTMLTextAreaElement).value)"
      ></textarea>
      <span class="hint">
        Un <code>+</code> separa ingredientes y la cantidad va entre paréntesis. Cada plato
        que use la receta puede servir más o menos raciones.
      </span>
    </label>

    <ul v-if="ingredients.length > 0" class="read">
      <li v-for="(ingredient, index) in ingredients" :key="index">
        <span>{{ ingredient.name }}</span>
        <span class="num qty">{{ amountText(ingredient) }}</span>
        <span class="chip" :class="{ linked: ingredient.compositionFoodId !== null || ingredient.foodItemId !== null }">
          {{ ingredient.matchedName ?? 'Sin vincular' }}
        </span>
      </li>
    </ul>
    <p v-if="ingredients.length > 0" class="hint">
      <template v-if="reading">Leyendo…</template>
      <template v-else>
        Una ración: {{ totals.kcal === null ? 'sin datos' : `${integer(totals.kcal)} kcal` }}{{
          complete(totals) ? '' : ` sobre ${totals.counted} de ${totals.ingredients} ingredientes`
        }}. Lo que quede sin vincular se corrige desde «Repasar sin vincular» de una dieta
        que la use.
      </template>
    </p>

    <!-- Inside the dialog's form: Enter in a composer field must not save the recipe. -->
    <div @keydown.enter="keepFormUnsent">
      <RationComposer
        :diet-id="null"
        :profile-code="null"
        @append="(fragment) => emit('input', joinFragment(text, fragment))"
      />
    </div>
  </div>
</template>

<style scoped>
.ingredients {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.optional {
  font-weight: 400;
  letter-spacing: 0;
  text-transform: none;
  color: var(--ink-faint);
}

.input {
  width: 100%;
  min-height: 34px;
  padding: 0 9px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink-strong);
}

.area {
  padding: 8px 9px;
  line-height: 1.45;
  resize: vertical;
}

.hint {
  display: block;
  margin: 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.hint code {
  font-family: inherit;
  color: var(--ink-muted);
}

.read {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.read li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--ink-strong);
}

.qty {
  color: var(--ink-muted);
}

.chip {
  margin-left: auto;
  max-width: 50%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 1px 7px;
  border-radius: 10px;
  font-size: 10.5px;
  color: var(--amber-700);
  background: var(--amber-50);
}

.chip.linked {
  color: var(--sage-700);
  background: var(--sage-50);
}
</style>
