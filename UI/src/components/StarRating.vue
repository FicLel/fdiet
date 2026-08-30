<script setup lang="ts">
/**
 * The one thing the patient may say about a plate.
 *
 * Pressing the star already given takes the rating back rather than setting it
 * to zero — the backend has no score of zero on purpose, since "no opinion"
 * has to stay out of the average instead of dragging it down. The parent turns
 * that press into a DELETE.
 */

withDefaults(
  defineProps<{
    score: number
    /** Bigger stars where the target has to be a finger, not a cursor. */
    size?: number
    disabled?: boolean
  }>(),
  { size: 16, disabled: false },
)

defineEmits<{ pick: [value: number] }>()

const STARS = [1, 2, 3, 4, 5]
</script>

<template>
  <span class="stars" role="group" aria-label="Puntuación del plato">
    <button
      v-for="value in STARS"
      :key="value"
      class="star"
      type="button"
      :disabled="disabled"
      :aria-pressed="score >= value"
      :aria-label="`${value} de 5`"
      :title="score === value ? 'Quitar la puntuación' : `Puntuar con ${value}`"
      @click.stop="$emit('pick', value)"
    >
      <svg
        :width="size"
        :height="size"
        viewBox="0 0 12 12"
        :fill="score >= value ? 'currentColor' : 'none'"
        stroke="currentColor"
        stroke-width="1.1"
        stroke-linejoin="round"
        :class="{ on: score >= value }"
        aria-hidden="true"
      >
        <path d="M6 1.4l1.4 3 3.2.4-2.4 2.2.6 3.2L6 8.7l-2.8 1.5.6-3.2L1.4 4.8l3.2-.4z" />
      </svg>
    </button>
  </span>
</template>

<style scoped>
.stars {
  display: flex;
  gap: 1px;
}

.star {
  display: flex;
  padding: 3px;
  border-radius: 3px;
  color: #c2ccc6;
}

.star:hover:not(:disabled) {
  color: var(--sage-600);
}

.star:disabled {
  cursor: default;
}

svg.on {
  color: var(--sage-700);
}
</style>
