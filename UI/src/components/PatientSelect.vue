<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { usePatients } from '@/stores/patients'
import type { Patient, Sex } from '@/api/types'

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
 */

defineProps<{
  /** The phone header has room for the name and nothing else. */
  compact?: boolean
}>()

const patients = usePatients()

const open = ref(false)
/** The add/edit form is showing. */
const adding = ref(false)
/** Which patient the form rewrites; null while it adds somebody new. */
const editingId = ref<number | null>(null)
const newName = ref('')
/** Optional. They only suggest the reference profile a new diet is read against. */
const birthDate = ref('')
const sex = ref<Sex | ''>('')
/** Which row is one click from being removed; only ever one at a time. */
const confirming = ref<number | null>(null)

const today = new Date().toISOString().slice(0, 10)

const root = ref<HTMLElement | null>(null)
const nameField = ref<HTMLInputElement | null>(null)

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
  editingId.value = null
  newName.value = ''
  birthDate.value = ''
  sex.value = ''
}

function close(): void {
  open.value = false
  confirming.value = null
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

async function startAdding(patient?: Patient): Promise<void> {
  resetForm()
  adding.value = true
  confirming.value = null
  if (patient) {
    editingId.value = patient.id
    newName.value = patient.name
    birthDate.value = patient.birthDate ?? ''
    sex.value = patient.sex ?? ''
  }
  patients.clearError()
  await nextTick()
  nameField.value?.focus()
}

async function add(): Promise<void> {
  const name = newName.value.trim()
  if (name === '') {
    return
  }
  const request = {
    name,
    birthDate: birthDate.value || null,
    sex: sex.value || null,
  }
  if (editingId.value !== null) {
    const patient = patients.patients.value.find((row) => row.id === editingId.value)
    // The note is not on this form, so it is sent back as it was.
    if (await patients.update(editingId.value, { ...request, notes: patient?.notes ?? null })) {
      resetForm()
    }
    return
  }
  // `create` switches to whoever was just added, which is what somebody who
  // has just typed a name wants next.
  if (await patients.create(request)) {
    close()
  }
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
    <div v-if="open" class="menu" :class="{ compact }" role="listbox">
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
        <form v-if="adding" class="add-form" @submit.prevent="add()">
          <span class="form-title">{{ editingId === null ? 'Nuevo paciente' : 'Editar paciente' }}</span>
          <input
            ref="nameField"
            v-model="newName"
            class="add-field"
            type="text"
            maxlength="255"
            placeholder="Nombre del paciente"
            aria-label="Nombre"
            :disabled="patients.saving.value"
          />
          <div class="form-line">
            <label class="mini">
              <span>Nacimiento</span>
              <input
                v-model="birthDate"
                class="add-field"
                type="date"
                :max="today"
                :disabled="patients.saving.value"
              />
            </label>
            <label class="mini">
              <span>Sexo</span>
              <select v-model="sex" class="add-field" :disabled="patients.saving.value">
                <option value="">Sin indicar</option>
                <option value="FEMALE">Mujer</option>
                <option value="MALE">Hombre</option>
              </select>
            </label>
          </div>
          <p class="form-hint">
            Opcionales. Sólo sirven para proponer la población de referencia de una dieta nueva.
          </p>
          <div class="form-line">
            <button class="add-cancel" type="button" @click="resetForm()">Cancelar</button>
            <button
              class="add-save"
              type="submit"
              :disabled="newName.trim() === '' || patients.saving.value"
            >
              Guardar
            </button>
          </div>
        </form>

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

.add-form {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 7px 4px 5px;
}

.form-title {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.form-line {
  display: flex;
  gap: 6px;
}

.mini {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 10.5px;
  color: var(--ink-muted);
}

.form-hint {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.4;
  color: var(--ink-faint);
}

.add-cancel {
  flex: 1 1 0;
  height: 32px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--ink);
}

.add-field {
  flex: 1 1 0;
  min-width: 0;
  width: 100%;
  height: 32px;
  padding: 0 9px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink);
}

.add-save {
  flex: 1 1 0;
  height: 32px;
  padding: 0 12px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--surface);
  background: var(--sage-700);
}

.add-save:disabled {
  background: #c2ccc6;
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
