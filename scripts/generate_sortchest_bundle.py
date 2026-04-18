#!/usr/bin/env python3
"""
Generates the Sort Chest all-versions bundle under incoming/sort-chest-all-versions/
Run: python3 scripts/generate_sortchest_bundle.py
"""
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

JAVA_PATH = f"src/main/java/{GROUP.replace('.', '/')}/SortChestMod.java"
LANG_PATH = f"src/main/resources/assets/{MOD_ID}/lang/en_us.json"
ENTRYPOINT = f"{GROUP}.SortChestMod"

# ===========================================================================
# 1.8.9 FORGE — Java 6 source level, no diamonds, no lambdas, no computeIfAbsent
# GuiOpenEvent, GuiContainer, GuiButton, playerController.windowClick
# ClickType.PICKUP exists in 1.8.9 Forge
# ===========================================================================
SRC_189_FORGE = """\
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mod(modid = SortChestMod.MOD_ID, name = "Sort Chest", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    public SortChestMod() {
        MinecraftForge.EVENT_BUS.register(this);
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
        List<Integer> slots = getContainerSlots(container, mc.thePlayer.inventory);
        if (slots.isEmpty()) return;
        mergeStacks(container, slots, mc);
        List<ItemStack> layout = buildLayout(container, slots);
        reorder(container, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(Container c,
            net.minecraft.entity.player.InventoryPlayer inv) {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            Slot s = c.getSlot(i);
            if (s.inventory != inv) result.add(Integer.valueOf(i));
        }
        return result;
    }

    private static void mergeStacks(Container c, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = c.getSlot(slots.get(i).intValue()).getStack();
            if (stack == null || stack.stackSize >= stack.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                if (stack.stackSize >= stack.getMaxStackSize()) break;
                ItemStack other = c.getSlot(slots.get(j).intValue()).getStack();
                if (other == null) continue;
                if (ItemStack.areItemsEqual(stack, other) && ItemStack.areItemStackTagsEqual(stack, other)) {
                    click(c, slots.get(j).intValue(), mc);
                    click(c, slots.get(i).intValue(), mc);
                    ItemStack held = mc.thePlayer.inventory.getItemStack();
                    if (held != null && held.stackSize > 0) {
                        click(c, slots.get(j).intValue(), mc);
                    }
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(Container c, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = c.getSlot(slots.get(i).intValue()).getStack();
            if (s == null) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<ItemStack>();
                groups.put(key, group);
            }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (List<ItemStack> g : groups.values()) result.addAll(g);
        while (result.size() < slots.size()) result.add(null);
        return result;
    }

    private static void reorder(Container c, List<Integer> slots, List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = c.getSlot(slots.get(i).intValue()).getStack();
            ItemStack des = layout.get(i);
            if (stacksMatch(cur, des)) continue;
            int from = findSlot(c, slots, i + 1, des);
            if (from == -1) continue;
            swap(c, slots.get(i).intValue(), slots.get(from).intValue(), mc);
        }
    }

    private static int findSlot(Container c, List<Integer> slots, int start, ItemStack target) {
        for (int i = start; i < slots.size(); i++) {
            if (stacksMatch(c.getSlot(slots.get(i).intValue()).getStack(), target)) return i;
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
        click(c, slotA, mc);
        click(c, slotB, mc);
        ItemStack held = mc.thePlayer.inventory.getItemStack();
        if (held != null && held.stackSize > 0) click(c, slotA, mc);
    }

    private static void click(Container c, int slot, Minecraft mc) {
        if (mc.thePlayer == null || mc.playerController == null) return;
        mc.playerController.windowClick(c.windowId, slot, 0, ClickType.PICKUP, mc.thePlayer);
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
            this.hash = Objects.hash(item, Integer.valueOf(meta), tag);
        }
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && meta == k.meta && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.12.2 FORGE — guiLeft/xSize/guiTop are protected, access via subclass trick
# Use GuiScreenEvent, GuiContainer subclass to expose fields
# getSlotUnderMouse() doesn't exist — skip that check
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mod(modid = SortChestMod.MOD_ID, name = "Sort Chest", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    // Subclass to expose protected fields
    private static final class GuiContainerAccessor extends GuiContainer {
        GuiContainerAccessor() { super(null); }
        static int getLeft(GuiContainer g) {
            try {
                java.lang.reflect.Field f = GuiContainer.class.getDeclaredField("guiLeft");
                f.setAccessible(true);
                return f.getInt(g);
            } catch (Exception e) { return 0; }
        }
        static int getTop(GuiContainer g) {
            try {
                java.lang.reflect.Field f = GuiContainer.class.getDeclaredField("guiTop");
                f.setAccessible(true);
                return f.getInt(g);
            } catch (Exception e) { return 0; }
        }
        static int getWidth(GuiContainer g) {
            try {
                java.lang.reflect.Field f = GuiContainer.class.getDeclaredField("xSize");
                f.setAccessible(true);
                return f.getInt(g);
            } catch (Exception e) { return 176; }
        }
        protected void drawGuiContainerBackgroundLayer(float p, int mx, int my) {}
    }

    public SortChestMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.getGui();
        int x = GuiContainerAccessor.getLeft(gui) + GuiContainerAccessor.getWidth(gui) - 44;
        int y = GuiContainerAccessor.getTop(gui) + 6;
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
        List<Integer> slots = getContainerSlots(container, mc.player.inventory);
        if (slots.isEmpty()) return;
        mergeStacks(container, slots, mc);
        List<ItemStack> layout = buildLayout(container, slots);
        reorder(container, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(Container c,
            net.minecraft.entity.player.InventoryPlayer inv) {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            Slot s = c.getSlot(i);
            if (s.inventory != inv) result.add(Integer.valueOf(i));
        }
        return result;
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
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = c.getSlot(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
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
            this.hash = Objects.hash(item, Integer.valueOf(meta), tag);
        }
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && meta == k.meta && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.16.5 FORGE — old MCP package names (net.minecraft.client.gui.screen,
# net.minecraft.inventory, net.minecraft.client.gui.widget)
# ScreenEvent.InitScreenEvent.Post, new Button(x,y,w,h,text,handler)
# StringTextComponent, ITextComponent
# ===========================================================================
SRC_1165_FORGE = """\
package net.itamio.sortchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mod(SortChestMod.MOD_ID)
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    public SortChestMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        Screen screen = event.getGui();
        if (!(screen instanceof ContainerScreen)) return;
        ContainerScreen<?> cs = (ContainerScreen<?>) screen;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int x = cs.getGuiLeft() + cs.getXSize() - 44;
        int y = cs.getGuiTop() + 6;
        event.addWidget(new Button(x, y, 40, 14,
                new StringTextComponent("Sort"),
                btn -> sortContainer(cs)));
    }

    private static void sortContainer(ContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        Container menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;
        List<Integer> slots = getContainerSlots(menu, mc.player.inventory);
        if (slots.isEmpty()) return;
        mergeStacks(menu, slots, mc);
        if (!menu.getCarried().isEmpty()) return;
        List<ItemStack> layout = buildLayout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> getContainerSlots(Container menu,
            net.minecraft.entity.player.PlayerInventory inv) {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container != inv) result.add(Integer.valueOf(i));
        }
        return result;
    }

    private static void mergeStacks(Container menu, List<Integer> slots, Minecraft mc) {
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

    private static List<ItemStack> buildLayout(Container menu, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (List<ItemStack> g : groups.values()) result.addAll(g);
        while (result.size() < slots.size()) result.add(ItemStack.EMPTY);
        return result;
    }

    private static void reorder(Container menu, List<Integer> slots,
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

    private static int findSlot(Container menu, List<Integer> slots, int start, ItemStack target) {
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

    private static void swap(Container menu, int slotA, int slotB, Minecraft mc) {
        click(menu, slotA, mc);
        click(menu, slotB, mc);
        if (!menu.getCarried().isEmpty()) click(menu, slotA, mc);
    }

    private static void click(Container menu, int slot, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final net.minecraft.nbt.CompoundNBT tag;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.tag = s.getTag() != null ? s.getTag().copy() : null;
            this.hash = Objects.hash(item, tag);
        }
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.17.1 FORGE — ScreenEvent.InitScreenEvent.Post (not Init.Post yet)
# TranslatableComponent (not Component.translatable yet)
# new Button(x,y,w,h,text,handler) — builder API not yet available
# net.minecraft.client.gui.screens (new package names from 1.17+)
# ===========================================================================
SRC_1171_FORGE = """\
package net.itamio.sortchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                new TranslatableComponent("sortchest.button.sort"),
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
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container != inv) result.add(Integer.valueOf(i));
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
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.18-1.19.4 FORGE — ScreenEvent.Init.Post (renamed in 1.18)
# TranslatableComponent still exists in 1.18/1.19 (Component.translatable added in 1.19.4)
# new Button(x,y,w,h,text,handler) — builder API not yet available until 1.20
# ===========================================================================
SRC_118_119_FORGE = """\
package net.itamio.sortchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        event.addListener(new Button(x, y, 40, 14,
                new TranslatableComponent("sortchest.button.sort"),
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
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container != inv) result.add(Integer.valueOf(i));
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
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.20.x FORGE — Button.builder() API available, Component.translatable()
# isSameItemSameTags still valid in 1.20.x, getTag() still valid
# This is the source that already worked for 1.20.1-1.20.4
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        event.addListener(Button.builder(
                Component.translatable("sortchest.button.sort"),
                btn -> sortContainer(cs))
                .pos(x, y).size(40, 14).build());
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
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container != inv) result.add(Integer.valueOf(i));
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
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# 1.21+ FORGE — isSameItemSameTags removed → isSameItemSameComponents
# getTag() removed → getComponents() / DataComponents pattern
# For comparison we use ItemStack.isSameItemSameComponents (1.20.5+)
# For ItemKey we compare via item + components hash
# ===========================================================================
SRC_121_FORGE = """\
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        event.addListener(Button.builder(
                Component.translatable("sortchest.button.sort"),
                btn -> sortContainer(cs))
                .pos(x, y).size(40, 14).build());
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
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container != inv) result.add(Integer.valueOf(i));
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
                if (ItemStack.isSameItemSameComponents(stack, other)) {
                    click(menu, slots.get(j), mc);
                    click(menu, slots.get(i), mc);
                    if (!menu.getCarried().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(AbstractContainerMenu menu, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
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
        return ItemStack.isSameItemSameComponents(a, b);
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
        final net.minecraft.core.component.DataComponentMap components;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.components = s.getComponents();
            this.hash = Objects.hash(item, components);
        }
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(components, k.components);
        }
        public int hashCode() { return hash; }
    }
}
"""

# NeoForge 1.21+ — same as Forge 1.21 but different event/bus imports
SRC_121_NEOFORGE = SRC_121_FORGE.replace(
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

# NeoForge 1.20.x — same as Forge 1.20 but different imports
SRC_120_NEOFORGE = SRC_120_FORGE.replace(
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
# CRITICAL FIX: source must be under src/main/java NOT src/client/java
# The fabric_presplit adapter family expects src/main/java layout
# fabric.mod.json goes in src/main/resources/
# HandledScreen.x/y/backgroundWidth are public in these versions
# ItemStack.canCombine() for comparison
# NbtCompound for tag
# ===========================================================================
SRC_FABRIC_1165_119 = """\
package net.itamio.sortchest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TranslatableText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                    new TranslatableText("sortchest.button.sort"),
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
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.slots.get(i);
            if (s.inventory != inv) result.add(Integer.valueOf(i));
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
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = handler.slots.get(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# FABRIC 1.20.x
# Text.translatable() replaces TranslatableText (removed in 1.19.4)
# HandledScreen.x/y still public
# ItemStack.canCombine still valid in 1.20.x
# getNbt() still valid in 1.20.x
# ===========================================================================
SRC_FABRIC_120 = """\
package net.itamio.sortchest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.slots.get(i);
            if (s.inventory != inv) result.add(Integer.valueOf(i));
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
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = handler.slots.get(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# FABRIC 1.21+
# ItemStack.areItemsAndComponentsEqual replaces canCombine (1.20.5+)
# getNbt() removed → use getComponents() for ItemKey
# HandledScreen.x/y still public
# ===========================================================================
SRC_FABRIC_121 = """\
package net.itamio.sortchest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.slots.get(i);
            if (s.inventory != inv) result.add(Integer.valueOf(i));
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
                if (ItemStack.areItemsAndComponentsEqual(stack, other)) {
                    click(handler, slots.get(j), mc);
                    click(handler, slots.get(i), mc);
                    if (!handler.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(ScreenHandler handler, List<Integer> slots) {
        Map<ItemKey, List<ItemStack>> groups = new LinkedHashMap<ItemKey, List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = handler.slots.get(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> group = groups.get(key);
            if (group == null) { group = new ArrayList<ItemStack>(); groups.put(key, group); }
            group.add(s.copy());
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
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
        return ItemStack.areItemsAndComponentsEqual(a, b);
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
        final net.minecraft.component.ComponentMap components;
        final int hash;
        ItemKey(ItemStack s) {
            this.item = s.getItem();
            this.components = s.getComponents();
            this.hash = Objects.hash(item, components);
        }
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey) o;
            return item == k.item && Objects.equals(components, k.components);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ===========================================================================
# fabric.mod.json — goes in src/main/resources/ (presplit adapter)
# ===========================================================================
def fabric_mod_json(mc_dep: str) -> str:
    return (
        '{\n'
        f'  "schemaVersion": 1,\n'
        f'  "id": "{MOD_ID}",\n'
        f'  "version": "{MOD_VERSION}",\n'
        f'  "name": "{MOD_NAME}",\n'
        f'  "description": "{DESCRIPTION}",\n'
        f'  "authors": ["{AUTHORS}"],\n'
        f'  "license": "{LICENSE}",\n'
        f'  "contact": {{"homepage": "{HOMEPAGE}"}},\n'
        f'  "environment": "client",\n'
        f'  "entrypoints": {{\n'
        f'    "client": ["{ENTRYPOINT}"]\n'
        f'  }},\n'
        f'  "depends": {{\n'
        f'    "fabricloader": ">=0.12.0",\n'
        f'    "minecraft": "{mc_dep}",\n'
        f'    "fabric-api": "*"\n'
        f'  }}\n'
        f'}}\n'
    )

# ===========================================================================
# Bundle targets
# Each entry: (folder, java_src, is_fabric, mc_version_txt, fabric_mc_dep)
#
# KEY FIXES vs previous run:
# - All Fabric targets use src/main/java (presplit adapter expects this)
# - Version specs use exact supported versions from version-manifest.json
#   (no "1.20", "1.17", "1.21" — only versions in supported_versions lists)
# - 1.8.9: Java-6-compatible source (no diamonds, no lambdas)
# - 1.12.x: reflection to access protected GuiContainer fields
# - 1.16.5 Forge: old MCP package names
# - 1.17.1 Forge: TranslatableComponent + InitScreenEvent.Post
# - 1.18-1.19 Forge: TranslatableComponent + Init.Post
# - 1.20.x Forge: Button.builder + Component.translatable (already worked)
# - 1.21+ Forge/NeoForge: isSameItemSameComponents + DataComponentMap
# - 1.16.5-1.19 Fabric: TranslatableText + canCombine + NbtCompound
# - 1.20.x Fabric: Text.translatable + canCombine + NbtCompound
# - 1.21+ Fabric: areItemsAndComponentsEqual + ComponentMap
# ===========================================================================
targets = [
    # (folder, java_src, is_fabric, mc_version_txt, fabric_mc_dep_or_None)
    ("SortChest189Forge",       SRC_189_FORGE,        False, "1.8.9",    None),
    ("SortChest1122Forge",      SRC_1122_FORGE,       False, "1.12.2",   None),
    ("SortChest1165Forge",      SRC_1165_FORGE,       False, "1.16.5",   None),
    ("SortChest1165Fabric",     SRC_FABRIC_1165_119,  True,  "1.16.5",   ">=1.16.5 <1.17"),
    ("SortChest1171Forge",      SRC_1171_FORGE,       False, "1.17.1",   None),
    ("SortChest1171Fabric",     SRC_FABRIC_1165_119,  True,  "1.17.1",   ">=1.17 <1.18"),
    ("SortChest1182Forge",      SRC_118_119_FORGE,    False, "1.18.2",   None),
    ("SortChest1182Fabric",     SRC_FABRIC_1165_119,  True,  "1.18.2",   ">=1.18 <1.19"),
    ("SortChest1194Forge",      SRC_118_119_FORGE,    False, "1.19.4",   None),
    ("SortChest1194Fabric",     SRC_FABRIC_1165_119,  True,  "1.19.4",   ">=1.19 <1.20"),
    ("SortChest1201Forge",      SRC_120_FORGE,        False, "1.20.1",   None),
    ("SortChest1201Fabric",     SRC_FABRIC_120,       True,  "1.20.1",   ">=1.20.1 <1.20.2"),
    ("SortChest1204Forge",      SRC_120_FORGE,        False, "1.20.4",   None),
    ("SortChest1204Fabric",     SRC_FABRIC_120,       True,  "1.20.4",   ">=1.20.4 <1.20.5"),
    ("SortChest1206Forge",      SRC_120_FORGE,        False, "1.20.6",   None),
    ("SortChest1206Fabric",     SRC_FABRIC_120,       True,  "1.20.6",   ">=1.20.6 <1.21"),
    ("SortChest1202NeoForge",   SRC_120_NEOFORGE,     False, "1.20.2",   None),
    ("SortChest1204NeoForge",   SRC_120_NEOFORGE,     False, "1.20.4",   None),
    ("SortChest1206NeoForge",   SRC_120_NEOFORGE,     False, "1.20.6",   None),
    ("SortChest121Forge",       SRC_120_FORGE,        False, "1.21.1",   None),
    ("SortChest121Fabric",      SRC_FABRIC_121,       True,  "1.21.1",   ">=1.21 <1.21.2"),
    ("SortChest121NeoForge",    SRC_121_NEOFORGE,     False, "1.21.1",   None),
    ("SortChest1214Forge",      SRC_121_FORGE,        False, "1.21.4",   None),
    ("SortChest1214Fabric",     SRC_FABRIC_121,       True,  "1.21.4",   ">=1.21.2 <1.21.9"),
    ("SortChest1214NeoForge",   SRC_121_NEOFORGE,     False, "1.21.4",   None),
    ("SortChest12111Forge",     SRC_121_FORGE,        False, "1.21.11",  None),
    ("SortChest12111Fabric",    SRC_FABRIC_121,       True,  "1.21.11",  ">=1.21.9 <1.22"),
    ("SortChest12111NeoForge",  SRC_121_NEOFORGE,     False, "1.21.11",  None),
]

def loader_for(folder: str, is_fabric: bool) -> str:
    if is_fabric: return "fabric"
    if "NeoForge" in folder: return "neoforge"
    return "forge"

# Generate all files
import shutil
if BUNDLE.exists():
    shutil.rmtree(BUNDLE)

for (folder, java_src, is_fabric, mc_ver, fabric_mc_dep) in targets:
    base = BUNDLE / folder
    loader = loader_for(folder, is_fabric)

    write(base / "mod.txt", mod_txt(ENTRYPOINT))
    write(base / "version.txt", version_txt(mc_ver, loader))
    # All targets use src/main/java (both Forge and Fabric presplit)
    write(base / JAVA_PATH, java_src)
    write(base / LANG_PATH, lang_json())
    if is_fabric:
        write(base / f"src/main/resources/fabric.mod.json",
              fabric_mod_json(fabric_mc_dep or "*"))

print(f"Generated {len(targets)} targets under {BUNDLE}")

# Create the zip — only files inside mod target folders (depth >= 2)
zip_path = ROOT / "incoming" / "sort-chest-all-versions.zip"
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for path in sorted(BUNDLE.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(BUNDLE)
        if len(rel.parts) < 2:
            continue
        zf.write(path, rel)
print(f"Zip created: {zip_path}")

# Quick sanity check
import subprocess
result = subprocess.run(
    ["python3", "build_mods.py", "prepare",
     "--zip-path", str(zip_path),
     "--manifest", "version-manifest.json",
     "--output-dir", "/tmp/prepare-sanity"],
    capture_output=True, text=True, cwd=str(ROOT)
)
if result.returncode == 0:
    import json
    matrix = json.loads(result.stdout)
    print(f"Prepare OK — {len(matrix.get('include', []))} build targets")
else:
    print(f"Prepare FAILED:\n{result.stderr[:500]}")
