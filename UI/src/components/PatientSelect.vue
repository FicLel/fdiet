<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * Placeholder. The backend carries no patients — the `users` bounded context
 * was removed — so this names nobody and changes nothing. It is here because
 * the screen it belongs to is designed around one diet per patient, and the
 * shape of the header should not have to be rediscovered when that context
 * comes back. The names are bracketed to say so on sight.
 */

defineProps<{ dietName: string }>()

const PATIENTS = [
  { name: '[Paciente 1]', meta: 'Dieta en curso' },
  { name: '[Paciente 2]', meta: 'Dieta en curso' },
  { name: '[Paciente 3]', meta: 'Sin dieta asignada' },
]

const open = ref(false)
const current = ref(PATIENTS[0].name)
const root = ref<HTMLElement | null>(null)

function pick(name: string): void {
  current.value = name
  open.value = false
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
      type="button"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="open = !open"
    >
      <span class="name">{{ current }}</span>
      <span class="meta">{{ dietName }}</span>
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

    <div v-if="open" class="menu" role="listbox">
      <button
        v-for="(patient, index) in PATIENTS"
        :key="patient.name"
        class="option"
        :class="{ current: patient.name === current }"
        type="button"
        role="option"
        :aria-selected="patient.name === current"
        @click="pick(patient.name)"
      >
        <span class="initial">{{ index + 1 }}</span>
        <span class="who">
          <span class="who-name">{{ patient.name }}</span>
          <span class="who-meta">{{ patient.meta }}</span>
        </span>
      </button>
      <p class="note">
        Los pacientes llegarán cuando el backend vuelva a tener usuarios. De momento sólo hay una
        dieta activa.
      </p>
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
  padding: 0 10px 0 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink-muted);
}

.name {
  font-weight: 500;
  color: var(--ink-strong);
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
  width: 300px;
  padding: 5px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 5px;
  box-shadow: var(--shadow-menu);
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

.option:hover {
  background: var(--sage-50);
}

.option.current {
  background: var(--sage-50);
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
}

.who-meta {
  display: block;
  font-size: 11.5px;
  color: var(--ink-muted);
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
