<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useFoodLink } from '@/stores/foodLink'
import { integer, NO_VALUE, quantity } from '@/domain/format'
import { dayName } from '@/domain/week'

/**
 * Matching one written ingredient to a food of the catalogue.
 *
 * It takes the right rail while it is open, because choosing a food is the
 * whole of what the nutritionist is doing at that moment and the dish's figures
 * are exactly what it is about to change.
 *
 * The ranked candidates come first and the search box below them, in that
 * order: the backend has already scored all 957 generic names against these
 * words, and most of the time the answer is in the first three.
 */

const link = useFoodLink()

const box = ref<HTMLInputElement | null>(null)

const at = computed(() => link.target.value)

/** Where in the week this ingredient sits, so the rail says what the grid shows. */
const where = computed(() =>
  at.value ? `${at.value.row.slotLabel} · ${dayName(at.value.day)}` : '',
)

/** The ranked candidates until something is typed, then what was searched for. */
const searched = computed(() => link.term.value.trim() !== '')

const list = computed(() => (searched.value ? link.results.value : link.ranked.value))

const listNote = computed(() => {
  if (link.searching.value) {
    return 'Buscando…'
  }
  if (searched.value) {
    return list.value.length === 0
      ? 'Ningún alimento del catálogo se llama así.'
      : `${list.value.length} resultado${list.value.length === 1 ? '' : 's'}`
  }
  if (link.loadingSuggestions.value) {
    return 'Buscando candidatos…'
  }
  return list.value.length === 0
    ? 'Ningún alimento se parece lo bastante. Búscalo por su nombre.'
    : 'Ordenados por parecido; ninguno está elegido.'
})

/** The keyboard lands in the search box, which is what the drawer is for. */
watch(
  () => at.value?.id,
  (id) => {
    if (id !== undefined) {
      void nextTick(() => box.value?.focus())
    }
  },
  { immediate: true },
)
</script>

<template>
  <aside v-if="at" class="panel">
    <div class="card">
      <div class="card-head">
        <div class="head-line">
          <span class="title">Vincular ingrediente</span>
          <button class="icon" type="button" aria-label="Cerrar" @click="link.close()">
            <svg width="15" height="15" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" aria-hidden="true">
              <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" />
            </svg>
          </button>
        </div>
        <div class="subject">
          <span class="subject-name">{{ at.name }}</span>
          <span class="num subject-qty">{{ quantity(at.quantity, at.unit) }}</span>
        </div>
        <div class="where">{{ where }}</div>
        <div v-if="at.matchedName" class="current">
          Ahora vinculado a <strong>{{ at.matchedName }}</strong>
        </div>
      </div>

      <div class="queue">
        <template v-if="link.position.value > 0">
          <span class="num">{{ link.position.value }} de {{ link.remaining.value }}</span>
          sin vincular en la semana
          <button class="skip" type="button" @click="link.skip()">Saltar</button>
        </template>
        <template v-else>
          Corrigiendo un vínculo ya hecho.
          <span v-if="link.remaining.value > 0" class="queue-aside">
            Quedan <span class="num">{{ link.remaining.value }}</span> sin vincular.
          </span>
        </template>
      </div>

      <div class="finder">
        <div class="halves" role="group" aria-label="Mitad del catálogo">
          <button
            type="button"
            :class="{ on: link.half.value === 'bedca' }"
            @click="link.useHalf('bedca')"
          >
            Genéricos
          </button>
          <button
            type="button"
            :class="{ on: link.half.value === 'branded' }"
            @click="link.useHalf('branded')"
          >
            De marca
          </button>
        </div>

        <div class="box">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="var(--ink-muted)" stroke-width="1.5" stroke-linecap="round" aria-hidden="true">
            <circle cx="6.2" cy="6.2" r="4" />
            <path d="M9.2 9.2l2.6 2.6" />
          </svg>
          <input
            ref="box"
            :value="link.term.value"
            :placeholder="
              link.half.value === 'bedca' ? 'Buscar en el catálogo' : 'Buscar un producto'
            "
            aria-label="Buscar en el catálogo"
            @input="link.search(($event.target as HTMLInputElement).value)"
          />
          <button
            v-if="searched"
            class="icon clear"
            type="button"
            aria-label="Borrar la búsqueda"
            @click="link.search('')"
          >
            <svg width="13" height="13" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" aria-hidden="true">
              <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" />
            </svg>
          </button>
        </div>

        <p class="note">{{ listNote }}</p>
      </div>

      <p v-if="link.error.value" class="banner">{{ link.error.value }}</p>

      <div class="scroll results">
        <button
          v-for="result in list"
          :key="result.key"
          class="result"
          type="button"
          :disabled="link.linking.value"
          @click="link.link(result)"
        >
          <span class="result-head">
            <span v-if="result.note" class="result-note">{{ result.note }}</span>
            <span v-if="result.code" class="num result-code">{{ result.code }}</span>
            <span
              v-if="result.score !== null"
              class="num result-score"
              title="Cuánto del nombre explican las palabras del ingrediente, de 0 a 100"
            >
              {{ result.score }}
            </span>
          </span>
          <span class="result-name">{{ result.name }}</span>
          <span class="result-foot">
            <!-- A candidate is ranked by its name alone — the ranking carries no
                 figures — so it shows none. An em dash here would claim the
                 source published no energy for it, which is a different thing. -->
            <span v-if="result.score === null" class="num result-kcal">
              {{ result.kcalPer100 === null ? NO_VALUE : integer(result.kcalPer100) }} kcal/100 g
            </span>
            <span class="result-add">
              <svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">
                <path d="M7 3v8M3 7h8" />
              </svg>
              Vincular
            </span>
          </span>
        </button>
      </div>

      <p class="footnote">
        Vincular guarda al momento y no cambia lo que lee el paciente: la dieta sigue diciendo
        «{{ at.name }}». Lo que gana es de qué alimento son sus cifras.
      </p>
    </div>
  </aside>
</template>

<style scoped>
.panel {
  width: 380px;
  flex: none;
  padding: 18px 24px 20px 4px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.card {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card-head {
  flex: none;
  padding: 13px 15px 12px;
  border-bottom: 1px solid var(--line-soft);
}

.head-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sage-700);
}

.icon {
  margin-left: auto;
  display: flex;
  padding: 2px;
  color: var(--ink-faint);
}

.icon:hover {
  color: var(--ink);
}

.subject {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 6px;
}

.subject-name {
  flex: 1 1 0;
  min-width: 0;
  font-weight: 600;
  font-size: 14px;
  color: var(--ink-strong);
}

.subject-qty {
  flex: none;
  font-size: 12px;
  color: var(--ink-muted);
}

.where {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--ink-faint);
}

.current {
  margin-top: 7px;
  padding: 6px 8px;
  border-radius: var(--radius);
  background: var(--sage-50);
  font-size: 11.5px;
  color: var(--ink);
}

.current strong {
  font-weight: 500;
  color: var(--ink-strong);
}

.queue {
  display: flex;
  align-items: center;
  gap: 5px;
  flex: none;
  padding: 8px 15px;
  background: var(--surface-muted);
  border-bottom: 1px solid var(--line-soft);
  font-size: 11.5px;
  color: var(--ink-muted);
}

.queue-aside {
  color: var(--ink-faint);
}

.skip {
  margin-left: auto;
  height: 22px;
  padding: 0 9px;
  border: 1px solid var(--line-input);
  border-radius: 3px;
  font-size: 11px;
  font-weight: 500;
  color: var(--ink);
  background: var(--surface);
}

.finder {
  flex: none;
  padding: 12px 15px 10px;
}

.halves {
  display: flex;
  gap: 4px;
  margin-bottom: 9px;
}

.halves button {
  flex: 1 1 0;
  height: 26px;
  border: 1px solid var(--line);
  border-radius: 3px;
  font-size: 11.5px;
  color: var(--ink-muted);
  background: var(--surface);
}

.halves button.on {
  border-color: var(--sage-200);
  color: var(--sage-700);
  background: var(--sage-50);
  font-weight: 500;
}

.box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--line-input);
  border-radius: var(--radius);
}

.box input {
  flex: 1 1 0;
  min-width: 0;
  border: 0;
  outline: none;
  font-size: 12.5px;
  background: transparent;
}

.clear {
  margin-left: 0;
}

.note {
  margin: 8px 0 0;
  font-size: 10.5px;
  color: var(--ink-faint);
}

.banner {
  flex: none;
  margin: 0 15px 8px;
  padding: 7px 9px;
  border-radius: var(--radius);
  background: var(--amber-50);
  color: var(--amber-700);
  font-size: 11.5px;
}

.results {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 0 15px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.result {
  display: block;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  text-align: left;
  background: var(--surface);
}

.result:hover:not(:disabled) {
  border-color: var(--sage-200);
  background: var(--sage-50);
}

.result:disabled {
  opacity: 0.6;
}

.result-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-height: 12px;
}

.result-note {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--amber-700);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-code,
.result-score {
  margin-left: auto;
  flex: none;
  font-size: 10px;
  color: var(--ink-faint);
}

.result-name {
  display: block;
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.3;
  color: var(--ink-strong);
}

.result-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.result-kcal {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ink);
}

.result-add {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  color: var(--sage-700);
}

.footnote {
  flex: none;
  margin: 0;
  padding: 11px 15px 12px;
  border-top: 1px solid var(--line-soft);
  font-size: 11px;
  line-height: 1.5;
  color: var(--ink-faint);
}
</style>
