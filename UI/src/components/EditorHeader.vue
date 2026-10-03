<script setup lang="ts">
import { computed } from 'vue'
import AppLogo from './AppLogo.vue'
import PatientSelect from './PatientSelect.vue'
import CopyDietMenu from './CopyDietMenu.vue'
import DietSettingsMenu from './DietSettingsMenu.vue'
import { weekLabel } from '@/domain/week'
import { useRecipes } from '@/stores/recipes'

/**
 * The selector on the left says whose week is being written; the copy menu on
 * the right sends it to somebody else. Neither is a permission — there is no
 * security layer — so both are plain controls over which data is on screen.
 */

const props = defineProps<{
  monday: Date
  dirtyCount: number
  publishing: boolean
}>()

defineEmits<{ discard: []; publish: []; newDiet: [] }>()

const recipes = useRecipes()

const label = computed(() => `Semana del ${weekLabel(props.monday)}`)

const dirtyLabel = computed(() => {
  if (props.publishing) {
    return 'Publicando…'
  }
  if (props.dirtyCount === 0) {
    return 'Todo publicado'
  }
  return props.dirtyCount === 1 ? '1 plato sin publicar' : `${props.dirtyCount} platos sin publicar`
})
</script>

<template>
  <header class="header">
    <AppLogo />
    <span class="divider" />

    <PatientSelect manage-diets />

    <span class="num week">{{ label }}</span>

    <DietSettingsMenu />

    <span class="spacer" />

    <button class="to-patient" type="button" title="Empezar o importar otra semana" @click="$emit('newDiet')">
      Nueva dieta
    </button>

    <button class="to-patient" type="button" title="Las recetas guardadas, para elegirlas en cualquier plato" @click="recipes.openLibrary()">
      Recetario
    </button>

    <CopyDietMenu />

    <!-- The other side of the same week, read-only and with the patient's own
         rows on it. Nothing enforces the split — there is no security layer —
         so it is a link, not a boundary. -->
    <RouterLink class="to-patient" to="/mi-dieta" title="Ver la dieta como la ve el paciente">
      Vista del paciente
    </RouterLink>

    <span class="dirty" :class="{ pending: dirtyCount > 0 }">{{ dirtyLabel }}</span>

    <button
      class="discard"
      type="button"
      :disabled="dirtyCount === 0 || publishing"
      @click="$emit('discard')"
    >
      Descartar
    </button>

    <button
      class="publish"
      type="button"
      :disabled="dirtyCount === 0 || publishing"
      @click="$emit('publish')"
    >
      <svg
        width="14"
        height="14"
        viewBox="0 0 14 14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M7 11V3M3.6 6.4 7 3l3.4 3.4" />
      </svg>
      Publicar cambios
    </button>
  </header>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 60px;
  flex: none;
  padding: 0 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.divider {
  width: 1px;
  height: 22px;
  background: var(--line);
}

.week {
  color: var(--ink-muted);
  white-space: nowrap;
}

.spacer {
  flex: 1 1 0;
}

.to-patient {
  flex: none;
  height: 28px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 11.5px;
  color: var(--ink-muted);
  white-space: nowrap;
}

.to-patient:hover {
  border-color: var(--line-input);
  color: var(--sage-700);
}

.dirty {
  font-size: 12px;
  color: var(--ink-faint);
  white-space: nowrap;
}

.dirty.pending {
  color: var(--amber-700);
}

.discard {
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--ink);
  background: var(--surface);
  white-space: nowrap;
}

.discard:disabled {
  color: var(--ink-disabled);
}

.publish {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 15px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--surface);
  background: var(--sage-700);
  white-space: nowrap;
}

.publish:disabled {
  background: #c2ccc6;
}
</style>
