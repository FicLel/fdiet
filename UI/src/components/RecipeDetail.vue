<script setup lang="ts">
import { computed } from 'vue'
import type { Dish } from '@/api/types'
import { servedQuantity } from '@/domain/dishText'

/**
 * What is on a plate and how it is made, as the patient reads it.
 *
 * The quantities are the plate's, not the recipe's: a shared recipe is written
 * for one serving, and a patient served 1.5 of it should read 3 eggs, not 2 with
 * a sum to do. An ingredient the text gave no quantity for (`sal`) is listed by
 * name alone rather than as "1 unidad", which the diet never said.
 */
const props = defineProps<{ dish: Dish | undefined }>()

const recipe = computed(() => props.dish?.recipe ?? null)

const lines = computed(() =>
  (recipe.value?.ingredients ?? []).map((ingredient) => ({
    name: ingredient.name,
    amount:
      ingredient.quantity === 1 && ingredient.quantityMax === null && ingredient.unit === 'unidad'
        ? ''
        : servedQuantity(ingredient, props.dish?.servings ?? 1),
  })),
)

const steps = computed(() => recipe.value?.steps?.trim() ?? '')
</script>

<template>
  <div v-if="recipe && (lines.length > 0 || steps)" class="recipe">
    <template v-if="lines.length > 0">
      <div class="title">Ingredientes</div>
      <ul class="ingredients">
        <li v-for="(line, index) in lines" :key="index">
          <span class="name">{{ line.name }}</span>
          <span v-if="line.amount" class="num amount">{{ line.amount }}</span>
        </li>
      </ul>
    </template>
    <template v-if="steps">
      <div class="title">Preparación</div>
      <p class="steps">{{ steps }}</p>
    </template>
  </div>
</template>

<style scoped>
.recipe {
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid var(--line-soft);
  text-align: left;
}

.title {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-muted);
  margin-top: 6px;
}

.title:first-child {
  margin-top: 0;
}

.ingredients {
  margin: 5px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ingredients li {
  display: flex;
  gap: 8px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--ink-strong);
}

.amount {
  margin-left: auto;
  flex: none;
  color: var(--ink-muted);
}

.steps {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--ink-strong);
  white-space: pre-line;
}
</style>
