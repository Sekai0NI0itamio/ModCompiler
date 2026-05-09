import { Dialog } from "@opencode-ai/ui/dialog"
import { For, type Component } from "solid-js"
import type { ModelCapabilityTestRecord } from "@/components/model-capability"

export const DialogModelCapabilityResults: Component<{
  modelName: string
  providerName: string
  record: ModelCapabilityTestRecord
}> = (props) => {
  return (
    <Dialog title={`${props.modelName} capability`} description={`${props.providerName} model capability analytics`}>
      <div class="flex flex-col gap-4 px-1 pb-2">
        <div class="grid grid-cols-2 gap-3 text-13-regular">
          <div class="rounded-md border border-border-base px-3 py-2">
            <div class="text-text-weak">Score</div>
            <div class="text-16-medium text-text-strong">{props.record.percent}%</div>
          </div>
          <div class="rounded-md border border-border-base px-3 py-2">
            <div class="text-text-weak">Scenarios</div>
            <div class="text-16-medium text-text-strong">
              {props.record.success_count}/{props.record.scenario_count}
            </div>
          </div>
        </div>

        <div class="text-12-regular text-text-weak">Last tested: {props.record.tested_at}</div>

        <div class="flex flex-col gap-2 max-h-[52vh] overflow-y-auto pr-1">
          <For each={props.record.scenarios}>
            {(scenario) => (
              <div class="rounded-md border border-border-base px-3 py-2">
                <div class="flex items-center justify-between gap-3">
                  <div class="text-13-medium text-text-strong">{scenario.label}</div>
                  <div class={scenario.success ? "text-12-medium text-green-600" : "text-12-medium text-red-600"}>
                    {scenario.score}%
                  </div>
                </div>
                <div class="mt-1 text-12-regular text-text-base">{scenario.summary}</div>
                <div class="mt-2 text-11-regular text-text-weak">{scenario.duration_ms ?? 0} ms</div>
              </div>
            )}
          </For>
        </div>

        {props.record.error ? <div class="text-12-regular text-red-600">{props.record.error}</div> : null}
      </div>
    </Dialog>
  )
}
