import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import DietBuilderView from '@/views/DietBuilderView.vue'
import PatientView from '@/views/PatientView.vue'

/**
 * Two screens over the same week, and the roles are not symmetrical.
 *
 * `/dieta` is where the nutritionist writes it and publishes it. `/mi-dieta` is
 * what the patient reads — the plan is read-only there, and the only rows a
 * patient writes are their own: what they thought of a plate, and what they ate
 * beside it.
 *
 * The app opens on the patient's view because that is the one read daily; the
 * builder is a link away in the header. There is nothing enforcing the split:
 * the backend now records *whose* a diet is, but it still has no security
 * layer, so both screens carry the same patient selector and either can be
 * pointed at anybody. This is a division of screens, not of permissions, and
 * `/mi-dieta` will need its patient fixed to the person logged in once there is
 * somebody logged in.
 */
const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/mi-dieta' },
  { path: '/mi-dieta', name: 'patient-week', component: PatientView },
  { path: '/dieta', name: 'diet-builder', component: DietBuilderView },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
