<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import type { DietImportSummary, ReferenceProfile } from '@/api/types'
import { useDietDraft } from '@/stores/dietDraft'
import { usePatients } from '@/stores/patients'
import { useReference } from '@/stores/reference'
import { integer } from '@/domain/format'

/**
 * A new week for the selected patient: in blank, to be written in the grid, or
 * read from a workbook. Either becomes their diet in force and archives the one
 * they were on — said before the click, not after it.
 *
 * The reference profile is *proposed* from the patient's age and chosen by a
 * person: the machine offers, the nutritionist decides.
 */

const props = defineProps<{ mode: 'blank' | 'import' }>()
const emit = defineEmits<{ close: [] }>()

const draft = useDietDraft()
const patients = usePatients()
const reference = useReference()

const tab = ref<'blank' | 'import'>(props.mode)
const name = ref('')
const startedOn = ref(new Date().toISOString().slice(0, 10))
const profileCode = ref<string>('')
const clinical = ref(false)
const file = ref<File | null>(null)
const sheet = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
const summary = ref<DietImportSummary | null>(null)
const profiles = shallowRef<ReferenceProfile[]>([])

const patient = computed(() => patients.selected.value)
const replaces = computed(() => patients.selectedDiet.value)

const canSubmit = computed(
  () =>
    !busy.value &&
    patient.value !== null &&
    (tab.value === 'blank' ? name.value.trim() !== '' : file.value !== null),
)

onMounted(async () => {
  document.addEventListener('keydown', onEscape)
  name.value = patient.value ? `Dieta de ${patient.value.name}` : ''
  try {
    profiles.value = await reference.profilesFor(patients.ageInMonths(patient.value))
    profileCode.value = profiles.value.find((profile) => profile.suggested)?.code ?? ''
  } catch {
    profiles.value = []
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onEscape)
})

function onEscape(event: KeyboardEvent): void {
  if (event.key === 'Escape' && !busy.value) {
    emit('close')
  }
}

function onFile(event: Event): void {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function submit(): Promise<void> {
  if (!canSubmit.value) {
    return
  }
  busy.value = true
  error.value = null
  // A blank is somebody choosing no profile. When the list never loaded there
  // was nothing to choose from, so the backend proposes one by age instead.
  const chosenProfile = profiles.value.length === 0 ? null : profileCode.value
  try {
    if (tab.value === 'blank') {
      const failed = await draft.createEmpty({
        name: name.value.trim(),
        startedOn: startedOn.value,
        referenceProfileCode: chosenProfile,
        clinical: clinical.value,
      })
      if (failed) {
        error.value = failed
      } else {
        emit('close')
      }
      return
    }
    const answer = await draft.importWorkbook({
      file: file.value!,
      sheet: sheet.value.trim() || undefined,
      name: name.value.trim() || undefined,
      startedOn: startedOn.value || undefined,
      referenceProfile: chosenProfile,
      clinical: clinical.value,
    })
    if (typeof answer === 'string') {
      error.value = answer
    } else {
      summary.value = answer
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="overlay" @mousedown.self="!busy && emit('close')">
    <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="new-diet-title">
      <div class="head">
        <h2 id="new-diet-title" class="title">Nueva dieta para {{ patient?.name ?? '—' }}</h2>
        <button class="icon" type="button" aria-label="Cerrar" :disabled="busy" @click="emit('close')">
          <svg width="15" height="15" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" aria-hidden="true">
            <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" />
          </svg>
        </button>
      </div>

      <div v-if="summary" class="body">
        <p class="done">
          Importada «{{ summary.name }}» de la hoja «{{ summary.sheet }}»:
          {{ integer(summary.days) }} días, {{ integer(summary.dishes) }} platos y
          {{ integer(summary.ingredients) }} ingredientes.
        </p>
        <p class="hint">
          {{ integer(summary.resolved) }} se han vinculado solos al catálogo;
          {{ integer(summary.unresolved) }} esperan a que elijas su alimento con «Repasar sin
          vincular».
        </p>
        <div class="foot">
          <button class="primary" type="button" @click="emit('close')">Ver la semana</button>
        </div>
      </div>

      <form v-else class="body" @submit.prevent="submit()">
        <div class="tabs" role="tablist">
          <button type="button" role="tab" :aria-selected="tab === 'blank'" :class="{ on: tab === 'blank' }" @click="tab = 'blank'">
            En blanco
          </button>
          <button type="button" role="tab" :aria-selected="tab === 'import'" :class="{ on: tab === 'import' }" @click="tab = 'import'">
            Desde Excel
          </button>
        </div>

        <label v-if="tab === 'import'" class="field">
          <span class="label">Libro de Excel</span>
          <input class="input" type="file" accept=".xlsx" @change="onFile" />
        </label>
        <label v-if="tab === 'import'" class="field">
          <span class="label">Hoja <span class="optional">(la primera si se deja vacía)</span></span>
          <input v-model="sheet" class="input" type="text" maxlength="100" />
        </label>

        <label class="field">
          <span class="label">
            Nombre <span v-if="tab === 'import'" class="optional">(el de la hoja si se deja vacío)</span>
          </span>
          <input v-model="name" class="input" type="text" maxlength="255" />
        </label>

        <label class="field">
          <span class="label">Empieza el</span>
          <input v-model="startedOn" class="input" type="date" />
        </label>

        <label class="field">
          <span class="label">Población de referencia</span>
          <select v-model="profileCode" class="input">
            <option value="">Sin población de referencia</option>
            <option v-for="profile in profiles" :key="profile.code" :value="profile.code">
              {{ profile.sourceShortName }} · {{ profile.label }}{{ profile.suggested ? ' — propuesta por edad' : '' }}
            </option>
          </select>
          <span class="hint">
            <template v-if="patient && !patient.birthDate">
              Sin fecha de nacimiento no se propone ninguna; puedes elegirla igualmente.
            </template>
            <template v-else>
              Con ella se cuentan raciones y frecuencias, de forma orientativa. Se puede cambiar
              después sin tocar la dieta.
            </template>
          </span>
        </label>

        <label class="check">
          <input v-model="clinical" type="checkbox" />
          <span>
            Dieta clínica
            <span class="hint">Cuenta además raciones de hidratos de carbono (10 g).</span>
          </span>
        </label>

        <p v-if="replaces" class="warn">
          Sustituye a «{{ replaces.name }}», que pasa al historial de {{ patient?.name }}.
        </p>
        <p v-if="error" class="error">{{ error }}</p>

        <div class="foot">
          <button class="secondary" type="button" :disabled="busy" @click="emit('close')">Cancelar</button>
          <button class="primary" type="submit" :disabled="!canSubmit">
            {{ busy ? (tab === 'blank' ? 'Creando…' : 'Importando…') : tab === 'blank' ? 'Crear semana' : 'Importar' }}
          </button>
        </div>
      </form>
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
  width: min(440px, 100%);
  max-height: calc(100vh - 32px);
  overflow-y: auto;
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

.body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px 16px;
}

.tabs {
  display: flex;
  gap: 4px;
}

.tabs button {
  flex: 1 1 0;
  height: 28px;
  border: 1px solid var(--line);
  border-radius: 3px;
  font-size: 12px;
  color: var(--ink-muted);
}

.tabs button.on {
  border-color: var(--sage-200);
  color: var(--sage-700);
  background: var(--sage-50);
  font-weight: 500;
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

input[type='file'].input {
  padding: 6px 9px;
}

.hint {
  display: block;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.check {
  display: flex;
  gap: 9px;
  font-weight: 500;
  color: var(--ink-strong);
}

.check input {
  margin-top: 3px;
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
  padding: 9px 10px;
  border-radius: var(--radius);
  background: var(--sage-50);
  font-size: 12.5px;
  line-height: 1.5;
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
</style>
