import type { Config } from "@opencode-ai/sdk/v2/client"

export type ModelCapabilityScenarioResult = {
  id: string
  label: string
  success: boolean
  score: number
  duration_ms?: number
  summary?: string
  details?: Record<string, unknown>
}

export type ModelCapabilityTestRecord = {
  providerID: string
  modelID: string
  request_type: "capability_test"
  tested_at: string
  percent: number
  success_count: number
  scenario_count: number
  scenarios: ModelCapabilityScenarioResult[]
  analytics?: Record<string, unknown>
  error?: string
}

type CapabilityResponse = {
  provider_id: string
  model: string
  request_type: "capability_test"
  tested_at: string
  success_count: number
  scenario_count: number
  percent: number
  scenarios: ModelCapabilityScenarioResult[]
  analytics?: Record<string, unknown>
  error?: string
}

export function modelCapabilityKey(input: { providerID: string; modelID: string }) {
  return `${input.providerID}:${input.modelID}`
}

export function resolveCapabilityProviderBaseURL(config: Config | undefined, providerID: string) {
  const provider = config?.provider?.[providerID]
  const value = provider?.options?.baseURL
  return typeof value === "string" && value.trim() ? value.trim() : undefined
}

export function normalizeCapabilityBaseURL(baseURL: string) {
  const value = baseURL.trim().replace(/\/+$/, "")
  return value
    .replace(/\/v1\/chat\/completions$/i, "")
    .replace(/\/chat\/completions$/i, "")
    .replace(/\/chat$/i, "")
}

export function buildCapabilityTestURL(baseURL: string) {
  return `${normalizeCapabilityBaseURL(baseURL)}/model-tests/run`
}

export function capabilityPercentColor(percent: number | undefined) {
  if (typeof percent !== "number" || !Number.isFinite(percent)) return "rgba(148, 163, 184, 0.35)"
  const clamped = Math.max(0, Math.min(100, percent))
  const hue = (clamped / 100) * 120
  return `hsla(${hue}, 70%, 48%, 0.45)`
}

export function capabilityPercentTextColor(percent: number | undefined) {
  if (typeof percent !== "number" || !Number.isFinite(percent)) return "rgb(148, 163, 184)"
  const clamped = Math.max(0, Math.min(100, percent))
  const hue = (clamped / 100) * 120
  return `hsl(${hue}, 72%, 42%)`
}

export function compareCapabilityScore(
  a: { providerID: string; modelID: string; name: string },
  b: { providerID: string; modelID: string; name: string },
  getRecord: (input: { providerID: string; modelID: string }) => ModelCapabilityTestRecord | undefined,
) {
  const aRecord = getRecord(a)
  const bRecord = getRecord(b)
  const aScore = aRecord?.percent
  const bScore = bRecord?.percent

  if (typeof aScore === "number" && typeof bScore === "number" && aScore !== bScore) {
    return bScore - aScore
  }
  if (typeof aScore === "number" && typeof bScore !== "number") return -1
  if (typeof aScore !== "number" && typeof bScore === "number") return 1
  return a.name.localeCompare(b.name)
}

export async function runModelCapabilityTest(input: {
  baseURL: string
  providerID: string
  modelID: string
  timeout_ms?: number
}) {
  const response = await fetch(buildCapabilityTestURL(input.baseURL), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      hoster: input.providerID,
      provider_id: input.providerID,
      model: input.modelID,
      request_type: "capability_test",
      timeout_ms: input.timeout_ms ?? 45000,
    }),
  })

  let payload: CapabilityResponse | { detail?: string } | undefined
  try {
    payload = await response.json()
  } catch {
    payload = undefined
  }

  if (!response.ok) {
    const detail =
      (payload && typeof payload === "object" && "detail" in payload && typeof payload.detail === "string"
        ? payload.detail
        : undefined) ?? `Capability test failed with HTTP ${response.status}`
    throw new Error(detail)
  }

  const result = payload as CapabilityResponse
  return {
    providerID: result.provider_id,
    modelID: result.model,
    request_type: result.request_type,
    tested_at: result.tested_at,
    percent: result.percent,
    success_count: result.success_count,
    scenario_count: result.scenario_count,
    scenarios: result.scenarios ?? [],
    analytics: result.analytics,
    error: result.error,
  } satisfies ModelCapabilityTestRecord
}
