import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * Which of the three layouts the screen is wide enough for.
 *
 * **The breakpoint is a rule about the content, not a device.** The dense week
 * is seven columns of a sentence each; below 834 px a column is too narrow for
 * a line of a diet to be read at all, so the week grid does not exist there and
 * there is no Semana/Día switch to offer — the day view is the only view. From
 * 834 px the grid comes back, and from 1240 px there is also room beside it for
 * the day's own summary, which the tablet lays across the top instead.
 */

/** Below this the week grid cannot be read, so it is not offered. */
const TABLET = 834

/** Below this the grid and a 312 px rail do not fit side by side. */
const DESKTOP = 1240

const width = ref(typeof window === 'undefined' ? DESKTOP : window.innerWidth)

let listeners = 0

function onResize(): void {
  width.value = window.innerWidth
}

export function useViewport() {
  onMounted(() => {
    if (listeners === 0) {
      window.addEventListener('resize', onResize, { passive: true })
    }
    listeners++
    onResize()
  })

  onBeforeUnmount(() => {
    listeners--
    if (listeners === 0) {
      window.removeEventListener('resize', onResize)
    }
  })

  const mobile = computed(() => width.value < TABLET)
  const tablet = computed(() => width.value >= TABLET && width.value < DESKTOP)
  const desktop = computed(() => width.value >= DESKTOP)

  return { width, mobile, tablet, desktop }
}
