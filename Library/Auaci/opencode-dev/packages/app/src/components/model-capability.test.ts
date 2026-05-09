import { describe, expect, test } from "bun:test"
import {
  buildCapabilityTestURL,
  compareCapabilityScore,
  normalizeCapabilityBaseURL,
  type ModelCapabilityTestRecord,
} from "./model-capability"

describe("model capability helpers", () => {
  test("normalizes legacy C05 chat endpoints to the capability root", () => {
    expect(buildCapabilityTestURL("http://localhost:8129/chat")).toBe("http://localhost:8129/model-tests/run")
    expect(buildCapabilityTestURL("http://localhost:8129/v1/chat/completions")).toBe(
      "http://localhost:8129/model-tests/run",
    )
  })

  test("sorts tested models ahead of untested ones and prefers higher scores", () => {
    const records = new Map<string, ModelCapabilityTestRecord>([
      [
        "provider:strong",
        {
          providerID: "provider",
          modelID: "strong",
          request_type: "capability_test",
          tested_at: "2026-04-26T00:00:00Z",
          percent: 90,
          success_count: 9,
          scenario_count: 10,
          scenarios: [],
        },
      ],
      [
        "provider:weak",
        {
          providerID: "provider",
          modelID: "weak",
          request_type: "capability_test",
          tested_at: "2026-04-26T00:00:00Z",
          percent: 20,
          success_count: 2,
          scenario_count: 10,
          scenarios: [],
        },
      ],
    ])

    const getRecord = (input: { providerID: string; modelID: string }) => records.get(`${input.providerID}:${input.modelID}`)

    expect(
      compareCapabilityScore(
        { providerID: "provider", modelID: "strong", name: "Strong" },
        { providerID: "provider", modelID: "weak", name: "Weak" },
        getRecord,
      ),
    ).toBeLessThan(0)

    expect(
      compareCapabilityScore(
        { providerID: "provider", modelID: "untested", name: "Untested" },
        { providerID: "provider", modelID: "weak", name: "Weak" },
        getRecord,
      ),
    ).toBeGreaterThan(0)
  })

  test("trims trailing slashes while normalizing", () => {
    expect(normalizeCapabilityBaseURL("http://localhost:8129/chat/")).toBe("http://localhost:8129")
  })
})
