/**
 * The one place a request is made. Vite proxies /api to the Spring backend, so
 * the browser stays on a single origin and no CORS setup is needed.
 */

const BASE = '/api'

/** A non-2xx answer, carrying whatever the backend said about it. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/** Spring's problem detail, when the backend bothered to write one. */
interface ProblemDetail {
  detail?: string
  message?: string
  error?: string
}

async function messageOf(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as ProblemDetail
    return body.detail ?? body.message ?? body.error ?? response.statusText
  } catch {
    return response.statusText
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // A multipart body sets its own Content-Type, boundary included.
  const json = init?.body && !(init.body instanceof FormData)
  const response = await fetch(BASE + path, {
    headers: { Accept: 'application/json', ...(json ? { 'Content-Type': 'application/json' } : {}) },
    ...init,
  })
  if (!response.ok) {
    throw new ApiError(response.status, await messageOf(response))
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

/** Drops the parameters left undefined rather than sending them empty. */
export function query(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) {
      search.set(key, String(value))
    }
  }
  const rendered = search.toString()
  return rendered ? `?${rendered}` : ''
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  /** A file upload: the form is sent as it is, never as JSON. */
  postForm: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', body: form }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  /** Answers 204, which `request` reads back as undefined rather than parsing. */
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
