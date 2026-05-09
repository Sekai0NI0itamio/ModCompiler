import { Popover as Kobalte } from "@kobalte/core/popover"
import { showToast } from "@opencode-ai/ui/toast"
import { Component, ComponentProps, createMemo, createSignal, JSX, Show, ValidComponent } from "solid-js"
import { createStore } from "solid-js/store"
import { useGlobalSync } from "@/context/global-sync"
import { useLocal } from "@/context/local"
import { useModels } from "@/context/models"
import { useDialog } from "@opencode-ai/ui/context/dialog"
import { popularProviders } from "@/hooks/use-providers"
import { Button } from "@opencode-ai/ui/button"
import { IconButton } from "@opencode-ai/ui/icon-button"
import { Tag } from "@opencode-ai/ui/tag"
import { Dialog } from "@opencode-ai/ui/dialog"
import { List } from "@opencode-ai/ui/list"
import { Tooltip } from "@opencode-ai/ui/tooltip"
import {
  capabilityPercentColor,
  capabilityPercentTextColor,
  compareCapabilityScore,
  resolveCapabilityProviderBaseURL,
  runModelCapabilityTest,
} from "@/components/model-capability"
import { ModelTooltip } from "./model-tooltip"
import { useLanguage } from "@/context/language"

const isFree = (provider: string, cost: { input: number } | undefined) =>
  provider === "opencode" && (!cost || cost.input === 0)

type ModelState = ReturnType<typeof useLocal>["model"]
type ModelItem = ReturnType<ModelState["list"]>[number]

async function openCapabilityResults(dialog: ReturnType<typeof useDialog>, input: {
  modelName: string
  providerName: string
  record: NonNullable<ReturnType<ReturnType<typeof useModels>["tests"]["get"]>>
}) {
  const mod = await import("./dialog-model-capability-results")
  dialog.show(() => (
    <mod.DialogModelCapabilityResults
      modelName={input.modelName}
      providerName={input.providerName}
      record={input.record}
    />
  ))
}

const ModelList: Component<{
  provider?: string
  class?: string
  onSelect: () => void
  action?: JSX.Element
  model?: ModelState
}> = (props) => {
  const model = props.model ?? useLocal().model
  const modelsState = useModels()
  const globalSync = useGlobalSync()
  const dialog = useDialog()
  const language = useLanguage()
  const [pending, setPending] = createStore<Record<string, boolean>>({})
  const [activeProvider, setActiveProvider] = createSignal<string | undefined>(props.provider)

  const models = createMemo(() =>
    model
      .list()
      .filter((m) => model.visible({ modelID: m.id, providerID: m.provider.id }))
      .filter((m) => (activeProvider() ? m.provider.id === activeProvider() : true)),
  )

  const openAnalytics = async (item: ModelItem) => {
    const record = modelsState.tests.get({ modelID: item.id, providerID: item.provider.id })
    if (!record) return
    await openCapabilityResults(dialog, {
      modelName: item.name,
      providerName: item.provider.name,
      record,
    })
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

  const topAction = () => (
    <div class="flex items-center gap-1">
      <Show when={activeProvider()}>
        {(providerID) => (
          <button
            type="button"
            class="rounded border border-border-base px-2 py-1 text-11-medium text-text-base hover:bg-surface-raised"
            onClick={async () => {
              const batch = models().filter((item) => item.provider.id === providerID())
              for (const item of batch) {
                await testModel(item)
              }
            }}
            title={
              activeProvider()
                ? `Run a capability test for every visible ${activeProvider()} model`
                : "Run model capability tests"
            }
          >
            Full Test
          </button>
        )}
      </Show>
      {props.action}
    </div>
  )

  return (
    <List
      class={`flex-1 min-h-0 [&_[data-slot=list-scroll]]:flex-1 [&_[data-slot=list-scroll]]:min-h-0 ${props.class ?? ""}`}
      search={{ placeholder: language.t("dialog.model.search.placeholder"), autofocus: true, action: topAction() }}
      emptyMessage={language.t("dialog.model.empty")}
      key={(x) => `${x.provider.id}:${x.id}`}
      items={models}
      current={model.current()}
      filterKeys={["provider.name", "name", "id"]}
      sortBy={(a, b) =>
        compareCapabilityScore(
          { providerID: a.provider.id, modelID: a.id, name: a.name },
          { providerID: b.provider.id, modelID: b.id, name: b.name },
          modelsState.tests.get,
        )
      }
      groupBy={(x) => x.provider.name}
      groupHeader={(group) => {
        const provider = group.items[0]?.provider
        if (!provider) return group.category
        return (
          <div class="flex items-center justify-between gap-3">
            <span>{provider.name}</span>
            <button
              type="button"
              class="rounded border border-border-base px-2 py-1 text-11-medium text-text-base hover:bg-surface-raised"
              onClick={async (event) => {
                event.stopPropagation()
                setActiveProvider(provider.id)
                for (const item of group.items) {
                  await testModel(item)
                }
              }}
            >
              Full Test
            </button>
          </div>
        )
      }}
      sortGroupsBy={(a, b) => {
        const aProvider = a.items[0].provider.id
        const bProvider = b.items[0].provider.id
        if (popularProviders.includes(aProvider) && !popularProviders.includes(bProvider)) return -1
        if (!popularProviders.includes(aProvider) && popularProviders.includes(bProvider)) return 1
        return popularProviders.indexOf(aProvider) - popularProviders.indexOf(bProvider)
      }}
      itemWrapper={(item, node) => {
        const record = createMemo(() => modelsState.tests.get({ modelID: item.id, providerID: item.provider.id }))
        const key = `${item.provider.id}:${item.id}`
        const percent = () => record()?.percent

        return (
          <Tooltip
            class="w-full"
            placement="right-start"
            gutter={12}
            value={<ModelTooltip model={item} latest={item.latest} free={isFree(item.provider.id, item.cost)} />}
          >
            <div
              class="flex items-stretch gap-2 rounded-md border p-1"
              style={{ "border-color": capabilityPercentColor(percent()) }}
            >
              <div class="min-w-0 flex-1">{node}</div>
              <div class="flex shrink-0 flex-col items-end gap-1">
                <button
                  type="button"
                  class="rounded border border-border-base px-2 py-1 text-11-medium text-text-base hover:bg-surface-raised disabled:opacity-50"
                  disabled={!!pending[key]}
                  onClick={(event) => {
                    event.stopPropagation()
                    void testModel(item)
                  }}
                >
                  {pending[key] ? "Testing..." : record() ? "Retest" : "Test"}
                </button>
                <Show when={record()}>
                  <button
                    type="button"
                    class="text-11-medium"
                    style={{ color: capabilityPercentTextColor(percent()) }}
                    onClick={(event) => {
                      event.stopPropagation()
                      void openAnalytics(item)
                    }}
                  >
                    {percent()}%
                  </button>
                </Show>
              </div>
            </div>
          </Tooltip>
        )
      }}
      onFilter={(value) => {
        const normalized = value.trim().toLowerCase()
        if (!normalized) {
          setActiveProvider(props.provider)
          return
        }
        const matched = model
          .list()
          .find((item) => item.provider.name.toLowerCase().includes(normalized) || item.provider.id.toLowerCase().includes(normalized))
        setActiveProvider(matched?.provider.id ?? props.provider)
      }}
      onSelect={(x) => {
        model.set(x ? { modelID: x.id, providerID: x.provider.id } : undefined, {
          recent: true,
        })
        props.onSelect()
      }}
    >
      {(i) => {
        const percent = () => modelsState.tests.get({ modelID: i.id, providerID: i.provider.id })?.percent
        return (
          <div class="w-full min-w-0 flex items-center gap-x-2 text-13-regular">
            <span class="truncate">{i.name}</span>
            <Show when={isFree(i.provider.id, i.cost)}>
              <Tag>{language.t("model.tag.free")}</Tag>
            </Show>
            <Show when={i.latest}>
              <Tag>{language.t("model.tag.latest")}</Tag>
            </Show>
            <Show when={typeof percent() === "number"}>
              <span class="ml-auto text-11-medium" style={{ color: capabilityPercentTextColor(percent()) }}>
                {percent()}%
              </span>
            </Show>
          </div>
        )
      }}
    </List>
  )
}

type ModelSelectorTriggerProps = Omit<ComponentProps<typeof Kobalte.Trigger>, "as" | "ref">

export function ModelSelectorPopover(props: {
  provider?: string
  model?: ModelState
  children?: JSX.Element
  triggerAs?: ValidComponent
  triggerProps?: ModelSelectorTriggerProps
}) {
  const [store, setStore] = createStore<{
    open: boolean
    dismiss: "escape" | "outside" | null
  }>({
    open: false,
    dismiss: null,
  })
  const dialog = useDialog()

  const handleManage = () => {
    setStore("open", false)
    void import("./dialog-manage-models").then((x) => {
      dialog.show(() => <x.DialogManageModels />)
    })
  }

  const handleConnectProvider = () => {
    setStore("open", false)
    void import("./dialog-select-provider").then((x) => {
      dialog.show(() => <x.DialogSelectProvider />)
    })
  }
  const language = useLanguage()

  return (
    <Kobalte
      open={store.open}
      onOpenChange={(next) => {
        if (next) setStore("dismiss", null)
        setStore("open", next)
      }}
      modal={false}
      placement="top-start"
      gutter={4}
    >
      <Kobalte.Trigger as={props.triggerAs ?? "div"} {...props.triggerProps}>
        {props.children}
      </Kobalte.Trigger>
      <Kobalte.Portal>
        <Kobalte.Content
          class="w-[28rem] h-[26rem] flex flex-col p-2 rounded-md border border-border-base bg-surface-raised-stronger-non-alpha shadow-md z-50 outline-none overflow-hidden"
          onEscapeKeyDown={(event) => {
            setStore("dismiss", "escape")
            setStore("open", false)
            event.preventDefault()
            event.stopPropagation()
          }}
          onPointerDownOutside={() => {
            setStore("dismiss", "outside")
            setStore("open", false)
          }}
          onFocusOutside={() => {
            setStore("dismiss", "outside")
            setStore("open", false)
          }}
          onCloseAutoFocus={(event) => {
            if (store.dismiss === "outside") event.preventDefault()
            setStore("dismiss", null)
          }}
        >
          <Kobalte.Title class="sr-only">{language.t("dialog.model.select.title")}</Kobalte.Title>
          <ModelList
            provider={props.provider}
            model={props.model}
            onSelect={() => setStore("open", false)}
            class="p-1"
            action={
              <div class="flex items-center gap-1">
                <Tooltip placement="top" value={language.t("command.provider.connect")}>
                  <IconButton
                    icon="plus-small"
                    variant="ghost"
                    iconSize="normal"
                    class="size-6"
                    aria-label={language.t("command.provider.connect")}
                    onClick={handleConnectProvider}
                  />
                </Tooltip>
                <Tooltip placement="top" value={language.t("dialog.model.manage")}>
                  <IconButton
                    icon="sliders"
                    variant="ghost"
                    iconSize="normal"
                    class="size-6"
                    aria-label={language.t("dialog.model.manage")}
                    onClick={handleManage}
                  />
                </Tooltip>
              </div>
            }
          />
        </Kobalte.Content>
      </Kobalte.Portal>
    </Kobalte>
  )
}

export const DialogSelectModel: Component<{ provider?: string; model?: ModelState }> = (props) => {
  const dialog = useDialog()
  const language = useLanguage()

  const provider = () => {
    void import("./dialog-select-provider").then((x) => {
      dialog.show(() => <x.DialogSelectProvider />)
    })
  }

  const manage = () => {
    void import("./dialog-manage-models").then((x) => {
      dialog.show(() => <x.DialogManageModels />)
    })
  }

  return (
    <Dialog
      title={language.t("dialog.model.select.title")}
      action={
        <Button class="h-7 -my-1 text-14-medium" icon="plus-small" tabIndex={-1} onClick={provider}>
          {language.t("command.provider.connect")}
        </Button>
      }
    >
      <ModelList provider={props.provider} model={props.model} onSelect={() => dialog.close()} />
      <Button variant="ghost" class="ml-3 mt-5 mb-6 text-text-base self-start" onClick={manage}>
        {language.t("dialog.model.manage")}
      </Button>
    </Dialog>
  )
}
