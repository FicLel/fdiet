import type { DayOfWeek } from '@/api/types'

/** Monday first, the order a diet is written and read in. */
export const WEEK: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
]

const NAMES: Record<DayOfWeek, string> = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
}

const MONTHS = [
  'ene',
  'feb',
  'mar',
  'abr',
  'may',
  'jun',
  'jul',
  'ago',
  'sep',
  'oct',
  'nov',
  'dic',
]

export function dayName(day: DayOfWeek): string {
  return NAMES[day]
}

/** `2026-08-24` as a local date; `new Date(string)` would read it as UTC. */
export function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** The Monday of the week `startedOn` falls in. A week is read Monday first. */
export function mondayOf(startedOn: string): Date {
  const date = parseIsoDate(startedOn)
  const offset = (date.getDay() + 6) % 7
  date.setDate(date.getDate() - offset)
  return date
}

export function addDays(from: Date, days: number): Date {
  const date = new Date(from)
  date.setDate(date.getDate() + days)
  return date
}

/** The day number alone — the grid header has no room for more. */
export function dayNumber(date: Date): string {
  return String(date.getDate())
}

export function longDate(date: Date): string {
  return `${date.getDate()} de ${fullMonth(date)}`
}

/** `24 – 30 ago`, or `29 ago – 4 sep` when the week straddles two months. */
export function weekLabel(monday: Date): string {
  const sunday = addDays(monday, 6)
  const from = monday.getMonth() === sunday.getMonth()
    ? String(monday.getDate())
    : `${monday.getDate()} ${MONTHS[monday.getMonth()]}`
  return `${from} \u2013 ${sunday.getDate()} ${MONTHS[sunday.getMonth()]}`
}

const FULL_MONTHS = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
]

function fullMonth(date: Date): string {
  return FULL_MONTHS[date.getMonth()]
}

const SHORT_NAMES: Record<DayOfWeek, string> = {
  MONDAY: 'Lun',
  TUESDAY: 'Mar',
  WEDNESDAY: 'Mié',
  THURSDAY: 'Jue',
  FRIDAY: 'Vie',
  SATURDAY: 'Sáb',
  SUNDAY: 'Dom',
}

/** `Lun` — the tablet header and the mobile day strip have no room for more. */
export function shortDayName(day: DayOfWeek): string {
  return SHORT_NAMES[day]
}
