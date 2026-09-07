<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { usePatients } from '@/stores/patients'
import { useDietDraft } from '@/stores/dietDraft'

/**
 * Writes this week again for somebody else.
 *
 * The copy carries the days, the dishes, the sentences they were typed as, and
 * every food match already made — which in an imported diet is most of the work
 * a person did by hand. What it does not carry is the journal: what one patient
 * thought of a plate is their own record, and so is what they ate beside it.
 *
 * Two things are said before the click rather than after it. It is the *stored*
 * week that is copied, so unpublished edits are not in it — the store refuses
 * outright rather than copying a stale week. And the copy becomes the target's
 * diet in force, archiving whatever they were on, which the row says for any
 * patient who already has one.
 */

const patients = usePatients()
const draft = useDietDraft()

const open = ref(false)
const done = ref<string | null>(null)
const root = ref<HTMLElement | null>(null)

/** Everybody but the patient whose week is on screen. */
const others = computed(() =>
  patients.listing.value.filter((row) => row.patient.id !== draft.diet.value?.patientId),
)

const disabled = computed(() => draft.diet.value === null || draft.publishing.value)

function close(): void {
  open.value = false
  done.value = null
}

function toggle(): void {
  if (open.value) {
    close()
  } else {
    open.value = true
    done.value = null
  }
}

async function copyTo(patientId: number, name: string): Promise<void> {
  if (await draft.copyTo(patientId)) {
    // The menu stays open saying what happened. Switching to the copy would
    // take the nutritionist off the week they were working on without asking.
    done.value = `Copiada a ${name}. Es ya su dieta en curso.`
  } else {
    close()
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
  <div ref="root" class="copy">
    <button
      class="trigger"
      type="button"
      :disabled="disabled"
      :aria-expanded="open"
      aria-haspopup="menu"
      title="Copiar esta semana a otro paciente"
      @click="toggle()"
    >
      <svg
        width="13"
        height="13"
        viewBox="0 0 14 14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <rect x="2.5" y="2.5" width="7" height="7" rx="1.5" />
        <path d="M4.5 11.5h5a2 2 0 0 0 2-2v-5" />
      </svg>
      Copiar a…
    </button>

    <div v-if="open" class="menu" role="menu">
      <p v-if="done" class="done">{{ done }}</p>

      <template v-else>
        <p class="lead">
          Se copia la semana guardada: los días, los platos y las comidas ya enlazadas. Las
          puntuaciones y los extras del paciente no se copian.
        </p>

        <p v-if="others.length === 0" class="empty">
          No hay ningún otro paciente. Añade uno desde el selector de la izquierda.
        </p>

        <button
          v-for="row in others"
          :key="row.patient.id"
          class="option"
          type="button"
          role="menuitem"
          :disabled="draft.publishing.value"
          @click="copyTo(row.patient.id, row.patient.name)"
        >
          <span class="avatar">{{ row.patient.name.charAt(0).toUpperCase() }}</span>
          <span class="who">
            <span class="who-name">{{ row.patient.name }}</span>
            <!-- The copy takes the active slot, so a patient who already has a
                 week is about to have it archived. Said before the click. -->
            <span class="who-meta" :class="{ warn: row.hasDiet }">
              {{ row.hasDiet ? `Sustituye a «${row.meta}»` : 'Sin dieta asignada' }}
            </span>
          </span>
        </button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.copy {
  position: relative;
  flex: none;
}

.trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 11.5px;
  color: var(--ink-muted);
  white-space: nowrap;
}

.trigger:hover:not(:disabled) {
  border-color: var(--line-input);
  color: var(--sage-700);
}

.trigger:disabled {
  color: var(--ink-disabled);
}

.menu {
  position: absolute;
  top: 34px;
  right: 0;
  z-index: 90;
  width: 300px;
  padding: 5px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 5px;
  box-shadow: var(--shadow-menu);
}

.lead,
.empty {
  margin: 4px 8px 8px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--ink-faint);
}

.done {
  margin: 4px;
  padding: 9px 10px;
  border-radius: var(--radius);
  background: var(--sage-50);
  font-size: 12px;
  line-height: 1.45;
  color: var(--sage-700);
}

.option {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: var(--radius);
  text-align: left;
}

.option:hover:not(:disabled) {
  background: var(--sage-50);
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
  color: var(--ink-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.who-meta.warn {
  color: var(--amber-700);
}
</style>
