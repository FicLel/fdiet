<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import EditorHeader from '@/components/EditorHeader.vue'
import GoalStrip from '@/components/GoalStrip.vue'
import RationsStrip from '@/components/RationsStrip.vue'
import WeekGrid from '@/components/WeekGrid.vue'
import DishEditPanel from '@/components/DishEditPanel.vue'
import FoodLinkPanel from '@/components/FoodLinkPanel.vue'
import NewDietDialog from '@/components/NewDietDialog.vue'
import RecipeLibraryDialog from '@/components/RecipeLibraryDialog.vue'
import AttributionFooter from '@/components/AttributionFooter.vue'
import { useDietDraft } from '@/stores/dietDraft'
import { useFoodLink } from '@/stores/foodLink'
import { usePatients } from '@/stores/patients'
import { useRations } from '@/stores/rations'
import { useReference } from '@/stores/reference'
import { useRecipes } from '@/stores/recipes'
import type { DishTotals } from '@/domain/nutrition'

/**
 * Where a diet is written. One week for one patient, one cell per dish slot per
 * day; a cell is selected in the grid and edited in the panel, and nothing
 * reaches the patient until it is published.
 *
 * The caseload is loaded before the week, because which week to load is the
 * patient selection's answer.
 */

const draft = useDietDraft()
const link = useFoodLink()
const recipes = useRecipes()
const patients = usePatients()
const rations = useRations()
const reference = useReference()

/** Which way the new-diet dialog opens, or null while it is closed. */
const dialog = ref<'blank' | 'import' | null>(null)

onMounted(async () => {
  void reference.ensure().catch(() => undefined)
  if (patients.status.value === 'idle') {
    await patients.load()
  }
  if (draft.status.value === 'idle') {
    void draft.load()
  } else {
    void rations.load(draft.diet.value?.id ?? null)
  }
})

/**
 * Switching patient loads their week and throws away the draft, which `load`
 * does. Two people's unpublished cells must never be in the editor at once.
 */
watch(patients.selectedId, () => {
  void draft.load()
})

/**
 * The ration count is of the stored week, so it is read again whenever the
 * stored week changes: a load, a publish, a match, a new profile.
 */
watch(draft.diet, (plan) => {
  void rations.load(plan?.id ?? null)
})

/** The day the rail is on, so the strip counts the day being written. */
const rationDay = computed(() => draft.selectedDay.value?.day ?? draft.days.value[0]?.day ?? null)

/** BEDCA always; every reference source the count used, once there is a count. */
const sources = computed(() => rations.rations.value?.sources ?? [])

/** Every ingredient of the week, so the goal strip can show what it counted. */
const week = computed<DishTotals>(() =>
  draft.days.value.reduce<DishTotals>(
    (total, day) => ({
      kcal: null,
      proteinG: null,
      carbohydratesG: null,
      fatG: null,
      ingredients: total.ingredients + day.totals.ingredients,
      counted: total.counted + day.totals.counted,
      unmatched: total.unmatched + day.totals.unmatched,
      unmeasured: total.unmeasured + day.totals.unmeasured,
    }),
    {
      kcal: null,
      proteinG: null,
      carbohydratesG: null,
      fatG: null,
      ingredients: 0,
      counted: 0,
      unmatched: 0,
      unmeasured: 0,
    },
  ),
)
</script>

<template>
  <div class="screen">
    <EditorHeader
      :monday="draft.monday.value"
      :dirty-count="draft.dirtyCount.value"
      :publishing="draft.publishing.value"
      @discard="draft.discardAll()"
      @publish="draft.publish()"
      @new-diet="dialog = 'blank'"
    />

    <GoalStrip
      v-model:goal-note="draft.goalNote.value"
      :week-average-kcal="draft.weekAverageKcal.value"
      :week="week"
      :unmatched="draft.unmatched.value.length"
      @review="link.review()"
    />

    <RationsStrip
      v-if="draft.status.value === 'ready'"
      :rations="rations.rations.value"
      :loading="rations.status.value === 'loading'"
      :error="rations.error.value"
      :day="rationDay"
      :dirty-count="draft.dirtyCount.value"
      :profile-label="reference.profileLabel(draft.diet.value?.referenceProfileCode)"
    />

    <p v-if="draft.error.value && draft.status.value === 'ready'" class="banner">
      {{ draft.error.value }}
    </p>

    <div v-if="draft.status.value === 'loading'" class="state">Cargando la dieta…</div>

    <div v-else-if="draft.status.value === 'error'" class="state">
      <p>{{ draft.error.value }}</p>
      <div v-if="draft.missing.value" class="actions">
        <button class="retry" type="button" @click="dialog = 'blank'">Semana en blanco</button>
        <button class="secondary" type="button" @click="dialog = 'import'">Importar Excel</button>
      </div>
      <button v-else-if="patients.any.value" class="retry" type="button" @click="draft.load()">
        Reintentar
      </button>
    </div>

    <div v-else class="body">
      <div class="grid-area scroll">
        <WeekGrid
          :rows="draft.rows.value"
          :days="draft.days.value"
          :target-kcal="draft.targetKcal.value"
          :selection="draft.selection.value"
          @select="draft.select"
        />
      </div>
      <!-- One rail, two jobs. While an ingredient is being matched the choice
           of food is the whole of the work, and the dish's figures are what it
           is about to change; the editor comes back the moment it is done. -->
      <FoodLinkPanel v-if="link.open.value" />
      <DishEditPanel v-else />
    </div>

    <AttributionFooter :sources="sources" />

    <NewDietDialog v-if="dialog" :mode="dialog" @close="dialog = null" />
    <RecipeLibraryDialog
      v-if="recipes.dialog.value"
      :initial-id="recipes.dialog.value.recipeId"
      @close="recipes.closeLibrary()"
    />
  </div>
</template>

<style scoped>
.screen {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--paper);
  color: var(--ink);
  overflow: hidden;
}

.body {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
}

.grid-area {
  flex: 1 1 0;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 18px 20px 20px 24px;
}

.state {
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--ink-muted);
}

.retry {
  height: 34px;
  padding: 0 15px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--surface);
  background: var(--sage-700);
}

.actions {
  display: flex;
  gap: 8px;
}

.secondary {
  height: 34px;
  padding: 0 15px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--ink);
  background: var(--surface);
}

.state p {
  max-width: 520px;
  margin: 0;
  text-align: center;
  line-height: 1.5;
}

.banner {
  flex: none;
  margin: 0;
  padding: 9px 24px;
  background: var(--amber-50);
  color: var(--amber-700);
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}
</style>
