<script setup lang="ts">
import { computed } from 'vue'
import { grams, NO_VALUE } from '@/domain/format'

/**
 * Protein, carbohydrate and fat as a share of the energy they carry between
 * them — 4 kcal a gram for the first two, 9 for fat, which is why a small
 * weight of fat draws a wide bar and should.
 *
 * **The bars are shares of what is known, not of the plate.** A component the
 * catalogue never published reads as a dash and takes no width, so three
 * bars over two known figures still fill the row; the caller says how much of
 * the dish was counted, and this says how what was counted was made up.
 */

const props = withDefaults(
  defineProps<{
    proteinG: number | null
    carbohydratesG: number | null
    fatG: number | null
    /** The wide form used in the day rail; the compact one is the hover card. */
    compact?: boolean
  }>(),
  { compact: false },
)

const KCAL_PER_G = { protein: 4, carbohydrate: 4, fat: 9 }

const bars = computed(() => {
  const kcal = {
    protein: (props.proteinG ?? 0) * KCAL_PER_G.protein,
    carbohydrate: (props.carbohydratesG ?? 0) * KCAL_PER_G.carbohydrate,
    fat: (props.fatG ?? 0) * KCAL_PER_G.fat,
  }
  const total = kcal.protein + kcal.carbohydrate + kcal.fat
  const share = (value: number) => (total > 0 ? `${Math.round((value / total) * 100)}%` : '0%')
  return [
    {
      key: 'protein',
      name: 'Proteínas',
      abbr: 'Prot',
      value: props.proteinG,
      width: share(kcal.protein),
      color: 'var(--macro-protein)',
    },
    {
      key: 'carbohydrate',
      name: 'H. de carbono',
      abbr: 'HC',
      value: props.carbohydratesG,
      width: share(kcal.carbohydrate),
      color: 'var(--macro-carbs)',
    },
    {
      key: 'fat',
      name: 'Grasas',
      abbr: 'Grasa',
      value: props.fatG,
      width: share(kcal.fat),
      color: 'var(--macro-fat)',
    },
  ]
})

function label(value: number | null): string {
  return value === null ? NO_VALUE : `${grams(value)} g`
}
</script>

<template>
  <div class="macros" :class="{ compact }">
    <div v-for="bar in bars" :key="bar.key" class="macro">
      <span class="name">{{ bar.name }}</span>
      <span class="track">
        <span class="fill" :style="{ width: bar.width, background: bar.color }" />
      </span>
      <span class="num value">{{ label(bar.value) }}</span>
    </div>
  </div>
</template>

<style scoped>
.macros {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.macros.compact {
  gap: 7px;
}

.macro {
  display: flex;
  align-items: center;
  gap: 9px;
}

.name {
  width: 92px;
  flex: none;
  font-size: 11.5px;
  color: var(--ink);
}

.compact .name {
  width: 88px;
  font-size: 11px;
}

.track {
  flex: 1 1 0;
  height: 6px;
  border-radius: 3px;
  background: var(--line-soft);
  overflow: hidden;
}

.compact .track {
  height: 5px;
}

.fill {
  display: block;
  height: 100%;
}

.value {
  width: 46px;
  flex: none;
  text-align: right;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ink-strong);
}

.compact .value {
  width: 42px;
  font-size: 11px;
}
</style>
