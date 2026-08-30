<script setup lang="ts">
import { computed } from 'vue'
import type { ExtraFood } from '@/api/types'
import { integer, NO_VALUE, quantity } from '@/domain/format'

/**
 * One thing eaten off the plan.
 *
 * It wears the amber the rest of the screen keeps for "not the plan", and it
 * says plainly when it counts towards nothing: an entry nobody matched to a
 * food, or one measured in something nothing can weigh, is on the record but
 * not in the total, and a blank where a number should be has to say which.
 */

const props = defineProps<{
  extra: ExtraFood
  /** The roomier form the mobile list uses. */
  large?: boolean
  disabled?: boolean
}>()

defineEmits<{ remove: [] }>()

const kcal = computed(() => props.extra.nutrition?.energyKcal ?? null)

const portion = computed(() => {
  const amount = quantity(props.extra.quantity, props.extra.unit)
  return props.extra.brand ? `${amount} · ${props.extra.brand}` : amount
})

/** Why there is no figure, when there is none. */
const missing = computed(() => {
  if (kcal.value !== null) {
    return ''
  }
  return props.extra.bedcaFoodId === null && props.extra.foodItemId === null
    ? 'sin vincular a un alimento'
    : 'no se puede pesar'
})
</script>

<template>
  <div class="extra" :class="{ large }">
    <span class="body">
      <span v-if="extra.brand" class="brand">{{ extra.brand }}</span>
      <span class="name">{{ extra.name }}</span>
      <span class="num portion">{{ portion }}</span>
      <span v-if="missing" class="missing">{{ missing }}</span>
    </span>

    <span class="figure">
      <span v-if="kcal !== null" class="srf num kcal">+{{ integer(kcal) }}</span>
      <span v-else class="num kcal none">{{ NO_VALUE }}</span>
      <span v-if="kcal !== null" class="unit">kcal</span>
    </span>

    <button
      class="remove"
      type="button"
      :disabled="disabled"
      :title="`Quitar ${extra.name}`"
      :aria-label="`Quitar ${extra.name}`"
      @click="$emit('remove')"
    >
      <svg
        width="15"
        height="15"
        viewBox="0 0 14 14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        aria-hidden="true"
      >
        <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.extra {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 10px;
  background: var(--extra-surface);
  border: 1px solid var(--extra-line);
  border-radius: var(--radius);
}

.extra.large {
  gap: 12px;
  padding: 11px 14px;
  border-radius: 8px;
}

.body {
  flex: 1 1 0;
  min-width: 0;
}

.brand {
  display: block;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--extra-ink);
}

.name {
  display: -webkit-box;
  margin-top: 2px;
  font-size: 11.5px;
  line-height: 1.3;
  color: var(--ink-strong);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.large .name {
  font-size: 12.5px;
  line-height: 1.35;
}

.portion {
  display: block;
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--extra-ink);
}

.missing {
  display: block;
  margin-top: 2px;
  font-size: 10.5px;
  color: var(--ink-faint);
}

.figure {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.kcal {
  font-size: 13px;
  font-weight: 500;
  color: var(--extra-ink);
}

.large .kcal {
  font-size: 17px;
}

.kcal.none {
  color: var(--ink-disabled);
}

.unit {
  font-size: 10px;
  color: var(--extra-ink);
}

.remove {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1px;
  color: #b9a583;
}

/* A finger needs 44 px; a cursor does not, so only the roomy form grows. */
.large .remove {
  width: 44px;
  height: 44px;
  padding: 0;
}

.remove:hover:not(:disabled) {
  color: var(--extra-ink);
}

.remove:disabled {
  color: var(--ink-disabled);
}
</style>
