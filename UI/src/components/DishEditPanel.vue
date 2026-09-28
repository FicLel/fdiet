<script setup lang="ts">
import { computed } from 'vue'
import RationComposer from './RationComposer.vue'
import { isUnweighed, locate, useDietDraft } from '@/stores/dietDraft'
import { useFoodLink } from '@/stores/foodLink'
import { useRations } from '@/stores/rations'
import type { DishIngredient } from '@/api/types'
import { grams, integer, NO_VALUE, quantity } from '@/domain/format'
import { complete } from '@/domain/nutrition'
import { amount, measureSource, perMeasure, stateWord } from '@/domain/rations'
import { cellKey } from '@/domain/slots'
import { quantityText } from '@/domain/dishText'

const draft = useDietDraft()
const link = useFoodLink()

const row = draft.selectedRow
const day = draft.selectedDay

const edit = computed(() =>
  row.value && day.value ? draft.edits[cellKey(row.value, day.value.day)] : undefined,
)

const text = computed(() => (row.value && day.value ? draft.textFor(row.value, day.value.day) : ''))

const dish = computed(() =>
  row.value && day.value
    ? draft.dishFor(day.value.day, row.value.mealType, row.value.dishIndex)
    : undefined,
)

const totals = computed(() =>
  row.value && day.value ? draft.totalsFor(row.value, day.value.day) : undefined,
)

const ingredients = computed(() => dish.value?.ingredients ?? [])

const matchedLabel = computed(() => {
  const all = ingredients.value.length
  if (all === 0) {
    return 'sin ingredientes'
  }
  const matched = ingredients.value.filter(
    (ingredient) => ingredient.foodItemId !== null || ingredient.bedcaFoodId !== null,
  ).length
  return `${matched} de ${all} en el catálogo`
})

/**
 * The figures are derived, never typed. fdiet works a dish's nutrition out from
 * the foods its ingredients are matched to and the weight the diet prescribes;
 * there is nowhere to store a figure the nutritionist overrode, and a number
 * with nothing behind it is exactly what the catalogue matching exists to
 * avoid. The way to move these is to match an ingredient or fix a quantity.
 */
const fields = computed(() => [
  { label: 'kcal', value: totals.value ? integer(totals.value.kcal) : NO_VALUE },
  { label: 'Proteínas g', value: totals.value ? grams(totals.value.proteinG) : NO_VALUE },
  { label: 'HC g', value: totals.value ? grams(totals.value.carbohydratesG) : NO_VALUE },
  { label: 'Grasas g', value: totals.value ? grams(totals.value.fatG) : NO_VALUE },
])

const coverage = computed(() => {
  const of = totals.value
  if (!of || of.ingredients === 0) {
    return null
  }
  if (complete(of)) {
    return null
  }
  const parts: string[] = []
  if (of.unmatched > 0) {
    parts.push(`${of.unmatched} sin vincular`)
  }
  if (of.unmeasured > 0) {
    parts.push(`${of.unmeasured} sin peso`)
  }
  return `Cuentan ${of.counted} de ${of.ingredients} ingredientes — ${parts.join(', ')}.`
})

/**
 * An ingredient can only be matched once it is stored: the PATCH addresses a
 * row, and a cell with unpublished changes has none for what it now says. So
 * the cell is published first, and the panel says so rather than offering a
 * button that could not do anything.
 */
const linkable = computed(() => edit.value === undefined)

const firstUnmatched = computed(() =>
  ingredients.value.find(
    (ingredient) => ingredient.foodItemId === null && ingredient.bedcaFoodId === null,
  ),
)

function openLink(ingredient: DishIngredient): void {
  if (!row.value || !day.value || !linkable.value) {
    return
  }
  const at = locate(row.value, day.value.day, ingredient)
  if (at) {
    link.focus(at)
  }
}

function onInput(event: Event): void {
  if (row.value && day.value) {
    draft.setText(row.value, day.value.day, (event.target as HTMLTextAreaElement).value)
  }
}

function revert(): void {
  if (row.value && day.value) {
    draft.revert(row.value, day.value.day)
  }
}

function append(fragment: string): void {
  if (row.value && day.value) {
    draft.appendFragment(row.value, day.value.day, fragment)
  }
}

/** `1 cdta → 5 g · Criterio de esta dieta`, when a measure weighs the ingredient. */
function measureLine(ingredient: DishIngredient): string | null {
  if (!ingredient.measure) {
    return null
  }
  const weight = perMeasure(ingredient.measure)
  return weight ? `${weight} · ${measureSource(ingredient.measure)}` : null
}

function mismatchTitle(ingredient: DishIngredient): string {
  const written = stateWord(ingredient.state) || 'sin estado'
  const base = `Escrito ${written}, vinculado a «${ingredient.matchedName}». El peso de uno no es el del otro: revisa el vínculo.`
  const hint = ingredient.yieldHint
  if (!hint || hint.equivalentGrams === null) {
    return base
  }
  return `${base} Como referencia, ${hint.sourceShortName}: ≈ ${integer(hint.equivalentGrams)} g ${stateWord(hint.foodState)}.`
}

const rations = useRations()

/**
 * The dish read as exchanges, when that view is on: the backend's count over
 * the stored week, addressed by the slot the way the journal addresses a plate.
 * A cell with unpublished changes has no stored count for what it now says, so
 * none is shown for it.
 */
const dishExchanges = computed(() => {
  const counted = rations.rations.value
  if (!rations.showExchanges.value || !counted || !row.value || !day.value || edit.value) {
    return []
  }
  const today = counted.days.find((candidate) => candidate.day === day.value?.day)
  return (today?.exchanges ?? []).flatMap((system) => {
    const dish = system.dishes.find(
      (candidate) =>
        candidate.mealType === row.value?.mealType && candidate.dishIndex === row.value?.dishIndex,
    )
    return dish ? [{ code: system.code, name: system.name, units: dish.units, complete: dish.complete }] : []
  })
})

/** A range the text gave, "40-60 g", which counts nowhere until a value in it is chosen. */
function amountText(ingredient: DishIngredient): string {
  return ingredient.quantityMax === null
    ? quantity(ingredient.quantity, ingredient.unit)
    : `${quantityText(ingredient)} ${ingredient.unit}`
}
</script>

<template>
  <aside class="panel">
    <div v-if="!row || !day" class="card empty">
      <p>Elige una comida en la rejilla para editarla.</p>
    </div>

    <div v-else class="card">
      <div class="card-head">
        <div class="slot">{{ row.slotLabel }}</div>
        <div class="when">
          <span class="when-day">{{ day.name }}</span>
          <span class="num when-date">{{ day.longDate }}</span>
        </div>
      </div>

      <div class="scroll body">
        <label class="label" for="dish-text">Descripción del plato</label>
        <textarea
          id="dish-text"
          class="text"
          rows="5"
          :value="text"
          @input="onInput"
        ></textarea>
        <p class="hint">
          <template v-if="edit?.parsing">Leyendo el texto…</template>
          <template v-else-if="edit?.failed">
            No se ha podido leer el texto; las cifras siguen siendo las del plato anterior.
          </template>
          <template v-else>
            Un <code>+</code> separa ingredientes y unos dos puntos nombran el plato. La cantidad va
            entre paréntesis: <code>lechuga (80 gr)</code>.
          </template>
        </p>

        <div class="section">
          <span class="label">Ingredientes detectados</span>
          <span class="num label-aside">{{ matchedLabel }}</span>
        </div>

        <ul class="ingredients">
          <li v-for="(ingredient, index) in ingredients" :key="index">
            <button
              class="ingredient"
              type="button"
              :disabled="!linkable"
              :title="
                linkable
                  ? 'Elegir a qué alimento del catálogo corresponde'
                  : 'Publica los cambios de esta celda para poder vincular sus ingredientes'
              "
              @click="openLink(ingredient)"
            >
              <span class="ingredient-line">
                <span class="ingredient-name">{{ ingredient.name }}</span>
                <span class="num ingredient-qty">
                  {{ amountText(ingredient) }}
                </span>
                <span
                  class="chip"
                  :class="{
                    linked: ingredient.foodItemId !== null || ingredient.bedcaFoodId !== null,
                  }"
                >
                  {{ ingredient.matchedName ?? 'Sin vincular' }}
                </span>
              </span>
              <span
                v-if="
                  measureLine(ingredient) ||
                  isUnweighed(ingredient) ||
                  ingredient.stateMismatch ||
                  ingredient.quantityMax !== null
                "
                class="ingredient-sub"
              >
                <span
                  v-if="ingredient.quantityMax !== null"
                  class="chip warn"
                  title="Escrito como intervalo. No cuenta en las cifras hasta que elijas un valor: pulsa para fijarlo."
                >
                  Intervalo sin fijar
                </span>
                <span v-else-if="measureLine(ingredient)" class="num measure">{{ measureLine(ingredient) }}</span>
                <span
                  v-else-if="isUnweighed(ingredient)"
                  class="chip warn"
                  title="Nada pesa esta medida para este alimento. Pulsa para elegir una o fijar la de esta dieta."
                >
                  Sin peso
                </span>
                <span v-if="ingredient.stateMismatch" class="chip warn" :title="mismatchTitle(ingredient)">
                  Crudo/cocinado no coincide
                </span>
              </span>
            </button>
          </li>
          <li v-if="ingredients.length === 0" class="ingredient empty-row">
            Esta celda no describe ningún ingrediente.
          </li>
        </ul>

        <button
          class="search"
          type="button"
          :disabled="!linkable || !firstUnmatched"
          @click="firstUnmatched && openLink(firstUnmatched)"
        >
          <svg width="13" height="13" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true">
            <circle cx="6.2" cy="6.2" r="4" />
            <path d="M9.2 9.2l2.6 2.6" />
          </svg>
          Buscar alimento en el catálogo
        </button>
        <RationComposer
          :diet-id="draft.diet.value?.id ?? null"
          :profile-code="draft.diet.value?.referenceProfileCode ?? null"
          @append="append"
        />
        <p class="hint">
          <template v-if="!linkable">
            Esta celda tiene cambios sin publicar. Publícalos y sus ingredientes podrán vincularse.
          </template>
          <template v-else-if="firstUnmatched">
            Vincular no cambia lo que lee el paciente, sólo de qué alimento salen las cifras. Se
            guarda al momento.
          </template>
          <template v-else>
            Todos los ingredientes de este plato están vinculados. Pulsa uno para corregirlo.
          </template>
        </p>

        <div class="label section-title">Valores del plato</div>
        <div class="fields">
          <div v-for="field in fields" :key="field.label" class="field">
            <span class="field-label">{{ field.label }}</span>
            <output class="num field-value">{{ field.value }}</output>
          </div>
        </div>
        <p class="hint">
          Se calculan a partir de los alimentos vinculados y del peso escrito; no se teclean.
          <template v-if="coverage"> {{ coverage }}</template>
        </p>

        <template v-if="dishExchanges.length > 0">
          <div class="label section-title">Intercambios del plato</div>
          <div class="fields">
            <div v-for="system in dishExchanges" :key="system.code" class="field">
              <span class="field-label">{{ system.name }}</span>
              <output class="num field-value">{{ amount(system.units, 1) }}</output>
            </div>
          </div>
          <p class="hint">
            10 g del nutriente por intercambio, sobre lo publicado.
            <template v-if="dishExchanges.some((system) => !system.complete)">
              Parte del plato no cuenta, así que es un mínimo.
            </template>
          </p>
        </template>

        <div class="day-total">
          <span>Total del día tras el cambio</span>
          <span class="srf num day-total-value">{{ integer(day.totals.kcal) }}</span>
          <span class="num day-total-target">/ {{ integer(draft.targetKcal.value) }} kcal</span>
        </div>

        <p class="footnote">
          Los cambios sólo llegan al paciente al publicar. El paciente ve la dieta en modo lectura.
        </p>
      </div>

      <div class="card-foot">
        <button class="revert" type="button" :disabled="!edit" @click="revert">Revertir plato</button>
        <button
          class="publish"
          type="button"
          :disabled="draft.dirtyCount.value === 0 || draft.publishing.value"
          @click="draft.publish()"
        >
          Publicar
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.panel {
  width: 380px;
  flex: none;
  padding: 18px 24px 20px 4px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.card {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card.empty {
  align-items: center;
  justify-content: center;
  color: var(--ink-faint);
}

.card-head {
  flex: none;
  padding: 14px 16px 13px;
  border-bottom: 1px solid var(--line-soft);
}

.slot {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.when {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 4px;
}

.when-day {
  font-weight: 600;
  font-size: 14px;
  color: var(--ink-strong);
}

.when-date {
  font-size: 11.5px;
  color: var(--ink-faint);
}

.body {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px 16px;
}

.label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.text {
  display: block;
  width: 100%;
  margin-top: 7px;
  padding: 10px 11px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
  font-size: 12.5px;
  line-height: 1.45;
  color: var(--ink-strong);
  background: var(--surface);
  outline: none;
}

.hint {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.hint code {
  font-family: inherit;
  color: var(--ink-muted);
}

.section {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
}

.label-aside {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-faint);
}

.ingredients {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.ingredient {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  text-align: left;
  background: var(--surface);
}

.ingredient:hover:not(:disabled) {
  border-color: var(--sage-200);
  background: var(--sage-50);
}

.ingredient:disabled .ingredient-name {
  color: var(--ink-muted);
}

.ingredient.empty-row {
  color: var(--ink-faint);
  font-size: 11.5px;
}

.ingredient-line,
.ingredient-sub {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
}

.ingredient-sub {
  gap: 6px;
}

.measure {
  font-size: 10.5px;
  color: var(--ink-muted);
}

.chip.warn {
  max-width: none;
  height: 18px;
  line-height: 18px;
}

.ingredient-name {
  flex: 1 1 0;
  min-width: 0;
  font-size: 12px;
  color: var(--ink-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ingredient-qty {
  flex: none;
  font-size: 11.5px;
  color: var(--ink);
}

/* The chip names the food it was matched to, so it says what the figures are
   of; a name too long for the rail is cut rather than allowed to wrap. */
.chip {
  flex: none;
  display: block;
  max-width: 122px;
  height: 20px;
  padding: 0 7px;
  border-radius: 3px;
  font-size: 10px;
  line-height: 20px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background: var(--amber-50);
  color: var(--amber-700);
}

.chip.linked {
  background: var(--sage-100);
  color: var(--sage-700);
}

.search {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  margin-top: 8px;
  height: 34px;
  border: 1px dashed var(--line-input);
  border-radius: var(--radius);
  font-size: 12px;
  font-weight: 500;
  color: var(--sage-700);
}

.search:disabled {
  color: var(--ink-disabled);
}

.section-title {
  margin-top: 20px;
}

.fields {
  display: flex;
  gap: 8px;
  margin-top: 9px;
}

.field {
  flex: 1 1 0;
  min-width: 0;
}

.field-label {
  display: block;
  font-size: 10.5px;
  color: var(--ink-faint);
}

.field-value {
  display: flex;
  align-items: center;
  width: 100%;
  margin-top: 4px;
  height: 36px;
  padding: 0 9px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-strong);
  background: var(--surface-muted);
}

.day-total {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 14px;
  padding: 11px 12px;
  background: var(--sage-50);
  border-radius: var(--radius);
  font-size: 11.5px;
  color: var(--ink);
}

.day-total-value {
  margin-left: auto;
  font-size: 17px;
  font-weight: 500;
  color: var(--ink-strong);
}

.day-total-target {
  font-size: 11px;
  color: var(--ink-muted);
}

.footnote {
  margin: 16px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--ink-faint);
}

.card-foot {
  display: flex;
  gap: 8px;
  flex: none;
  padding: 12px 16px;
  border-top: 1px solid var(--line-soft);
}

.revert {
  flex: 1 1 0;
  height: 36px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--ink);
}

.revert:disabled {
  color: var(--ink-disabled);
}

.publish {
  flex: 1 1 0;
  height: 36px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--surface);
  background: var(--sage-700);
}

.publish:disabled {
  background: #c2ccc6;
}
</style>
