import { ref, shallowRef } from 'vue'
import { recipesApi, type RecipeRequest } from '@/api/recipes'
import type { Recipe, RecipeUsage } from '@/api/types'
import { useDietDraft } from '@/stores/dietDraft'

/**
 * The shared recipe library, as the builder reads and writes it.
 *
 * **A library recipe is linked, not copied.** Every plate that points at one
 * reads it, in every patient's week, so a save here reaches all of them at once
 * — without the publish that gates everything else the nutritionist changes.
 * That is why `usage` exists and why the dialog shows it before a save: the edit
 * is only safe to make knowing whose weeks it lands in.
 */

const list = shallowRef<Recipe[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
let lastQuery = ''

/** The library dialog, when open, and the recipe it opened on (null for a new one). */
const dialog = ref<{ recipeId: number | null } | null>(null)

function openLibrary(recipeId: number | null = null): void {
  dialog.value = { recipeId }
}

function closeLibrary(): void {
  dialog.value = null
}

function failure(what: string, cause: unknown): string {
  return `${what}${cause instanceof Error ? `: ${cause.message}` : ''}`
}

async function search(name = lastQuery): Promise<void> {
  lastQuery = name
  loading.value = true
  error.value = null
  try {
    const page = await recipesApi.library(name.trim())
    // A slower answer to an older query must not overwrite a newer one.
    if (lastQuery === name) {
      list.value = page.content
    }
  } catch (cause) {
    error.value = failure('No se pudo leer el recetario', cause)
  } finally {
    loading.value = false
  }
}

function usage(id: number): Promise<RecipeUsage> {
  return recipesApi.usage(id)
}

/**
 * The week on screen serves the recipe that changed, possibly in several cells;
 * it is read again so every one of them shows what the library now holds. A cell
 * being edited that links it takes the new version too.
 */
async function afterChange(changed: Recipe | null, removedId: number | null = null): Promise<void> {
  const draft = useDietDraft()
  for (const edit of Object.values(draft.edits)) {
    if (edit.mode !== 'library' || !edit.recipe) {
      continue
    }
    if (changed && edit.recipe.id === changed.id) {
      edit.recipe = changed
    }
    if (removedId !== null && edit.recipe.id === removedId) {
      edit.mode = 'none'
      edit.recipe = null
    }
  }
  await draft.refresh()
  await search()
}

async function save(id: number | null, request: RecipeRequest): Promise<Recipe | string> {
  try {
    const saved = id === null ? await recipesApi.create(request) : await recipesApi.update(id, request)
    await afterChange(saved)
    return saved
  } catch (cause) {
    return failure('No se pudo guardar la receta', cause)
  }
}

async function remove(id: number): Promise<string | null> {
  try {
    await recipesApi.remove(id)
    await afterChange(null, id)
    return null
  } catch (cause) {
    return failure('No se pudo borrar la receta', cause)
  }
}

export function useRecipes() {
  return { list, loading, error, dialog, search, usage, save, remove, openLibrary, closeLibrary }
}
