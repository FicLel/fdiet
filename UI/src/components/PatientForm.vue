<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { usePatients } from '@/stores/patients'
import type { Patient, Sex } from '@/api/types'

/**
 * The patient selector's add/edit form. A patient given is rewritten; none
 * means somebody new, whom `create` switches to.
 */

const props = defineProps<{ patient: Patient | null }>()

const emit = defineEmits<{
  /** `created` is true for a new patient, false for an edit. */
  saved: [created: boolean]
  cancel: []
}>()

const patients = usePatients()

const name = ref(props.patient?.name ?? '')
/** Optional. They only suggest the reference profile a new diet is read against. */
const birthDate = ref(props.patient?.birthDate ?? '')
const sex = ref<Sex | ''>(props.patient?.sex ?? '')

const today = new Date().toISOString().slice(0, 10)

const nameField = ref<HTMLInputElement | null>(null)

onMounted(() => nameField.value?.focus())

async function save(): Promise<void> {
  const trimmed = name.value.trim()
  if (trimmed === '') {
    return
  }
  const request = {
    name: trimmed,
    birthDate: birthDate.value || null,
    sex: sex.value || null,
  }
  if (props.patient !== null) {
    // The note is not on this form, so it is sent back as it was.
    if (await patients.update(props.patient.id, { ...request, notes: props.patient.notes ?? null })) {
      emit('saved', false)
    }
    return
  }
  if (await patients.create(request)) {
    emit('saved', true)
  }
}
</script>

<template>
  <form class="add-form" @submit.prevent="save()">
    <span class="form-title">{{ patient === null ? 'Nuevo paciente' : 'Editar paciente' }}</span>
    <input
      ref="nameField"
      v-model="name"
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
      <button class="add-cancel" type="button" @click="emit('cancel')">Cancelar</button>
      <button
        class="add-save"
        type="submit"
        :disabled="name.trim() === '' || patients.saving.value"
      >
        Guardar
      </button>
    </div>
  </form>
</template>

<style scoped>
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
</style>
