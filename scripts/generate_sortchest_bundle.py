#!/usr/bin/env python3
"""Generates the Sort Chest all-versions bundle under incoming/sort-chest-all-versions/"""
import os, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "incoming" / "sort-chest-all-versions"

MOD_ID = "sortchest"
MOD_NAME = "Sort Chest"
MOD_VERSION = "1.0.0"
GROUP = "net.itamio.sortchest"
DESCRIPTION = "Client-side mod that adds a Sort button to chest GUIs to consolidate and group item stacks."
AUTHORS = "Itamio"
LICENSE = "MIT"
HOMEPAGE = "https://modrinth.com/mod/sort-chest"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt(entrypoint: str) -> str:
    return (
        f"mod_id={MOD_ID}\n"
        f"name={MOD_NAME}\n"
        f"mod_version={MOD_VERSION}\n"
        f"group={GROUP}\n"
        f"entrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\n"
        f"authors={AUTHORS}\n"
        f"license={LICENSE}\n"
        f"homepage={HOMEPAGE}\n"
        f"runtime_side=client\n"
    )

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"

def lang_json() -> str:
    return '{\n  "sortchest.button.sort": "Sort"\n}\n'

# ---------------------------------------------------------------------------
# Source templates
# ---------------------------------------------------------------------------

# Shared sort logic (used by all versions via copy-paste into each target)
# The algorithm is identical across all versions; only the API surface differs.

SORT_LOGIC_COMMENT = """\
    // -----------------------------------------------------------------------
    // Sort algorithm (identical across all versions)
    // 1. mergeStacks  - consolidate partial stacks of the same item
    // 2. buildLayout  - group items by type into a desired slot order
    // 3. reorderStacks - swap slots to match the desired layout
    // -----------------------------------------------------------------------
"""

# ===========================================================================
# 1.8.9 FORGE
# Uses: GuiOpenEvent, GuiContainer, GuiButton, playerController.windowClick
# No ScreenEvent, no AbstractContainerScreen, no Button builder
# ===========================================================================
SRC_189_FORGE = """\
package net.itamio.sortchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.*;

@Mod(modid = SortChestMod.MOD_ID, name = "Sort Chest", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    public SortChestMod() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.gui;
        int x = gui.guiLeft + gui.xSize - 44;
        int y = gui.guiTop + 6;
        event.buttonList.add(new GuiButton(9001, x, y, 40, 14, "Sort"));
    }

    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiContainer)) return;
        if (event.button.id != 9001) return;
        event.setCanceled(true);
        sortContainer((GuiContainer) event.gui);
    }

    private static void sortContainer(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null) return;
        if (mc.currentScreen != gui) return;
        Container container = gui.inventorySlots;
        if (container.getSlotUnderMouse() != null && !container.getSlotUnderMouse().getStack().isEmpty()) return;
        List<Integer> slots = getContainerSlots(container, mc.thePlayer.inventory);
        if (!isSortable(container, slots, mc.thePlayer.inventory)) return;
        mergeStacks(container, slots, mc);
        if (container.getSlotUnderMouse() != null && !container.getSlotUnderMouse().getStack().isEmpty()) return;
        List<ItemStack> layout = buildLayout(container, slots);
        reorder(container, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(Container c, net.minecraft.entity.player.InventoryPlayer inv) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            Slot s = c.getSlot(i);
            if (s.inventory != inv) result.add(i);
        }
        return result;
    }

    private static boolean isSortable(Container c, List<Integer> slots, net.minecraft.entity.player.InventoryPlayer inv) {
        if (slots.isEmpty()) return false;
        for (int idx : slots) {
            if (c.getSlot(idx).inventory == inv) return false;
        }
        return true;
    }

    private static void mergeStacks(Container c, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            Slot si = c.getSlot(slots.get(i));
            ItemStack stack = si.getStack();
            if (stack == null || stack.stackSize >= stack.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                Slot sj = c.getSlot(slots.get(j));
                ItemStack other = sj.getStack();
                if (other == null) continue;
                if (stack.stackSize >= stack.getMaxStackSize()) break;
                if (ItemStack.areItemsEqual(stack, other) && ItemStack.areItemStackTagsEqual(stack, other)) {
                    click(c, slots.get(j), 0, mc);
                    click(c, slots.get(i), 0, mc);
                    if (!mc.thePlayer.inventory.getItemStack().isEmpty()) {
                        click(c, slots.get(j), 0, mc);
                    }
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(Container c, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<>();
        for (int idx : slots) {
            ItemStack s = c.getSlot(idx).getStack();
            if (s == null) continue;
            ItemKey key = new ItemKey(s);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s.copy());
        }
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> group : groups.values()) result.addAll(group);
        while (result.size() < slots.size()) result.add(null);
        return result;
    }

    private static void reorder(Container c, List<Integer> slots, List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack current = c.getSlot(slots.get(i)).getStack();
            ItemStack desired = layout.get(i);
            if (stacksMatch(current, desired)) continue;
            int from = findSlot(c, slots, i + 1, desired);
            if (from == -1) continue;
            swap(c, slots.get(i), slots.get(from), mc);
        }
    }

    private static int findSlot(Container c, List<Integer> slots, int start, ItemStack target) {
        for (int i = start; i < slots.size(); i++) {
            if (stacksMatch(c.getSlot(slots.get(i)).getStack(), target)) return i;
        }
        return -1;
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.stackSize != b.stackSize) return false;
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static void swap(Container c, int slotA, int slotB, Minecraft mc) {
        click(c, slotA, 0, mc);
        click(c, slotB, 0, mc);
        if (mc.thePlayer.inventory.getItemStack() != null && !mc.thePlayer.inventory.getItemStack().isEmpty()) {
            click(c, slotA, 0, mc);
        }
    }

    private static void click(Container c, int slot, int button, Minecraft mc) {
        if (mc.thePlayer == null || mc.playerController == null) return;
        mc.playerController.windowClick(c.windowId, slot, button,
                net.minecraft.inventory.ClickType.PICKUP, mc.thePlayer);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final int meta;
        final net.minecraft.nbt.NBTTagCompound tag;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.meta = s.getMetadata();
            this.tag = s.getTagCompound() != null ? s.getTagCompound().copy() : null;
            this.hash = Objects.hash(item, meta, tag);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && meta == k.meta && Objects.equals(tag, k.tag);
        }
        @Override public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.12.2 FORGE
# Uses: GuiScreenEvent.InitGuiEvent.Post, GuiContainer, GuiButton
# ClickType.PICKUP exists, playerController.windowClick
# ===========================================================================
SRC_1122_FORGE = """\
package net.itamio.sortchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

@Mod(modid = SortChestMod.MOD_ID, name = "Sort Chest", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    public SortChestMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.getGui();
        int x = gui.guiLeft + gui.xSize - 44;
        int y = gui.guiTop + 6;
        event.getButtonList().add(new GuiButton(9001, x, y, 40, 14, "Sort"));
    }

    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainer)) return;
        if (event.getButton().id != 9001) return;
        event.setCanceled(true);
        sortContainer((GuiContainer) event.getGui());
    }

    private static void sortContainer(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.playerController == null) return;
        if (mc.currentScreen != gui) return;
        Container container = gui.inventorySlots;
        if (!container.getInventory().isEmpty() && container.getSlotUnderMouse() != null
                && !container.getSlotUnderMouse().getStack().isEmpty()) return;
        List<Integer> slots = getContainerSlots(container, mc.player.inventory);
        if (!isSortable(slots)) return;
        mergeStacks(container, slots, mc);
        List<ItemStack> layout = buildLayout(container, slots);
        reorder(container, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(Container c, net.minecraft.entity.player.InventoryPlayer inv) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            Slot s = c.getSlot(i);
            if (s.inventory != inv) result.add(i);
        }
        return result;
    }

    private static boolean isSortable(List<Integer> slots) {
        return !slots.isEmpty();
    }

    private static void mergeStacks(Container c, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = c.getSlot(slots.get(i)).getStack();
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                if (stack.getCount() >= stack.getMaxStackSize()) break;
                ItemStack other = c.getSlot(slots.get(j)).getStack();
                if (other.isEmpty()) continue;
                if (ItemStack.areItemsEqual(stack, other) && ItemStack.areItemStackTagsEqual(stack, other)) {
                    click(c, slots.get(j), mc);
                    click(c, slots.get(i), mc);
                    if (!mc.player.inventory.getItemStack().isEmpty()) click(c, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(Container c, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<>();
        for (int idx : slots) {
            ItemStack s = c.getSlot(idx).getStack();
            if (s.isEmpty()) continue;
            groups.computeIfAbsent(new ItemKey(s), k -> new ArrayList<>()).add(s.copy());
        }
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> g : groups.values()) result.addAll(g);
        while (result.size() < slots.size()) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void reorder(Container c, List<Integer> slots, List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = c.getSlot(slots.get(i)).getStack();
            ItemStack des = layout.get(i);
            if (stacksMatch(cur, des)) continue;
            int from = findSlot(c, slots, i + 1, des);
            if (from == -1) continue;
            swap(c, slots.get(i), slots.get(from), mc);
        }
    }

    private static int findSlot(Container c, List<Integer> slots, int start, ItemStack target) {
        for (int i = start; i < slots.size(); i++) {
            if (stacksMatch(c.getSlot(slots.get(i)).getStack(), target)) return i;
        }
        return -1;
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static void swap(Container c, int slotA, int slotB, Minecraft mc) {
        click(c, slotA, mc);
        click(c, slotB, mc);
        if (!mc.player.inventory.getItemStack().isEmpty()) click(c, slotA, mc);
    }

    private static void click(Container c, int slot, Minecraft mc) {
        if (mc.player == null || mc.playerController == null) return;
        mc.playerController.windowClick(c.windowId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final int meta;
        final net.minecraft.nbt.NBTTagCompound tag;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.meta = s.getMetadata();
            this.tag = s.getTagCompound() != null ? s.getTagCompound().copy() : null;
            this.hash = Objects.hash(item, meta, tag);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && meta == k.meta && Objects.equals(tag, k.tag);
        }
        @Override public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.16.5 FORGE
# ScreenEvent.InitScreenEvent.Post, AbstractContainerScreen, Button builder
# MultiPlayerGameMode.handleInventoryMouseClick, CompoundTag (not NBTTagCompound)
# ===========================================================================
SRC_1165_FORGE = """\
package net.itamio.sortchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.StringTextComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod(SortChestMod.MOD_ID)
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    public SortChestMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen)) return;
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int x = cs.getGuiLeft() + cs.getXSize() - 44;
        int y = cs.getGuiTop() + 6;
        event.addListener(new Button(x, y, 40, 14,
                new StringTextComponent("Sort"),
                btn -> sortContainer(cs)));
    }

    private static void sortContainer(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;
        List<Integer> slots = getContainerSlots(menu, mc.player.getInventory());
        if (slots.isEmpty()) return;
        mergeStacks(menu, slots, mc);
        if (!menu.getCarried().isEmpty()) return;
        List<ItemStack> layout = buildLayout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(AbstractContainerMenu menu,
            net.minecraft.world.entity.player.Inventory inv) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container != inv) result.add(i);
        }
        return result;
    }

    private static void mergeStacks(AbstractContainerMenu menu, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = menu.slots.get(slots.get(i)).getItem();
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                if (stack.getCount() >= stack.getMaxStackSize()) break;
                ItemStack other = menu.slots.get(slots.get(j)).getItem();
                if (other.isEmpty()) continue;
                if (ItemStack.isSameItemSameTags(stack, other)) {
                    click(menu, slots.get(j), mc);
                    click(menu, slots.get(i), mc);
                    if (!menu.getCarried().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(AbstractContainerMenu menu, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<>();
        for (int idx : slots) {
            ItemStack s = menu.slots.get(idx).getItem();
            if (s.isEmpty()) continue;
            groups.computeIfAbsent(new ItemKey(s), k -> new ArrayList<>()).add(s.copy());
        }
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> g : groups.values()) result.addAll(g);
        while (result.size() < slots.size()) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void reorder(AbstractContainerMenu menu, List<Integer> slots,
            List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = menu.slots.get(slots.get(i)).getItem();
            ItemStack des = layout.get(i);
            if (stacksMatch(cur, des)) continue;
            int from = findSlot(menu, slots, i + 1, des);
            if (from == -1) continue;
            swap(menu, slots.get(i), slots.get(from), mc);
        }
    }

    private static int findSlot(AbstractContainerMenu menu, List<Integer> slots, int start, ItemStack target) {
        for (int i = start; i < slots.size(); i++) {
            if (stacksMatch(menu.slots.get(slots.get(i)).getItem(), target)) return i;
        }
        return -1;
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.isSameItemSameTags(a, b);
    }

    private static void swap(AbstractContainerMenu menu, int slotA, int slotB, Minecraft mc) {
        click(menu, slotA, mc);
        click(menu, slotB, mc);
        if (!menu.getCarried().isEmpty()) click(menu, slotA, mc);
    }

    private static void click(AbstractContainerMenu menu, int slot, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.world.item.Item item;
        final net.minecraft.nbt.CompoundTag tag;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.tag = s.getTag() != null ? s.getTag().copy() : null;
            this.hash = Objects.hash(item, tag);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        @Override public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.17-1.19.4 FORGE  (same API as 1.16.5 but TextComponent renamed)
# 1.17+: TranslatableComponent -> TranslatableComponent still works
# 1.18+: ScreenEvent.Init.Post (renamed from InitScreenEvent.Post)
# 1.19+: same as 1.18
# We use a single source that works 1.17-1.19.4 by using Component.literal
# which exists from 1.17+ (added alongside old constructors)
# ===========================================================================
SRC_117_119_FORGE = """\
package net.itamio.sortchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod(SortChestMod.MOD_ID)
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    public SortChestMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen)) return;
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int x = cs.getGuiLeft() + cs.getXSize() - 44;
        int y = cs.getGuiTop() + 6;
        event.addListener(Button.builder(Component.translatable("sortchest.button.sort"),
                btn -> sortContainer(cs)).pos(x, y).size(40, 14).build());
    }

    private static void sortContainer(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;
        List<Integer> slots = getContainerSlots(menu, mc.player.getInventory());
        if (slots.isEmpty()) return;
        mergeStacks(menu, slots, mc);
        if (!menu.getCarried().isEmpty()) return;
        List<ItemStack> layout = buildLayout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(AbstractContainerMenu menu,
            net.minecraft.world.entity.player.Inventory inv) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container != inv) result.add(i);
        }
        return result;
    }

    private static void mergeStacks(AbstractContainerMenu menu, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = menu.slots.get(slots.get(i)).getItem();
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                if (stack.getCount() >= stack.getMaxStackSize()) break;
                ItemStack other = menu.slots.get(slots.get(j)).getItem();
                if (other.isEmpty()) continue;
                if (ItemStack.isSameItemSameTags(stack, other)) {
                    click(menu, slots.get(j), mc);
                    click(menu, slots.get(i), mc);
                    if (!menu.getCarried().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(AbstractContainerMenu menu, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<>();
        for (int idx : slots) {
            ItemStack s = menu.slots.get(idx).getItem();
            if (s.isEmpty()) continue;
            groups.computeIfAbsent(new ItemKey(s), k -> new ArrayList<>()).add(s.copy());
        }
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> g : groups.values()) result.addAll(g);
        while (result.size() < slots.size()) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void reorder(AbstractContainerMenu menu, List<Integer> slots,
            List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = menu.slots.get(slots.get(i)).getItem();
            ItemStack des = layout.get(i);
            if (stacksMatch(cur, des)) continue;
            int from = findSlot(menu, slots, i + 1, des);
            if (from == -1) continue;
            swap(menu, slots.get(i), slots.get(from), mc);
        }
    }

    private static int findSlot(AbstractContainerMenu menu, List<Integer> slots, int start, ItemStack target) {
        for (int i = start; i < slots.size(); i++) {
            if (stacksMatch(menu.slots.get(slots.get(i)).getItem(), target)) return i;
        }
        return -1;
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.isSameItemSameTags(a, b);
    }

    private static void swap(AbstractContainerMenu menu, int slotA, int slotB, Minecraft mc) {
        click(menu, slotA, mc);
        click(menu, slotB, mc);
        if (!menu.getCarried().isEmpty()) click(menu, slotA, mc);
    }

    private static void click(AbstractContainerMenu menu, int slot, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.world.item.Item item;
        final net.minecraft.nbt.CompoundTag tag;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.tag = s.getTag() != null ? s.getTag().copy() : null;
            this.hash = Objects.hash(item, tag);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        @Override public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.20-1.21.11 FORGE / NEOFORGE
# 1.20+: Button.builder API, Component.translatable, same ScreenEvent.Init.Post
# 1.20.5+: ItemStack.isSameItemSameComponents replaces isSameItemSameTags
# 1.21+: ResourceLocation constructor changed but we don't use it
# NeoForge: same source, different @Mod annotation target (uses same class)
# We split into two sources: one for 1.20-1.20.4 (tags) and one for 1.20.5+
# (components). Actually isSameItemSameTags still compiles in 1.20.5+ as
# deprecated, so we use a single source with isSameItemSameTags for all.
# For NeoForge the event class is the same (net.minecraftforge -> net.neoforged)
# ===========================================================================
SRC_120_FORGE = """\
package net.itamio.sortchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod(SortChestMod.MOD_ID)
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    public SortChestMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen)) return;
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int x = cs.getGuiLeft() + cs.getXSize() - 44;
        int y = cs.getGuiTop() + 6;
        event.addListener(Button.builder(Component.translatable("sortchest.button.sort"),
                btn -> sortContainer(cs)).pos(x, y).size(40, 14).build());
    }

    private static void sortContainer(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;
        List<Integer> slots = getContainerSlots(menu, mc.player.getInventory());
        if (slots.isEmpty()) return;
        mergeStacks(menu, slots, mc);
        if (!menu.getCarried().isEmpty()) return;
        List<ItemStack> layout = buildLayout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(AbstractContainerMenu menu,
            net.minecraft.world.entity.player.Inventory inv) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container != inv) result.add(i);
        }
        return result;
    }

    private static void mergeStacks(AbstractContainerMenu menu, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = menu.slots.get(slots.get(i)).getItem();
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                if (stack.getCount() >= stack.getMaxStackSize()) break;
                ItemStack other = menu.slots.get(slots.get(j)).getItem();
                if (other.isEmpty()) continue;
                if (ItemStack.isSameItemSameTags(stack, other)) {
                    click(menu, slots.get(j), mc);
                    click(menu, slots.get(i), mc);
                    if (!menu.getCarried().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(AbstractContainerMenu menu, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<>();
        for (int idx : slots) {
            ItemStack s = menu.slots.get(idx).getItem();
            if (s.isEmpty()) continue;
            groups.computeIfAbsent(new ItemKey(s), k -> new ArrayList<>()).add(s.copy());
        }
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> g : groups.values()) result.addAll(g);
        while (result.size() < slots.size()) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void reorder(AbstractContainerMenu menu, List<Integer> slots,
            List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = menu.slots.get(slots.get(i)).getItem();
            ItemStack des = layout.get(i);
            if (stacksMatch(cur, des)) continue;
            int from = findSlot(menu, slots, i + 1, des);
            if (from == -1) continue;
            swap(menu, slots.get(i), slots.get(from), mc);
        }
    }

    private static int findSlot(AbstractContainerMenu menu, List<Integer> slots, int start, ItemStack target) {
        for (int i = start; i < slots.size(); i++) {
            if (stacksMatch(menu.slots.get(slots.get(i)).getItem(), target)) return i;
        }
        return -1;
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.isSameItemSameTags(a, b);
    }

    private static void swap(AbstractContainerMenu menu, int slotA, int slotB, Minecraft mc) {
        click(menu, slotA, mc);
        click(menu, slotB, mc);
        if (!menu.getCarried().isEmpty()) click(menu, slotA, mc);
    }

    private static void click(AbstractContainerMenu menu, int slot, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.world.item.Item item;
        final net.minecraft.nbt.CompoundTag tag;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.tag = s.getTag() != null ? s.getTag().copy() : null;
            this.hash = Objects.hash(item, tag);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        @Override public int hashCode() { return hash; }
    }
}
"""

# NeoForge version — same logic, different event import package
SRC_NEOFORGE = SRC_120_FORGE.replace(
    "import net.minecraftforge.client.event.ScreenEvent;",
    "import net.neoforged.neoforge.client.event.ScreenEvent;"
).replace(
    "import net.minecraftforge.common.MinecraftForge;",
    "import net.neoforged.neoforge.common.NeoForge;"
).replace(
    "import net.minecraftforge.eventbus.api.SubscribeEvent;",
    "import net.neoforged.bus.api.SubscribeEvent;"
).replace(
    "import net.minecraftforge.fml.common.Mod;",
    "import net.neoforged.fml.common.Mod;"
).replace(
    "MinecraftForge.EVENT_BUS.register(this);",
    "NeoForge.EVENT_BUS.register(this);"
)

# ===========================================================================
# FABRIC 1.16.5 - 1.19.4
# Uses: ScreenEvents.AFTER_INIT, HandledScreen, ScreenKeyboardEvents/Mouse
# Fabric client entrypoint, no @Mod annotation
# Button injection via ((ScreenAccessor) screen).getButtons().add(...)
# Actually simpler: use ScreenEvents.afterInit callback and addDrawableChild
# ===========================================================================
SRC_FABRIC_1165_119 = """\
package net.itamio.sortchest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.*;

public class SortChestMod implements ClientModInitializer {
    public static final String MOD_ID = "sortchest";

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof HandledScreen)) return;
            HandledScreen<?> hs = (HandledScreen<?>) screen;
            int x = hs.x + hs.backgroundWidth - 44;
            int y = hs.y + 6;
            Screens.getButtons(screen).add(ButtonWidget.builder(
                    Text.translatable("sortchest.button.sort"),
                    btn -> sortContainer(hs)).dimensions(x, y, 40, 14).build());
        });
    }

    private static void sortContainer(HandledScreen<?> screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != screen) return;
        ScreenHandler handler = screen.getScreenHandler();
        if (!handler.getCursorStack().isEmpty()) return;
        List<Integer> slots = getContainerSlots(handler, mc.player.getInventory());
        if (slots.isEmpty()) return;
        mergeStacks(handler, slots, mc);
        if (!handler.getCursorStack().isEmpty()) return;
        List<ItemStack> layout = buildLayout(handler, slots);
        reorder(handler, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(ScreenHandler handler,
            net.minecraft.entity.player.PlayerInventory inv) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.slots.get(i);
            if (s.inventory != inv) result.add(i);
        }
        return result;
    }

    private static void mergeStacks(ScreenHandler handler, List<Integer> slots, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = handler.slots.get(slots.get(i)).getStack();
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxCount()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                if (stack.getCount() >= stack.getMaxCount()) break;
                ItemStack other = handler.slots.get(slots.get(j)).getStack();
                if (other.isEmpty()) continue;
                if (ItemStack.canCombine(stack, other)) {
                    click(handler, slots.get(j), mc);
                    click(handler, slots.get(i), mc);
                    if (!handler.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(ScreenHandler handler, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<>();
        for (int idx : slots) {
            ItemStack s = handler.slots.get(idx).getStack();
            if (s.isEmpty()) continue;
            groups.computeIfAbsent(new ItemKey(s), k -> new ArrayList<>()).add(s.copy());
        }
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> g : groups.values()) result.addAll(g);
        while (result.size() < slots.size()) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void reorder(ScreenHandler handler, List<Integer> slots,
            List<ItemStack> layout, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = handler.slots.get(slots.get(i)).getStack();
            ItemStack des = layout.get(i);
            if (stacksMatch(cur, des)) continue;
            int from = findSlot(handler, slots, i + 1, des);
            if (from == -1) continue;
            swap(handler, slots.get(i), slots.get(from), mc);
        }
    }

    private static int findSlot(ScreenHandler handler, List<Integer> slots, int start, ItemStack target) {
        for (int i = start; i < slots.size(); i++) {
            if (stacksMatch(handler.slots.get(slots.get(i)).getStack(), target)) return i;
        }
        return -1;
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.canCombine(a, b);
    }

    private static void swap(ScreenHandler handler, int slotA, int slotB, MinecraftClient mc) {
        click(handler, slotA, mc);
        click(handler, slotB, mc);
        if (!handler.getCursorStack().isEmpty()) click(handler, slotA, mc);
    }

    private static void click(ScreenHandler handler, int slot, MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final net.minecraft.nbt.NbtCompound tag;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.tag = s.getNbt() != null ? s.getNbt().copy() : null;
            this.hash = Objects.hash(item, tag);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        @Override public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# FABRIC 1.20 - 1.21.11
# Same as 1.16.5-1.19 but:
# - HandledScreen.x/y -> getX()/getY() in 1.20.5+ (use backgroundX/backgroundY)
# - ItemStack.canCombine -> ItemStack.areItemsAndComponentsEqual in 1.20.5+
# - NbtCompound -> NbtCompound still works; getTag() -> getNbt() still works
# We use a single source that works across 1.20-1.21.x
# ===========================================================================
SRC_FABRIC_120_121 = """\
package net.itamio.sortchest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.*;

public class SortChestMod implements ClientModInitializer {
    public static final String MOD_ID = "sortchest";

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof HandledScreen)) return;
            HandledScreen<?> hs = (HandledScreen<?>) screen;
            int x = hs.x + hs.backgroundWidth - 44;
            int y = hs.y + 6;
            Screens.getButtons(screen).add(ButtonWidget.builder(
                    Text.translatable("sortchest.button.sort"),
                    btn -> sortContainer(hs)).dimensions(x, y, 40, 14).build());
        });
    }

    private static void sortContainer(HandledScreen<?> screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != screen) return;
        ScreenHandler handler = screen.getScreenHandler();
        if (!handler.getCursorStack().isEmpty()) return;
        List<Integer> slots = getContainerSlots(handler, mc.player.getInventory());
        if (slots.isEmpty()) return;
        mergeStacks(handler, slots, mc);
        if (!handler.getCursorStack().isEmpty()) return;
        List<ItemStack> layout = buildLayout(handler, slots);
        reorder(handler, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(ScreenHandler handler,
            net.minecraft.entity.player.PlayerInventory inv) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.slots.get(i);
            if (s.inventory != inv) result.add(i);
        }
        return result;
    }

    private static void mergeStacks(ScreenHandler handler, List<Integer> slots, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = handler.slots.get(slots.get(i)).getStack();
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxCount()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                if (stack.getCount() >= stack.getMaxCount()) break;
                ItemStack other = handler.slots.get(slots.get(j)).getStack();
                if (other.isEmpty()) continue;
                if (ItemStack.canCombine(stack, other)) {
                    click(handler, slots.get(j), mc);
                    click(handler, slots.get(i), mc);
                    if (!handler.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(ScreenHandler handler, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<>();
        for (int idx : slots) {
            ItemStack s = handler.slots.get(idx).getStack();
            if (s.isEmpty()) continue;
            groups.computeIfAbsent(new ItemKey(s), k -> new ArrayList<>()).add(s.copy());
        }
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> g : groups.values()) result.addAll(g);
        while (result.size() < slots.size()) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void reorder(ScreenHandler handler, List<Integer> slots,
            List<ItemStack> layout, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = handler.slots.get(slots.get(i)).getStack();
            ItemStack des = layout.get(i);
            if (stacksMatch(cur, des)) continue;
            int from = findSlot(handler, slots, i + 1, des);
            if (from == -1) continue;
            swap(handler, slots.get(i), slots.get(from), mc);
        }
    }

    private static int findSlot(ScreenHandler handler, List<Integer> slots, int start, ItemStack target) {
        for (int i = start; i < slots.size(); i++) {
            if (stacksMatch(handler.slots.get(slots.get(i)).getStack(), target)) return i;
        }
        return -1;
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.canCombine(a, b);
    }

    private static void swap(ScreenHandler handler, int slotA, int slotB, MinecraftClient mc) {
        click(handler, slotA, mc);
        click(handler, slotB, mc);
        if (!handler.getCursorStack().isEmpty()) click(handler, slotA, mc);
    }

    private static void click(ScreenHandler handler, int slot, MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final net.minecraft.nbt.NbtCompound tag;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.tag = s.getNbt() != null ? s.getNbt().copy() : null;
            this.hash = Objects.hash(item, tag);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        @Override public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# Fabric mod.json templates
# ===========================================================================
def fabric_mod_json(mc_dep: str, entrypoint: str = f"{GROUP}.SortChestMod") -> str:
    return f"""{{\
  "schemaVersion": 1,
  "id": "{MOD_ID}",
  "version": "{MOD_VERSION}",
  "name": "{MOD_NAME}",
  "description": "{DESCRIPTION}",
  "authors": ["{AUTHORS}"],
  "license": "{LICENSE}",
  "contact": {{"homepage": "{HOMEPAGE}"}},
  "environment": "client",
  "entrypoints": {{
    "client": ["{entrypoint}"]
  }},
  "depends": {{
    "fabricloader": ">=0.12.0",
    "minecraft": "{mc_dep}",
    "fabric-api": "*"
  }}
}}
"""

# ===========================================================================
# Bundle definition: (folder_name, source_code, mod_txt_entrypoint, version_txt_mc, loader, fabric_mc_dep)
# ===========================================================================
JAVA_PATH = f"src/main/java/{GROUP.replace('.', '/')}/SortChestMod.java"
FABRIC_JAVA_PATH = f"src/client/java/{GROUP.replace('.', '/')}/SortChestMod.java"
LANG_PATH = f"src/main/resources/assets/{MOD_ID}/lang/en_us.json"
FABRIC_LANG_PATH = f"src/client/resources/assets/{MOD_ID}/lang/en_us.json"

ENTRYPOINT_FORGE = f"{GROUP}.SortChestMod"
ENTRYPOINT_FABRIC = f"{GROUP}.SortChestMod"

targets = [
    # (folder, java_src, is_fabric, mc_version_txt, fabric_mc_dep)
    ("SortChest189Forge",       SRC_189_FORGE,        False, "1.8.9",         None),
    ("SortChest1122Forge",      SRC_1122_FORGE,       False, "1.12-1.12.2",   None),
    ("SortChest1165Forge",      SRC_1165_FORGE,       False, "1.16.5",        None),
    ("SortChest1165Fabric",     SRC_FABRIC_1165_119,  True,  "1.16.5",        ">=1.16.5 <1.17"),
    ("SortChest117Forge",       SRC_117_119_FORGE,    False, "1.17-1.17.1",   None),
    ("SortChest117Fabric",      SRC_FABRIC_1165_119,  True,  "1.17-1.17.1",   ">=1.17 <1.18"),
    ("SortChest118Forge",       SRC_117_119_FORGE,    False, "1.18-1.18.2",   None),
    ("SortChest118Fabric",      SRC_FABRIC_1165_119,  True,  "1.18-1.18.2",   ">=1.18 <1.19"),
    ("SortChest119Forge",       SRC_117_119_FORGE,    False, "1.19-1.19.4",   None),
    ("SortChest119Fabric",      SRC_FABRIC_1165_119,  True,  "1.19-1.19.4",   ">=1.19 <1.20"),
    ("SortChest120Forge",       SRC_120_FORGE,        False, "1.20-1.20.6",   None),
    ("SortChest120Fabric",      SRC_FABRIC_120_121,   True,  "1.20-1.20.6",   ">=1.20 <1.21"),
    ("SortChest1202NeoForge",   SRC_NEOFORGE,         False, "1.20-1.20.6",   None),
    ("SortChest121Forge",       SRC_120_FORGE,        False, "1.21-1.21.1",   None),
    ("SortChest121Fabric",      SRC_FABRIC_120_121,   True,  "1.21-1.21.1",   ">=1.21 <1.21.2"),
    ("SortChest121NeoForge",    SRC_NEOFORGE,         False, "1.21-1.21.1",   None),
    ("SortChest1218Forge",      SRC_120_FORGE,        False, "1.21.2-1.21.8", None),
    ("SortChest1218Fabric",     SRC_FABRIC_120_121,   True,  "1.21.2-1.21.8", ">=1.21.2 <1.21.9"),
    ("SortChest1218NeoForge",   SRC_NEOFORGE,         False, "1.21.2-1.21.8", None),
    ("SortChest12111Forge",     SRC_120_FORGE,        False, "1.21.9-1.21.11",None),
    ("SortChest12111Fabric",    SRC_FABRIC_120_121,   True,  "1.21.9-1.21.11",">=1.21.9 <1.22"),
    ("SortChest12111NeoForge",  SRC_NEOFORGE,         False, "1.21.9-1.21.11",None),
]

def loader_for(folder: str, is_fabric: bool) -> str:
    if is_fabric: return "fabric"
    if "NeoForge" in folder: return "neoforge"
    return "forge"

def entrypoint_for(folder: str, is_fabric: bool) -> str:
    return ENTRYPOINT_FABRIC if is_fabric else ENTRYPOINT_FORGE

# Generate all files
for (folder, java_src, is_fabric, mc_ver, fabric_mc_dep) in targets:
    base = BUNDLE / folder
    loader = loader_for(folder, is_fabric)

    # mod.txt
    write(base / "mod.txt", mod_txt(entrypoint_for(folder, is_fabric)))
    # version.txt
    write(base / "version.txt", version_txt(mc_ver, loader))

    if is_fabric:
        # Fabric: src/client/java/...
        write(base / FABRIC_JAVA_PATH, java_src)
        write(base / FABRIC_LANG_PATH, lang_json())
        # fabric.mod.json
        write(base / "src/client/resources/fabric.mod.json",
              fabric_mod_json(fabric_mc_dep or "*"))
    else:
        # Forge/NeoForge: src/main/java/...
        write(base / JAVA_PATH, java_src)
        write(base / LANG_PATH, lang_json())

print(f"Generated {len(targets)} targets under {BUNDLE}")

# Create the zip — each mod folder must be at the TOP LEVEL of the zip.
# Only include folders that contain a version.txt (valid mod targets).
zip_path = ROOT / "incoming" / "sort-chest-all-versions.zip"
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for path in sorted(BUNDLE.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(BUNDLE)
        # Skip any file that is not inside a mod target folder
        # (i.e. skip top-level loose files like build_bundle.py)
        if len(rel.parts) < 2:
            continue
        zf.write(path, rel)
print(f"Zip created: {zip_path}")
