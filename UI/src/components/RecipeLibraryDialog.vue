<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { dietsApi } from '@/api/diets'
import { recipesApi } from '@/api/recipes'
import type { Recipe, RecipeUsage } from '@/api/types'
import { useRecipes } from '@/stores/recipes'
import { renderRecipe, quantityText } from '@/domain/dishText'
import { recipeTotals, complete } from '@/domain/nutrition'
import { integer } from '@/domain/format'

/**
 * The shared recipe library: browse, write, correct and remove.
 *
 * A save here is live. A library recipe is linked from every plate that serves
 * it, so an edit reaches every patient's week that uses it at once, with no
 * publish in between — the usage line says whose, before the button is pressed.
 */
const props = defineProps<{ initialId?: number | null }>()
const emit = defineEmits<{ close: [] }>()

const recipes = useRecipes()

const query = ref('')
const selectedId = ref<number | null>(null)
const name = ref('')
const text = ref('')
const steps = ref('')
const read = ref<Recipe | null>(null)
const reading = ref(false)
const usage = ref<RecipeUsage | null>(null)
const busy = ref(false)
const error = ref<string | null>(null)
const saved = ref<string | null>(null)

const PARSE_DEBOUNCE_MS = 450
let parseTimer: ReturnType<typeof setTimeout> | undefined
let searchTimer: ReturnType<typeof setTimeout> | undefined

const isNew = computed(() => selectedId.value === null)
const canSave = computed(() => name.value.trim() !== '' && !busy.value && !reading.value)
const totals = computed(() => recipeTotals(read.value?.ingredients ?? []))
const servedIn = computed(() => {
  const found = usage.value
  if (!found || found.dishes === 0) {
    return null
  }
  const whose = found.diets.map((diet) => `${diet.patientName} (${diet.name})`).join(', ')
  const plates = found.dishes === 1 ? '1 plato' : `${found.dishes} platos`
  return `Se sirve en ${plates}: ${whose}. Guardar la cambia en todos, sin publicar.`
})

function edit(recipe: Recipe | null): void {
  clearTimeout(parseTimer)
  selectedId.value = recipe?.id ?? null
  name.value = recipe?.name ?? ''
  text.value = renderRecipe(recipe)
  steps.value = recipe?.steps ?? ''
  read.value = recipe
  reading.value = false
  usage.value = null
  error.value = null
  saved.value = null
  if (recipe?.id != null) {
    const id = recipe.id
    void recipes.usage(id).then((found) => {
      if (selectedId.value === id) {
        usage.value = found
      }
    })
  }
}

async function reparse(): Promise<void> {
  const typed = text.value
  if (typed.trim() === '') {
    read.value = null
    reading.value = false
    return
  }
  try {
    const answer = await dietsApi.parse({ text: typed, slotName: name.value.trim() || undefined })
    if (text.value === typed) {
      read.value = answer
    }
  } catch {
    // Keeps the last reading; the save sends the text and the backend reads it.
  } finally {
    if (text.value === typed) {
      reading.value = false
    }
  }
}

function onText(value: string): void {
  text.value = value
  saved.value = null
  reading.value = true
  clearTimeout(parseTimer)
  parseTimer = setTimeout(() => void reparse(), PARSE_DEBOUNCE_MS)
}

function onQuery(value: string): void {
  query.value = value
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void recipes.search(value), 250)
}

async function save(): Promise<void> {
  if (!canSave.value) {
    return
  }
  busy.value = true
  error.value = null
  const answer = await recipes.save(selectedId.value, {
    name: name.value.trim(),
    steps: steps.value.trim() || null,
    rawText: text.value.trim() || null,
    // What was read, with any match it carries; empty lets the backend read the text.
    ingredients:
      text.value.trim() === ''
        ? []
        : (read.value?.ingredients ?? []).map((ingredient) => ({
            name: ingredient.name,
            quantity: ingredient.quantity,
            quantityMax: ingredient.quantityMax,
            unit: ingredient.unit,
            foodItemId: ingredient.foodItemId,
            bedcaFoodId: ingredient.bedcaFoodId,
            state: ingredient.state,
            size: ingredient.size,
            foodMeasureId: ingredient.foodMeasureId,
          })),
  })
  busy.value = false
  if (typeof answer === 'string') {
    error.value = answer
    return
  }
  edit(answer)
  saved.value = 'Guardada.'
}

async function remove(): Promise<void> {
  const id = selectedId.value
  if (id === null || busy.value) {
    return
  }
  busy.value = true
  const failed = await recipes.remove(id)
  busy.value = false
  if (failed) {
    error.value = failed
    return
  }
  edit(null)
}

function onEscape(event: KeyboardEvent): void {
  if (event.key === 'Escape' && !busy.value) {
    emit('close')
  }
}

onMounted(async () => {
  window.addEventListener('keydown', onEscape)
  await recipes.search('')
  edit(props.initialId == null ? null : await recipeById(props.initialId))
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onEscape)
  clearTimeout(parseTimer)
  clearTimeout(searchTimer)
})

watch(
  () => props.initialId,
  async (id) => {
    if (id != null) {
      edit(await recipeById(id))
    }
  },
)

/** From the page already listed, or asked for: the library may hold more than one page. */
async function recipeById(id: number): Promise<Recipe | null> {
  const listed = recipes.list.value.find((recipe) => recipe.id === id)
  if (listed) {
    return listed
  }
  try {
    return await recipesApi.byId(id)
  } catch {
    return null
  }
}
</script>

<template>
  <div class="overlay" @mousedown.self="!busy && emit('close')">
    <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="recipes-title">
      <div class="head">
        <h2 id="recipes-title" class="title">Recetario</h2>
        <button class="icon" type="button" aria-label="Cerrar" :disabled="busy" @click="emit('close')">
          <svg width="15" height="15" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" aria-hidden="true">
            <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" />
          </svg>
        </button>
      </div>

      <div class="columns">
        <div class="list-col">
          <input
            class="input"
            type="search"
            placeholder="Buscar receta"
            :value="query"
            @input="onQuery(($event.target as HTMLInputElement).value)"
          />
          <button class="new" type="button" :class="{ on: isNew }" @click="edit(null)">
            + Receta nueva
          </button>
          <ul class="list">
            <li v-for="recipe in recipes.list.value" :key="recipe.id ?? recipe.name">
              <button
                type="button"
                class="item"
                :class="{ on: recipe.id === selectedId }"
                @click="edit(recipe)"
              >
                <span class="item-name">{{ recipe.name }}</span>
                <span class="num item-kcal">
                  {{ recipe.nutrition?.totals?.energyKcal != null ? `${integer(recipe.nutrition.totals.energyKcal)} kcal` : '' }}
                </span>
              </button>
            </li>
            <li v-if="!recipes.loading.value && recipes.list.value.length === 0" class="none">
              {{ query ? 'Ninguna receta con ese nombre.' : 'Todavía no hay recetas guardadas.' }}
            </li>
          </ul>
          <p v-if="recipes.error.value" class="error">{{ recipes.error.value }}</p>
        </div>

        <form class="body" @submit.prevent="save()">
          <label class="field">
            <span class="label">Nombre</span>
            <input v-model="name" class="input" type="text" maxlength="255" @input="saved = null" />
          </label>

          <label class="field">
            <span class="label">Ingredientes <span class="optional">(una ración)</span></span>
            <textarea
              class="input area"
              rows="3"
              :value="text"
              placeholder="2 huevos + aceite de oliva (5 ml) + sal"
              @input="onText(($event.target as HTMLTextAreaElement).value)"
            ></textarea>
            <span class="hint">
              Un <code>+</code> separa ingredientes y la cantidad va entre paréntesis. Cada plato
              que use la receta puede servir más o menos raciones.
            </span>
          </label>

          <ul v-if="read && read.ingredients.length > 0" class="read">
            <li v-for="(ingredient, index) in read.ingredients" :key="index">
              <span>{{ ingredient.name }}</span>
              <span class="num qty">{{ quantityText(ingredient) }} {{ ingredient.unit }}</span>
              <span class="chip" :class="{ linked: ingredient.bedcaFoodId !== null || ingredient.foodItemId !== null }">
                {{ ingredient.matchedName ?? 'Sin vincular' }}
              </span>
            </li>
          </ul>
          <p v-if="read && read.ingredients.length > 0" class="hint">
            <template v-if="reading">Leyendo…</template>
            <template v-else>
              Una ración: {{ totals.kcal === null ? 'sin datos' : `${integer(totals.kcal)} kcal` }}{{
                complete(totals) ? '' : ` sobre ${totals.counted} de ${totals.ingredients} ingredientes`
              }}. Lo que quede sin vincular se corrige desde «Repasar sin vincular» de una dieta
              que la use.
            </template>
          </p>

          <label class="field">
            <span class="label">Preparación</span>
            <textarea v-model="steps" class="input area" rows="5" maxlength="4000" @input="saved = null"></textarea>
          </label>

          <p v-if="servedIn" class="warn">{{ servedIn }}</p>
          <p v-if="error" class="error">{{ error }}</p>
          <p v-if="saved" class="done">{{ saved }}</p>

          <div class="foot">
            <button
              v-if="!isNew"
              class="secondary"
              type="button"
              :disabled="busy || (usage?.dishes ?? 0) > 0"
              :title="(usage?.dishes ?? 0) > 0 ? 'Quítala antes de los platos que la sirven' : undefined"
              @click="remove()"
            >
              Borrar
            </button>
            <button class="primary" type="submit" :disabled="!canSave">
              {{ isNew ? 'Añadir al recetario' : 'Guardar cambios' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(22, 28, 24, 0.28);
}

.dialog {
  width: min(820px, 100%);
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.head {
  display: flex;
  align-items: center;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--line-soft);
}

.title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-strong);
}

.icon {
  margin-left: auto;
  display: flex;
  padding: 2px;
  color: var(--ink-faint);
}

.columns {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
}

.list-col {
  width: 260px;
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 12px 14px 16px;
  border-right: 1px solid var(--line-soft);
  min-height: 0;
}

.new {
  height: 30px;
  border: 1px dashed var(--line-input);
  border-radius: var(--radius);
  font-size: 12px;
  color: var(--sage-700);
}

.new.on {
  border-style: solid;
  border-color: var(--sage-200);
  background: var(--sage-50);
}

.list {
  flex: 1 1 auto;
  min-height: 120px;
  overflow-y: auto;
  margin: 0;
  padding: 0;
  list-style: none;
}

.item {
  width: 100%;
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 7px 8px;
  border-radius: var(--radius);
  text-align: left;
  font-size: 12.5px;
  color: var(--ink-strong);
}

.item:hover {
  background: #f4f7f5;
}

.item.on {
  background: var(--sage-50);
  color: var(--sage-700);
  font-weight: 500;
}

.item-name {
  flex: 1 1 auto;
  min-width: 0;
}

.item-kcal {
  flex: none;
  font-size: 11px;
  color: var(--ink-faint);
}

.none {
  padding: 8px;
  font-size: 12px;
  color: var(--ink-faint);
}

.body {
  flex: 1 1 auto;
  min-width: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px 16px;
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

.warn,
.error {
  margin: 0;
  padding: 8px 10px;
  border-radius: var(--radius);
  background: var(--amber-50);
  font-size: 11.5px;
  line-height: 1.45;
  color: var(--amber-700);
}

.done {
  margin: 0;
  padding: 7px 10px;
  border-radius: var(--radius);
  background: var(--sage-50);
  font-size: 12px;
  color: var(--sage-700);
}

.foot {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.primary,
.secondary {
  flex: 1 1 0;
  height: 36px;
  border-radius: var(--radius);
  font-weight: 500;
}

.primary {
  color: var(--surface);
  background: var(--sage-700);
}

.primary:disabled {
  background: #c2ccc6;
}

.secondary {
  border: 1px solid var(--line);
  color: var(--ink);
}

.secondary:disabled {
  color: var(--ink-faint);
}

@media (max-width: 700px) {
  .columns {
    flex-direction: column;
    overflow-y: auto;
  }

  .list-col {
    width: auto;
    border-right: none;
    border-bottom: 1px solid var(--line-soft);
  }

  .list {
    max-height: 180px;
  }
}
</style>
