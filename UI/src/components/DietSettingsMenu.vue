<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import type { ReferenceProfile } from '@/api/types'
import { useDietDraft } from '@/stores/dietDraft'
import { usePatients } from '@/stores/patients'
import { useReference } from '@/stores/reference'

/**
 * What the week on screen is read against: its reference population and
 * whether it is a clinical diet.
 *
 * **Changing either never rewrites a gram.** The profile decides what the
 * ration counts are compared with; the clinical flag decides whether
 * carbohydrate rations are counted. The week itself stays as written.
 */

const draft = useDietDraft()
const patients = usePatients()
const reference = useReference()

const open = ref(false)
const saving = ref(false)
const root = ref<HTMLElement | null>(null)
const options = shallowRef<ReferenceProfile[]>([])

const plan = computed(() => draft.diet.value)

const chip = computed(() => {
  const code = plan.value?.referenceProfileCode
  const found = reference.profile(code)
  return found ? found.sourceShortName : code ? code : 'Sin referencia'
})

const title = computed(() => reference.profileLabel(plan.value?.referenceProfileCode))

async function toggle(): Promise<void> {
  if (open.value) {
    open.value = false
    return
  }
  open.value = true
  try {
    options.value = await reference.profilesFor(patients.ageInMonths(patients.selected.value))
  } catch {
    options.value = reference.selectable.value
  }
}

async function choose(code: string | null): Promise<void> {
  if (!plan.value || saving.value || code === plan.value.referenceProfileCode) {
    return
  }
  saving.value = true
  // An empty string clears the profile; null would mean "leave it alone".
  await draft.updateSettings({ referenceProfileCode: code ?? '' })
  saving.value = false
}

async function setClinical(clinical: boolean): Promise<void> {
  if (!plan.value || saving.value) {
    return
  }
  saving.value = true
  await draft.updateSettings({ clinical })
  saving.value = false
}

function onOutside(event: MouseEvent): void {
  if (open.value && root.value && !root.value.contains(event.target as Node)) {
    open.value = false
  }
}

function onEscape(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    open.value = false
  }
}

onMounted(() => {
  void reference.ensure().catch(() => undefined)
  document.addEventListener('mousedown', onOutside)
  document.addEventListener('keydown', onEscape)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutside)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div ref="root" class="settings">
    <button
      class="trigger"
      type="button"
      :disabled="!plan"
      :aria-expanded="open"
      aria-haspopup="menu"
      :title="title"
      @click="toggle()"
    >
      <span class="chip-label">Referencia</span>
      <span class="chip-value">{{ chip }}</span>
      <span v-if="plan?.clinical" class="clinical">Clínica</span>
    </button>

    <div v-if="open && plan" class="menu" role="menu">
      <p class="lead">
        Población con la que se comparan las raciones. Cambiarla no toca ningún gramo de la
        dieta.
      </p>

      <button
        v-for="profile in options"
        :key="profile.code"
        class="option"
        :class="{ on: profile.code === plan.referenceProfileCode }"
        type="button"
        role="menuitemradio"
        :aria-checked="profile.code === plan.referenceProfileCode"
        :disabled="saving"
        @click="choose(profile.code)"
      >
        <span class="option-name">
          {{ profile.label }}
          <span v-if="profile.suggested" class="suggested">propuesta por edad</span>
        </span>
        <span class="option-meta">{{ profile.sourceShortName }}</span>
      </button>

      <button
        class="option"
        :class="{ on: plan.referenceProfileCode === null }"
        type="button"
        role="menuitemradio"
        :aria-checked="plan.referenceProfileCode === null"
        :disabled="saving"
        @click="choose(null)"
      >
        <span class="option-name">Sin población de referencia</span>
        <span class="option-meta">No se cuentan raciones</span>
      </button>

      <label class="clinical-toggle">
        <input
          type="checkbox"
          :checked="plan.clinical"
          :disabled="saving"
          @change="setClinical(($event.target as HTMLInputElement).checked)"
        />
        <span>
          <span class="option-name">Dieta clínica</span>
          <span class="option-meta">
            Cuenta además raciones de hidratos de carbono (10 g), como en la educación diabetológica.
            Sólo con indicación clínica.
          </span>
        </span>
      </label>

      <p v-if="patients.selected.value && !patients.selected.value.birthDate" class="lead">
        {{ patients.selected.value.name }} no tiene fecha de nacimiento: ninguna población se
        propone por edad.
      </p>
    </div>
  </div>
</template>

<style scoped>
.settings {
  position: relative;
  flex: none;
}

.trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 11.5px;
  white-space: nowrap;
}

.trigger:hover:not(:disabled) {
  border-color: var(--line-input);
}

.trigger:disabled {
  opacity: 0.5;
}

.chip-label {
  color: var(--ink-faint);
}

.chip-value {
  font-weight: 500;
  color: var(--sage-700);
}

.clinical {
  padding: 0 5px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  color: var(--amber-700);
  background: var(--amber-50);
}

.menu {
  position: absolute;
  top: 34px;
  left: 0;
  z-index: 90;
  width: 320px;
  max-height: 70vh;
  overflow-y: auto;
  padding: 5px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 5px;
  box-shadow: var(--shadow-menu);
}

.lead {
  margin: 4px 8px 8px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--ink-faint);
}

.option,
.clinical-toggle {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 1px;
  padding: 8px 10px;
  border-radius: var(--radius);
  text-align: left;
}

.option:hover:not(:disabled),
.option.on {
  background: var(--sage-50);
}

.option.on .option-name {
  color: var(--sage-700);
}

.option-name {
  display: block;
  font-weight: 500;
  color: var(--ink-strong);
}

.option-meta {
  display: block;
  font-size: 11px;
  line-height: 1.4;
  color: var(--ink-muted);
}

.suggested {
  margin-left: 4px;
  font-size: 10px;
  font-weight: 500;
  color: var(--amber-700);
}

.clinical-toggle {
  flex-direction: row;
  gap: 9px;
  margin-top: 5px;
  padding-top: 10px;
  border-top: 1px solid var(--line-soft);
  cursor: pointer;
}

.clinical-toggle input {
  margin-top: 2px;
}
</style>
