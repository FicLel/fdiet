<script setup lang="ts">
import { computed, watch } from 'vue'
import type { DietSummary, Patient } from '@/api/types'
import { deletionWarning } from '@/domain/dietDeletion'
import { shortDate } from '@/domain/week'
import { usePatientDiets } from '@/stores/patientDiets'
import { useDietDraft } from '@/stores/dietDraft'
import { useViewport } from '@/composables/useViewport'

/**
 * One patient's diets, inside the patient selector: the one in force and the
 * archived ones, each with a delete.
 *
 * It lives beside the patient because that is where the question comes up —
 * a patient cannot be removed while they have diets, and this is where those
 * diets are cleared. The confirm is in the page, under the row it is about,
 * and names what goes with the diet before anything is deleted.
 */

const props = defineProps<{ patient: Patient }>()

defineEmits<{ back: [] }>()

const diets = usePatientDiets()
const draft = useDietDraft()
/** A phone gets thumb-sized controls. */
const { mobile } = useViewport()

watch(
  () => props.patient.id,
  (id) => void diets.load(id),
  { immediate: true },
)

const rows = computed<DietSummary[]>(() =>
  diets.active.value ? [diets.active.value, ...diets.archived.value] : diets.archived.value,
)

const warning = computed(() => {
  const pending = diets.pending.value
  return pending ? deletionWarning(pending.diet.name, pending.counts) : ''
})

/** The week on screen is the one being deleted, with cells typed but not published. */
const losesDraft = computed(
  () =>
    diets.pending.value !== null &&
    diets.pending.value.diet.id === draft.diet.value?.id &&
    draft.dirtyCount.value > 0,
)

function isActive(diet: DietSummary): boolean {
  return diet.id === diets.active.value?.id
}

function period(diet: DietSummary): string {
  const from = shortDate(diet.startedOn)
  return diet.endedOn ? `${from} – ${shortDate(diet.endedOn)}` : `desde ${from}`
}
</script>

<template>
  <div class="diets" :class="{ touch: mobile }">
    <div class="head">
      <button class="back" type="button" aria-label="Volver a los pacientes" @click="$emit('back')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M8.5 3.5 5 7l3.5 3.5" />
        </svg>
      </button>
      <span class="title">
        <span class="kicker">Dietas de</span>
        <span class="who">{{ patient.name }}</span>
      </span>
    </div>

    <p v-if="diets.status.value === 'loading'" class="quiet">Cargando…</p>
    <p v-else-if="diets.status.value === 'ready' && rows.length === 0" class="quiet">
      {{ patient.name }} no tiene ninguna dieta.
    </p>

    <ul v-if="rows.length > 0" class="list">
      <li v-for="diet in rows" :key="diet.id" class="item">
        <div class="line">
          <span class="what">
            <span class="name">{{ diet.name }}</span>
            <span class="meta">
              <span class="badge" :class="{ live: isActive(diet) }">
                {{ isActive(diet) ? 'En vigor' : 'Archivada' }}
              </span>
              <span class="num">{{ period(diet) }}</span>
            </span>
          </span>
          <button
            class="delete"
            type="button"
            :disabled="diets.deleting.value || diets.asking.value !== null"
            :aria-label="`Eliminar la dieta ${diet.name}`"
            @click="diets.ask(diet)"
          >
            {{ diets.asking.value === diet.id ? 'Comprobando…' : 'Eliminar' }}
          </button>
        </div>

        <div v-if="diets.pending.value?.diet.id === diet.id" class="confirm" role="alertdialog" aria-live="polite">
          <p class="warning">{{ warning }}</p>
          <p v-if="isActive(diet)" class="hint">
            {{ patient.name }} se quedará sin dieta en vigor; ninguna archivada vuelve a activarse.
          </p>
          <p v-if="losesDraft" class="hint">Los platos sin publicar de esta semana también se perderán.</p>
          <div class="actions">
            <button class="cancel" type="button" :disabled="diets.deleting.value" @click="diets.cancel()">
              Cancelar
            </button>
            <button class="confirm-delete" type="button" :disabled="diets.deleting.value" @click="diets.confirm()">
              {{ diets.deleting.value ? 'Eliminando…' : 'Eliminar dieta' }}
            </button>
          </div>
        </div>
      </li>
    </ul>

    <button v-if="diets.nextPage.value !== null" class="more" type="button" @click="diets.loadMore()">
      Ver más dietas archivadas
    </button>

    <p v-if="diets.error.value" class="error">{{ diets.error.value }}</p>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 2px 7px;
  border-bottom: 1px solid var(--line-soft);
}

.back {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius);
  color: var(--ink-muted);
}

.back:hover {
  background: var(--sage-50);
  color: var(--ink-strong);
}

.title {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.kicker {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.who {
  font-weight: 500;
  color: var(--ink-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quiet {
  margin: 10px 8px;
  font-size: 12px;
  color: var(--ink-faint);
}

.list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.item {
  border-bottom: 1px solid var(--line-soft);
}

.item:last-child {
  border-bottom: none;
}

.line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 6px 8px 8px;
}

.what {
  flex: 1 1 0;
  min-width: 0;
}

.name {
  display: block;
  font-weight: 500;
  color: var(--ink-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-muted);
}

.badge {
  padding: 0 5px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 10.5px;
  color: var(--ink-muted);
}

.badge.live {
  border-color: var(--sage-200);
  color: var(--sage-700);
}

.delete {
  flex: none;
  height: 32px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 12px;
  color: var(--ink-muted);
}

.delete:hover:not(:disabled) {
  border-color: var(--amber-400);
  color: var(--amber-700);
}

.delete:disabled {
  color: var(--ink-disabled);
}

.confirm {
  margin: 0 6px 8px;
  padding: 8px 9px;
  border: 1px solid var(--extra-line);
  border-radius: var(--radius);
  background: var(--amber-50);
}

.warning {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--ink-strong);
}

.hint {
  margin: 5px 0 0;
  font-size: 11.5px;
  line-height: 1.4;
  color: var(--amber-700);
}

.actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.cancel,
.confirm-delete {
  flex: 1 1 0;
  height: 32px;
  border-radius: var(--radius);
  font-weight: 500;
}

.cancel {
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
}

.confirm-delete {
  border: 1px solid var(--amber-700);
  background: var(--surface);
  color: var(--amber-700);
}

.confirm-delete:disabled,
.cancel:disabled {
  color: var(--ink-disabled);
  border-color: var(--line);
}

.more {
  width: 100%;
  height: 34px;
  margin-top: 4px;
  border-radius: var(--radius);
  font-weight: 500;
  color: var(--sage-700);
}

.more:hover {
  background: var(--sage-50);
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

/* A phone: every control a thumb can hit. */
.touch .back,
.touch .delete,
.touch .cancel,
.touch .confirm-delete,
.touch .more {
  min-height: 44px;
}

.touch .back {
  width: 44px;
}
</style>
