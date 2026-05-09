import { showToast } from "@opencode-ai/ui/toast"
import { Dialog } from "@opencode-ai/ui/dialog"
import { List } from "@opencode-ai/ui/list"
import { Switch } from "@opencode-ai/ui/switch"
import { Tooltip } from "@opencode-ai/ui/tooltip"
import { Button } from "@opencode-ai/ui/button"
import type { Component } from "solid-js"
import { createMemo } from "solid-js"
import { createStore } from "solid-js/store"
import { useGlobalSync } from "@/context/global-sync"
import { useLocal } from "@/context/local"
import { useModels } from "@/context/models"
import { popularProviders } from "@/hooks/use-providers"
import { useLanguage } from "@/context/language"
import { useDialog } from "@opencode-ai/ui/context/dialog"
import {
  capabilityPercentColor,
  capabilityPercentTextColor,
  compareCapabilityScore,
  resolveCapabilityProviderBaseURL,
  runModelCapabilityTest,
} from "@/components/model-capability"
import { DialogSelectProvider } from "./dialog-select-provider"

type ModelItem = ReturnType<ReturnType<typeof useLocal>["model"]["list"]>[number]

export const DialogManageModels: Component = () => {
  const local = useLocal()
  const modelsState = useModels()
  const globalSync = useGlobalSync()
  const language = useLanguage()
  const dialog = useDialog()
  const [pending, setPending] = createStore<Record<string, boolean>>({})

  const handleConnectProvider = () => {
    dialog.show(() => <DialogSelectProvider />)
  }
  const providerRank = (id: string) => popularProviders.indexOf(id)
  const providerList = (providerID: string) => local.model.list().filter((x) => x.provider.id === providerID)
  const providerVisible = (providerID: string) =>
    providerList(providerID).every((x) => local.model.visible({ modelID: x.id, providerID: x.provider.id }))
  const setProviderVisibility = (providerID: string, checked: boolean) => {
    providerList(providerID).forEach((x) => {
      local.model.setVisibility({ modelID: x.id, providerID: x.provider.id }, checked)
    })
  }

  const openAnalytics = async (item: ModelItem) => {
    const record = modelsState.tests.get({ providerID: item.provider.id, modelID: item.id })
    if (!record) return
    const mod = await import("./dialog-model-capability-results")
    dialog.show(() => (
      <mod.DialogModelCapabilityResults modelName={item.name} providerName={item.provider.name} record={record} />
    ))
  }

  const testModel = async (item: ModelItem) => {
    const key = `${item.provider.id}:${item.id}`
    const baseURL = resolveCapabilityProviderBaseURL(globalSync.data.config, item.provider.id)
    if (!baseURL) {
      showToast({
        variant: "error",
        title: "Capability test unavailable",
        description: "This model is not configured through a custom C05LocalAi provider.",
      })
      return
    }

    setPending(key, true)
    try {
      const record = await runModelCapabilityTest({
        baseURL,
        providerID: item.provider.id,
        modelID: item.id,
      })
      modelsState.tests.set({ providerID: item.provider.id, modelID: item.id }, record)
      showToast({
        variant: "success",
        icon: "circle-check",
        title: "Model test complete",
        description: `${item.name} scored ${record.percent}%`,
      })
    } catch (error) {
      showToast({
        variant: "error",
        title: "Model test failed",
        description: error instanceof Error ? error.message : String(error),
      })
    } finally {
      setPending(key, false)
    }
  }

  const items = createMemo(() => local.model.list())

  return (
    <Dialog
      title={language.t("dialog.model.manage")}
      description={language.t("dialog.model.manage.description")}
      action={
        <Button class="h-7 -my-1 text-14-medium" icon="plus-small" tabIndex={-1} onClick={handleConnectProvider}>
          {language.t("command.provider.connect")}
        </Button>
      }
    >
      <List
        search={{ placeholder: language.t("dialog.model.search.placeholder"), autofocus: true }}
        emptyMessage={language.t("dialog.model.empty")}
        key={(x) => `${x?.provider?.id}:${x?.id}`}
        items={items}
        filterKeys={["provider.name", "name", "id"]}
        sortBy={(a, b) =>
          compareCapabilityScore(
            { providerID: a.provider.id, modelID: a.id, name: a.name },
            { providerID: b.provider.id, modelID: b.id, name: b.name },
            modelsState.tests.get,
          )
        }
        groupBy={(x) => x.provider.id}
        groupHeader={(group) => {
          const provider = group.items[0].provider
          return (
            <div class="flex items-center justify-between gap-3">
              <span>{provider.name}</span>
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  class="rounded border border-border-base px-2 py-1 text-11-medium text-text-base hover:bg-surface-raised"
                  onClick={async (event) => {
                    event.stopPropagation()
                    for (const item of group.items) {
                      await testModel(item)
                    }
                  }}
                >
                  Full Test
                </button>
                <Tooltip
                  placement="top"
                  value={language.t("dialog.model.manage.provider.toggle", { provider: provider.name })}
                >
                  <Switch
                    class="-mr-1"
                    checked={providerVisible(provider.id)}
                    onChange={(checked) => setProviderVisibility(provider.id, checked)}
                    hideLabel
                  >
                    {provider.name}
                  </Switch>
                </Tooltip>
              </div>
            </div>
          )
        }}
        sortGroupsBy={(a, b) => {
          const aRank = providerRank(a.items[0].provider.id)
          const bRank = providerRank(b.items[0].provider.id)
          const aPopular = aRank >= 0
          const bPopular = bRank >= 0
          if (aPopular && !bPopular) return -1
          if (!aPopular && bPopular) return 1
          return aRank - bRank
        }}
        itemWrapper={(item, node) => {
          const score = () => modelsState.tests.get({ providerID: item.provider.id, modelID: item.id })?.percent
          const key = `${item.provider.id}:${item.id}`
          return (
            <div class="flex items-stretch gap-2 rounded-md border p-1" style={{ "border-color": capabilityPercentColor(score()) }}>
              <div class="min-w-0 flex-1">{node}</div>
              <div class="flex shrink-0 items-center gap-2" onClick={(e) => e.stopPropagation()}>
                {typeof score() === "number" ? (
                  <button
                    type="button"
                    class="text-11-medium"
                    style={{ color: capabilityPercentTextColor(score()) }}
                    onClick={() => void openAnalytics(item)}
                  >
                    {score()}%
                  </button>
                ) : null}
                <button
                  type="button"
                  class="rounded border border-border-base px-2 py-1 text-11-medium text-text-base hover:bg-surface-raised disabled:opacity-50"
                  disabled={!!pending[key]}
                  onClick={() => void testModel(item)}
                >
                  {pending[key] ? "Testing..." : score() !== undefined ? "Retest" : "Test"}
                </button>
                <Switch
                  checked={!!local.model.visible({ modelID: item.id, providerID: item.provider.id })}
                  onChange={(checked) => {
                    local.model.setVisibility({ modelID: item.id, providerID: item.provider.id }, checked)
                  }}
                />
              </div>
            </div>
          )
        }}
        onSelect={(x) => {
          if (!x) return
          const key = { modelID: x.id, providerID: x.provider.id }
          local.model.setVisibility(key, !local.model.visible(key))
        }}
      >
        {(i) => (
          <div class="w-full flex min-w-0 items-center justify-between gap-x-3">
            <span class="truncate">{i.name}</span>
            <span class="shrink-0 text-11-regular text-text-weak">{i.provider.name}</span>
          </div>
        )}
      </List>
    </Dialog>
  )
}
