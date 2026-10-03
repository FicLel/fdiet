<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import PatientForm from './PatientForm.vue'
import PatientDiets from './PatientDiets.vue'
import { usePatients } from '@/stores/patients'
import type { Patient } from '@/api/types'

/**
 * Who the screen is about, and how to add somebody it could be about.
 *
 * **It is not a login.** The backend has no security layer, so picking a name
 * here changes whose week is drawn and nothing else — nothing is unlocked and
 * nothing is hidden. The menu says so at the bottom rather than letting the
 * shape of the control imply otherwise.
 *
 * Each row carries the diet that patient is on, because that is the question
 * asked while picking one: who has a week written and who is still waiting.
 * In the builder each row also opens that patient's diets, to delete one — a
 * patient is only removed once they have none.
 */

defineProps<{
  /** The phone header has room for the name and nothing else. */
  compact?: boolean
  /** The nutritionist's screen: each row opens the patient's diets, with a delete per diet. */
  manageDiets?: boolean
}>()

const patients = usePatients()

const open = ref(false)
/** The add/edit form is showing. */
const adding = ref(false)
/** Which patient the form rewrites; null while it adds somebody new. */
const editing = ref<Patient | null>(null)
/** Which row is one click from being removed; only ever one at a time. */
const confirming = ref<number | null>(null)
/** Whose diets the menu is showing instead of the caseload. */
const viewingDiets = ref<Patient | null>(null)

const root = ref<HTMLElement | null>(null)

const label = computed(() => patients.selected.value?.name ?? 'Sin pacientes')

const meta = computed(() => {
  if (patients.status.value === 'loading') {
    return 'Cargando…'
  }
  return patients.selectedDiet.value?.name ?? 'Sin dieta asignada'
})

const initial = computed(() => label.value.trim().charAt(0).toUpperCase() || '·')

function resetForm(): void {
  adding.value = false
  editing.value = null
}

function close(): void {
  open.value = false
  confirming.value = null
  viewingDiets.value = null
  resetForm()
  patients.clearError()
}

function toggle(): void {
  if (open.value) {
    close()
  } else {
    open.value = true
  }
}

function pick(id: number): void {
  patients.select(id)
  close()
}

function startAdding(patient?: Patient): void {
  editing.value = patient ?? null
  adding.value = true
  confirming.value = null
  patients.clearError()
}

/** A new patient closes the menu: `create` already switched to them. */
function onSaved(created: boolean): void {
  if (created) {
    close()
  } else {
    resetForm()
  }
}

function showDiets(patient: Patient): void {
  resetForm()
  confirming.value = null
  patients.clearError()
  viewingDiets.value = patient
}

async function remove(id: number): Promise<void> {
  if (confirming.value !== id) {
    // A patient is removed in two clicks, never one: the first says which row,
    // the second means it.
    confirming.value = id
    patients.clearError()
    return
  }
  if (await patients.remove(id)) {
    confirming.value = null
  }
}
function onOutside(event: MouseEvent): void {
  if (open.value && root.value && !root.value.contains(event.target as Node)) {
    close()
  }
}

function onEscape(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close()
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onOutside)
  document.addEventListener('keydown', onEscape)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutside)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div ref="root" class="select">
    <button
      class="trigger"
      :class="{ compact }"
      type="button"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="toggle()"
    >
      <span class="initial">{{ initial }}</span>
      <span class="name">{{ label }}</span>
      <span v-if="!compact" class="meta">{{ meta }}</span>
      <svg
        width="14"
        height="14"
        viewBox="0 0 14 14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M3.5 5.5 7 9l3.5-3.5" />
      </svg>
    </button>

    <!-- The trigger sits on the right of the patient header, so on a phone the
         menu hangs from that edge instead of running off it. -->
    <div v-if="open" class="menu" :class="{ compact }" :role="viewingDiets ? 'dialog' : 'listbox'">
      <PatientDiets v-if="viewingDiets" :patient="viewingDiets" @back="viewingDiets = null" />

      <template v-else>
        <p v-if="patients.listing.value.length === 0" class="empty">
          Todavía no hay ningún paciente.
        </p>

        <div
          v-for="row in patients.listing.value"
          :key="row.patient.id"
          class="row"
          :class="{ current: row.patient.id === patients.selectedId.value }"
        >
          <button
            class="option"
            type="button"
            role="option"
            :aria-selected="row.patient.id === patients.selectedId.value"
            @click="pick(row.patient.id)"
          >
            <span class="avatar">{{ row.patient.name.charAt(0).toUpperCase() }}</span>
            <span class="who">
              <span class="who-name">{{ row.patient.name }}</span>
              <span class="who-meta" :class="{ none: !row.hasDiet }">{{ row.meta }}</span>
            </span>
          </button>

          <button
            v-if="manageDiets"
            class="remove diets"
            type="button"
            :title="`Dietas de ${row.patient.name}, para eliminar alguna`"
            @click="showDiets(row.patient)"
          >
            Dietas
          </button>

          <button
            class="remove"
            type="button"
            :disabled="patients.saving.value"
            :title="`Editar a ${row.patient.name}`"
            :aria-label="`Editar a ${row.patient.name}`"
            @click="startAdding(row.patient)"
          >
            <svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M9.5 2.5l2 2L5 11H3V9z" />
            </svg>
          </button>

          <button
            class="remove"
            :class="{ armed: confirming === row.patient.id }"
            type="button"
            :disabled="patients.saving.value"
            :title="
              confirming === row.patient.id
                ? 'Pulsa otra vez para quitarlo'
                : `Quitar a ${row.patient.name}`
            "
            @click="remove(row.patient.id)"
          >
            {{ confirming === row.patient.id ? '¿Seguro?' : '×' }}
          </button>
        </div>

        <p v-if="patients.error.value" class="error">{{ patients.error.value }}</p>

        <div class="foot">
          <PatientForm
            v-if="adding"
            :key="editing?.id ?? 'new'"
            :patient="editing"
            @saved="onSaved"
            @cancel="resetForm()"
          />

          <button v-else class="add" type="button" @click="startAdding()">
            <svg
              width="13"
              height="13"
              viewBox="0 0 14 14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              aria-hidden="true"
            >
              <path d="M7 3v8M3 7h8" />
            </svg>
            Añadir paciente
          </button>

          <!-- The shape of this control suggests a login. It is not one, and the
               note is here so nobody has to find that out by trying. -->
          <p class="note">
            Cambiar de paciente cambia la semana que se ve. No hay cuentas ni contraseñas: cualquiera
            puede ver la dieta de cualquiera.
          </p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.select {
  position: relative;
}

.trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px 0 6px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink-muted);
}

.trigger.compact {
  height: 32px;
  padding: 0 6px 0 4px;
}

.initial {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--sage-100);
  color: var(--sage-700);
  font-size: 11px;
  font-weight: 600;
}

.name {
  font-weight: 500;
  color: var(--ink-strong);
  max-width: 18ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  font-size: 11px;
  color: var(--ink-muted);
  max-width: 16ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu {
  position: absolute;
  top: 40px;
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

.menu.compact {
  left: auto;
  right: 0;
  width: min(320px, calc(100vw - 24px));
}

.empty {
  margin: 6px 10px 10px;
  font-size: 12px;
  color: var(--ink-faint);
}

.row {
  display: flex;
  align-items: stretch;
  gap: 2px;
  border-radius: var(--radius);
}

.row:hover,
.row.current {
  background: var(--sage-50);
}

.option {
  display: flex;
  flex: 1 1 0;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: var(--radius);
  text-align: left;
}

.avatar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--sage-100);
  color: var(--sage-700);
  font-size: 10px;
  font-weight: 600;
}

.who {
  flex: 1 1 0;
  min-width: 0;
}

.who-name {
  display: block;
  font-weight: 500;
  color: var(--ink-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.who-meta {
  display: block;
  font-size: 11.5px;
  color: var(--ink-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.who-meta.none {
  color: var(--ink-faint);
  font-style: italic;
}

.remove {
  flex: none;
  padding: 0 8px;
  border-radius: var(--radius);
  font-size: 13px;
  line-height: 1;
  color: var(--ink-faint);
}

.remove:hover {
  color: var(--amber-700);
}

.remove.diets {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ink-muted);
}

.remove.diets:hover {
  color: var(--sage-700);
}

.remove.armed {
  font-size: 11px;
  font-weight: 500;
  color: var(--amber-700);
}

.remove:disabled {
  color: var(--ink-disabled);
}

.error {
  margin: 6px 4px 2px;
  padding: 7px 8px;
  border-radius: var(--radius);
  background: var(--amber-50);
  font-size: 11.5px;
  line-height: 1.45;
  color: var(--amber-700);
}

.foot {
  margin-top: 5px;
  padding-top: 5px;
  border-top: 1px solid var(--line-soft);
}

.add {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 9px 10px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--sage-700);
  text-align: left;
}

.add:hover {
  background: var(--sage-50);
}

.note {
  margin: 6px 4px 4px;
  padding-top: 8px;
  border-top: 1px solid var(--line-soft);
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}
</style>
