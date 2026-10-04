<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ReferenceSource } from '@/api/types'
import { SOURCE_ATTRIBUTIONS } from '@/domain/compositionFood'

/**
 * Where the figures on screen come from. Not decoration: CIQUAL's and BLS's
 * licence (CC BY 4.0) requires their attribution wherever their values are
 * shown, and each reference source's figures are shown only with the line it
 * asks for.
 */

const props = defineProps<{ sources: ReferenceSource[] }>()

const open = ref(false)

const lines = computed(() =>
  props.sources.map((source) => ({
    code: source.code,
    text: source.attribution ?? `${source.title}${source.year ? ` (${source.year})` : ''}`,
    licence: source.licence,
    url: source.url,
  })),
)
</script>

<template>
  <footer class="footer">
    <span v-for="credit in SOURCE_ATTRIBUTIONS" :key="credit.source" class="line">
      {{ credit.text }}
      <a :href="credit.url" target="_blank" rel="noopener noreferrer">enlace</a>
    </span>
    <template v-if="lines.length > 0">
      <button class="more" type="button" :aria-expanded="open" @click="open = !open">
        {{ open ? 'Ocultar fuentes' : `Raciones y medidas: ${lines.length} ${lines.length === 1 ? 'fuente' : 'fuentes'}` }}
      </button>
      <ul v-if="open" class="sources">
        <li v-for="line in lines" :key="line.code">
          {{ line.text }}
          <span v-if="line.licence" class="licence">· {{ line.licence }}</span>
          <a v-if="line.url" :href="line.url" target="_blank" rel="noopener noreferrer">enlace</a>
        </li>
      </ul>
    </template>
  </footer>
</template>

<style scoped>
.footer {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 12px;
  padding: 6px 24px;
  border-top: 1px solid var(--line);
  background: var(--surface);
  font-size: 10.5px;
  line-height: 1.45;
  color: var(--ink-faint);
}

.more {
  font-size: 10.5px;
  color: var(--sage-700);
}

.sources {
  flex-basis: 100%;
  margin: 2px 0 0;
  padding: 0;
  list-style: none;
}

.licence {
  color: var(--ink-disabled);
}

.line a,
.sources a {
  margin-left: 4px;
  color: var(--sage-700);
}
</style>
