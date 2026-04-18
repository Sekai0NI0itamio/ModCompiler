#!/usr/bin/env python3
"""
Generates the Sort Chest all-versions bundle under incoming/sort-chest-all-versions/
Run: python3 scripts/generate_sortchest_bundle.py
"""
import os, shutil, subprocess, zipfile, json
from pathlib import Path

ROOT   = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "incoming" / "sort-chest-all-versions"

MOD_ID      = "sortchest"
MOD_NAME    = "Sort Chest"
MOD_VERSION = "1.0.0"
GROUP       = "net.itamio.sortchest"
DESCRIPTION = "Client-side mod that adds a Sort button to chest GUIs to consolidate and group item stacks."
AUTHORS     = "Itamio"
LICENSE     = "MIT"
HOMEPAGE    = "https://modrinth.com/mod/sort-chest"
ENTRYPOINT  = f"{GROUP}.SortChestMod"

JAVA_PATH      = f"src/main/java/{GROUP.replace('.','/')}/SortChestMod.java"
CLIENT_JAVA    = f"src/client/java/{GROUP.replace('.','/')}/SortChestMod.java"
LANG_MAIN      = f"src/main/resources/assets/{MOD_ID}/lang/en_us.json"
LANG_CLIENT    = f"src/client/resources/assets/{MOD_ID}/lang/en_us.json"
FAB_MAIN_JSON  = "src/main/resources/fabric.mod.json"
FAB_CLIENT_JSON= "src/client/resources/fabric.mod.json"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt(entrypoint: str = ENTRYPOINT) -> str:
    return (f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
            f"group={GROUP}\nentrypoint_class={entrypoint}\n"
            f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
            f"homepage={HOMEPAGE}\nruntime_side=client\n")

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"

def lang_json() -> str:
    return '{\n  "sortchest.button.sort": "Sort"\n}\n'

def fabric_mod_json(mc_dep: str) -> str:
    return (f'{{\n  "schemaVersion": 1,\n  "id": "{MOD_ID}",\n  "version": "{MOD_VERSION}",\n'
            f'  "name": "{MOD_NAME}",\n  "description": "{DESCRIPTION}",\n'
            f'  "authors": ["{AUTHORS}"],\n  "license": "{LICENSE}",\n'
            f'  "contact": {{"homepage": "{HOMEPAGE}"}},\n  "environment": "client",\n'
            f'  "entrypoints": {{"client": ["{ENTRYPOINT}"]}},\n'
            f'  "depends": {{"fabricloader": ">=0.12.0","minecraft": "{mc_dep}","fabric-api": "*"}}\n}}\n')

# ============================================================
# FORGE SOURCES
# ============================================================

# --- 1.8.9 Forge ---
# Java-6 compat: no diamonds, no lambdas, no computeIfAbsent
# guiLeft/xSize/guiTop are protected → use reflection
# NBTTagCompound.copy() returns NBTBase → cast needed
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mod(modid = SortChestMod.MOD_ID, name = "Sort Chest", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class SortChestMod {
    public static final String MOD_ID = "sortchest";
    public SortChestMod() { MinecraftForge.EVENT_BUS.register(this); }

    private static int getInt(GuiContainer g, String name) {
        try { Field f = GuiContainer.class.getDeclaredField(name); f.setAccessible(true); return f.getInt(g); }
        catch (Exception e) { return 0; }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.gui;
        int x = getInt(gui, "guiLeft") + getInt(gui, "xSize") - 44;
        int y = getInt(gui, "guiTop") + 6;
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
        Container c = gui.inventorySlots;
        List slots = getSlots(c, mc.thePlayer.inventory);
        if (slots.isEmpty()) return;
        merge(c, slots, mc);
        List layout = buildLayout(c, slots);
        reorder(c, slots, layout, mc);
    }

    private static List getSlots(Container c, net.minecraft.entity.player.InventoryPlayer inv) {
        List r = new ArrayList();
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            Slot s = c.getSlot(i);
            if (s.inventory != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static void merge(Container c, List slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = c.getSlot(((Integer)slots.get(i)).intValue()).getStack();
            if (a == null || a.stackSize >= a.getMaxStackSize()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.stackSize >= a.getMaxStackSize()) break;
                ItemStack b = c.getSlot(((Integer)slots.get(j)).intValue()).getStack();
                if (b == null) continue;
                if (ItemStack.areItemsEqual(a,b) && ItemStack.areItemStackTagsEqual(a,b)) {
                    click(c, ((Integer)slots.get(j)).intValue(), mc);
                    click(c, ((Integer)slots.get(i)).intValue(), mc);
                    ItemStack held = mc.thePlayer.inventory.getItemStack();
                    if (held != null && held.stackSize > 0) click(c, ((Integer)slots.get(j)).intValue(), mc);
                }
            }
        }
    }

    private static List buildLayout(Container c, List slots) {
        Map groups = new LinkedHashMap();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = c.getSlot(((Integer)slots.get(i)).intValue()).getStack();
            if (s == null) continue;
            ItemKey key = new ItemKey(s);
            List g = (List) groups.get(key);
            if (g == null) { g = new ArrayList(); groups.put(key, g); }
            g.add(s.copy());
        }
        List r = new ArrayList();
        for (Object v : groups.values()) r.addAll((List)v);
        while (r.size() < slots.size()) r.add(null);
        return r;
    }

    private static void reorder(Container c, List slots, List layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = c.getSlot(((Integer)slots.get(i)).intValue()).getStack();
            ItemStack des = (ItemStack) layout.get(i);
            if (match(cur, des)) continue;
            int from = find(c, slots, i+1, des);
            if (from == -1) continue;
            swap(c, ((Integer)slots.get(i)).intValue(), ((Integer)slots.get(from)).intValue(), mc);
        }
    }

    private static int find(Container c, List slots, int start, ItemStack t) {
        for (int i = start; i < slots.size(); i++)
            if (match(c.getSlot(((Integer)slots.get(i)).intValue()).getStack(), t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.stackSize != b.stackSize) return false;
        return ItemStack.areItemsEqual(a,b) && ItemStack.areItemStackTagsEqual(a,b);
    }

    private static void swap(Container c, int a, int b, Minecraft mc) {
        click(c,a,mc); click(c,b,mc);
        ItemStack h = mc.thePlayer.inventory.getItemStack();
        if (h != null && h.stackSize > 0) click(c,a,mc);
    }

    private static void click(Container c, int slot, Minecraft mc) {
        if (mc.thePlayer == null || mc.playerController == null) return;
        mc.playerController.windowClick(c.windowId, slot, 0, ClickType.PICKUP, mc.thePlayer);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item; final int meta;
        final net.minecraft.nbt.NBTTagCompound tag; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem(); meta = s.getMetadata();
            net.minecraft.nbt.NBTTagCompound raw = s.getTagCompound();
            tag = raw != null ? (net.minecraft.nbt.NBTTagCompound) raw.copy() : null;
            hash = Objects.hash(item, Integer.valueOf(meta), tag);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item==k.item && meta==k.meta && Objects.equals(tag,k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# --- 1.12.2 Forge ---
# guiLeft/xSize/guiTop protected → reflection
# getSlotUnderMouse() doesn't exist → skip check
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mod(modid = SortChestMod.MOD_ID, name = "Sort Chest", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class SortChestMod {
    public static final String MOD_ID = "sortchest";
    public SortChestMod() { MinecraftForge.EVENT_BUS.register(this); }

    private static int getInt(GuiContainer g, String name) {
        try { Field f = GuiContainer.class.getDeclaredField(name); f.setAccessible(true); return f.getInt(g); }
        catch (Exception e) { return 0; }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.getGui();
        int x = getInt(gui,"guiLeft") + getInt(gui,"xSize") - 44;
        int y = getInt(gui,"guiTop") + 6;
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
        Container c = gui.inventorySlots;
        List<Integer> slots = getSlots(c, mc.player.inventory);
        if (slots.isEmpty()) return;
        merge(c, slots, mc);
        List<ItemStack> layout = buildLayout(c, slots);
        reorder(c, slots, layout, mc);
    }

    private static List<Integer> getSlots(Container c, net.minecraft.entity.player.InventoryPlayer inv) {
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            if (c.getSlot(i).inventory != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static void merge(Container c, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = c.getSlot(slots.get(i)).getStack();
            if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.getCount() >= a.getMaxStackSize()) break;
                ItemStack b = c.getSlot(slots.get(j)).getStack();
                if (b.isEmpty()) continue;
                if (ItemStack.areItemsEqual(a,b) && ItemStack.areItemStackTagsEqual(a,b)) {
                    click(c, slots.get(j), mc); click(c, slots.get(i), mc);
                    if (!mc.player.inventory.getItemStack().isEmpty()) click(c, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(Container c, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = c.getSlot(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> g = groups.get(key);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(key,g); }
            g.add(s.copy());
        }
        List<ItemStack> r = new ArrayList<ItemStack>();
        for (List<ItemStack> g : groups.values()) r.addAll(g);
        while (r.size() < slots.size()) r.add(ItemStack.EMPTY);
        return r;
    }

    private static void reorder(Container c, List<Integer> slots, List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = c.getSlot(slots.get(i)).getStack();
            ItemStack des = layout.get(i);
            if (match(cur,des)) continue;
            int from = find(c, slots, i+1, des);
            if (from == -1) continue;
            swap(c, slots.get(i), slots.get(from), mc);
        }
    }

    private static int find(Container c, List<Integer> slots, int start, ItemStack t) {
        for (int i = start; i < slots.size(); i++)
            if (match(c.getSlot(slots.get(i)).getStack(), t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.areItemsEqual(a,b) && ItemStack.areItemStackTagsEqual(a,b);
    }

    private static void swap(Container c, int a, int b, Minecraft mc) {
        click(c,a,mc); click(c,b,mc);
        if (!mc.player.inventory.getItemStack().isEmpty()) click(c,a,mc);
    }

    private static void click(Container c, int slot, Minecraft mc) {
        if (mc.player == null || mc.playerController == null) return;
        mc.playerController.windowClick(c.windowId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item; final int meta;
        final net.minecraft.nbt.NBTTagCompound tag; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem(); meta = s.getMetadata();
            tag = s.getTagCompound() != null ? s.getTagCompound().copy() : null;
            hash = Objects.hash(item, Integer.valueOf(meta), tag);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item==k.item && meta==k.meta && Objects.equals(tag,k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# --- 1.16.5 Forge ---
# Old MCP package names: net.minecraft.client.gui.screen, net.minecraft.inventory.container
# getContainer() not getMenu(), getDraggedStack() not getCarried()
# isSameItemSameTags doesn't exist → use areItemsEqual + tag check
# CompoundNBT not CompoundTag, getTag() returns CompoundNBT
# new Button(x,y,w,h,text,handler) constructor
# GuiScreenEvent.InitGuiEvent.Post (not ScreenEvent)
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
    public SortChestMod() { MinecraftForge.EVENT_BUS.register(this); }

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
                new StringTextComponent("Sort"), btn -> sortContainer(cs)));
    }

    private static void sortContainer(ContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        Container menu = screen.getMenu();
        if (!menu.getDraggedStack().isEmpty()) return;
        List<Integer> slots = getSlots(menu, mc.player.inventory);
        if (slots.isEmpty()) return;
        merge(menu, slots, mc);
        if (!menu.getDraggedStack().isEmpty()) return;
        List<ItemStack> layout = buildLayout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> getSlots(Container menu, net.minecraft.entity.player.PlayerInventory inv) {
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return ItemStack.isSameItem(a,b) && ItemStack.tagMatches(a,b);
    }

    private static void merge(Container menu, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = menu.slots.get(slots.get(i)).getItem();
            if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.getCount() >= a.getMaxStackSize()) break;
                ItemStack b = menu.slots.get(slots.get(j)).getItem();
                if (b.isEmpty()) continue;
                if (sameItem(a,b)) {
                    click(menu, slots.get(j), mc); click(menu, slots.get(i), mc);
                    if (!menu.getDraggedStack().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(Container menu, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> g = groups.get(key);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(key,g); }
            g.add(s.copy());
        }
        List<ItemStack> r = new ArrayList<ItemStack>();
        for (List<ItemStack> g : groups.values()) r.addAll(g);
        while (r.size() < slots.size()) r.add(ItemStack.EMPTY);
        return r;
    }

    private static void reorder(Container menu, List<Integer> slots, List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = menu.slots.get(slots.get(i)).getItem();
            ItemStack des = layout.get(i);
            if (match(cur,des)) continue;
            int from = find(menu, slots, i+1, des);
            if (from == -1) continue;
            swap(menu, slots.get(i), slots.get(from), mc);
        }
    }

    private static int find(Container menu, List<Integer> slots, int start, ItemStack t) {
        for (int i = start; i < slots.size(); i++)
            if (match(menu.slots.get(slots.get(i)).getItem(), t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return sameItem(a,b);
    }

    private static void swap(Container menu, int a, int b, Minecraft mc) {
        click(menu,a,mc); click(menu,b,mc);
        if (!menu.getDraggedStack().isEmpty()) click(menu,a,mc);
    }

    private static void click(Container menu, int slot, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final net.minecraft.nbt.CompoundNBT tag; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem();
            tag = s.getTag() != null ? s.getTag().copy() : null;
            hash = Objects.hash(item, tag);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item==k.item && Objects.equals(tag,k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# --- 1.17.1 Forge ---
# ScreenEvent.InitScreenEvent.Post (not Init.Post — that's 1.18+)
# TranslatableComponent still exists
# new Button(x,y,w,h,text,handler) — builder not available yet
# net.minecraft.client.gui.screens (new package names from 1.17)
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
    public SortChestMod() { MinecraftForge.EVENT_BUS.register(this); }

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
        List<Integer> slots = getSlots(menu, mc.player.getInventory());
        if (slots.isEmpty()) return;
        merge(menu, slots, mc);
        if (!menu.getCarried().isEmpty()) return;
        List<ItemStack> layout = buildLayout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> getSlots(AbstractContainerMenu menu,
            net.minecraft.world.entity.player.Inventory inv) {
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static void merge(AbstractContainerMenu menu, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = menu.slots.get(slots.get(i)).getItem();
            if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.getCount() >= a.getMaxStackSize()) break;
                ItemStack b = menu.slots.get(slots.get(j)).getItem();
                if (b.isEmpty()) continue;
                if (ItemStack.isSameItemSameTags(a,b)) {
                    click(menu, slots.get(j), mc); click(menu, slots.get(i), mc);
                    if (!menu.getCarried().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(AbstractContainerMenu menu, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> g = groups.get(key);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(key,g); }
            g.add(s.copy());
        }
        List<ItemStack> r = new ArrayList<ItemStack>();
        for (List<ItemStack> g : groups.values()) r.addAll(g);
        while (r.size() < slots.size()) r.add(ItemStack.EMPTY);
        return r;
    }

    private static void reorder(AbstractContainerMenu menu, List<Integer> slots,
            List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = menu.slots.get(slots.get(i)).getItem();
            ItemStack des = layout.get(i);
            if (match(cur,des)) continue;
            int from = find(menu, slots, i+1, des);
            if (from == -1) continue;
            swap(menu, slots.get(i), slots.get(from), mc);
        }
    }

    private static int find(AbstractContainerMenu menu, List<Integer> slots, int start, ItemStack t) {
        for (int i = start; i < slots.size(); i++)
            if (match(menu.slots.get(slots.get(i)).getItem(), t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.isSameItemSameTags(a,b);
    }

    private static void swap(AbstractContainerMenu menu, int a, int b, Minecraft mc) {
        click(menu,a,mc); click(menu,b,mc);
        if (!menu.getCarried().isEmpty()) click(menu,a,mc);
    }

    private static void click(AbstractContainerMenu menu, int slot, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.world.item.Item item;
        final net.minecraft.nbt.CompoundTag tag; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem();
            tag = s.getTag() != null ? s.getTag().copy() : null;
            hash = Objects.hash(item, tag);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item==k.item && Objects.equals(tag,k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# --- 1.18.x Forge ---
# ScreenEvent.Init.Post (renamed from InitScreenEvent.Post in 1.18)
# TranslatableComponent still exists in 1.18
# new Button(x,y,w,h,text,handler) still works in 1.18
SRC_118_FORGE = SRC_1171_FORGE.replace(
    "ScreenEvent.InitScreenEvent.Post",
    "ScreenEvent.Init.Post"
)

# --- 1.19.x Forge ---
# ScreenEvent.Init.Post
# TranslatableComponent REMOVED in 1.19.4 → Component.translatable()
# new Button(x,y,w,h,text,handler) still works in 1.19
SRC_119_FORGE = SRC_118_FORGE.replace(
    "import net.minecraft.network.chat.TranslatableComponent;",
    "import net.minecraft.network.chat.Component;"
).replace(
    "new TranslatableComponent(\"sortchest.button.sort\")",
    "Component.translatable(\"sortchest.button.sort\")"
)

# --- 1.20.1-1.20.4 Forge --- (already worked, keep as-is)
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
    public SortChestMod() { MinecraftForge.EVENT_BUS.register(this); }

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
                btn -> sortContainer(cs)).pos(x,y).size(40,14).build());
    }

    private static void sortContainer(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;
        List<Integer> slots = getSlots(menu, mc.player.getInventory());
        if (slots.isEmpty()) return;
        merge(menu, slots, mc);
        if (!menu.getCarried().isEmpty()) return;
        List<ItemStack> layout = buildLayout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> getSlots(AbstractContainerMenu menu,
            net.minecraft.world.entity.player.Inventory inv) {
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static void merge(AbstractContainerMenu menu, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = menu.slots.get(slots.get(i)).getItem();
            if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.getCount() >= a.getMaxStackSize()) break;
                ItemStack b = menu.slots.get(slots.get(j)).getItem();
                if (b.isEmpty()) continue;
                if (ItemStack.isSameItemSameTags(a,b)) {
                    click(menu, slots.get(j), mc); click(menu, slots.get(i), mc);
                    if (!menu.getCarried().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(AbstractContainerMenu menu, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> g = groups.get(key);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(key,g); }
            g.add(s.copy());
        }
        List<ItemStack> r = new ArrayList<ItemStack>();
        for (List<ItemStack> g : groups.values()) r.addAll(g);
        while (r.size() < slots.size()) r.add(ItemStack.EMPTY);
        return r;
    }

    private static void reorder(AbstractContainerMenu menu, List<Integer> slots,
            List<ItemStack> layout, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = menu.slots.get(slots.get(i)).getItem();
            ItemStack des = layout.get(i);
            if (match(cur,des)) continue;
            int from = find(menu, slots, i+1, des);
            if (from == -1) continue;
            swap(menu, slots.get(i), slots.get(from), mc);
        }
    }

    private static int find(AbstractContainerMenu menu, List<Integer> slots, int start, ItemStack t) {
        for (int i = start; i < slots.size(); i++)
            if (match(menu.slots.get(slots.get(i)).getItem(), t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.isSameItemSameTags(a,b);
    }

    private static void swap(AbstractContainerMenu menu, int a, int b, Minecraft mc) {
        click(menu,a,mc); click(menu,b,mc);
        if (!menu.getCarried().isEmpty()) click(menu,a,mc);
    }

    private static void click(AbstractContainerMenu menu, int slot, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.world.item.Item item;
        final net.minecraft.nbt.CompoundTag tag; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem();
            tag = s.getTag() != null ? s.getTag().copy() : null;
            hash = Objects.hash(item, tag);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item==k.item && Objects.equals(tag,k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# --- 1.20.5-1.20.6 Forge ---
# isSameItemSameTags removed → isSameItemSameComponents
# getTag() removed → use DataComponentMap
SRC_1205_FORGE = SRC_120_FORGE.replace(
    "ItemStack.isSameItemSameTags(a,b)",
    "ItemStack.isSameItemSameComponents(a,b)"
).replace(
    "ItemStack.isSameItemSameTags(a, b)",
    "ItemStack.isSameItemSameComponents(a, b)"
).replace(
    "final net.minecraft.nbt.CompoundTag tag; final int hash;\n        ItemKey(ItemStack s) {\n            item = s.getItem();\n            tag = s.getTag() != null ? s.getTag().copy() : null;\n            hash = Objects.hash(item, tag);",
    "final net.minecraft.core.component.DataComponentMap components; final int hash;\n        ItemKey(ItemStack s) {\n            item = s.getItem();\n            components = s.getComponents();\n            hash = Objects.hash(item, components);"
).replace(
    "return item==k.item && Objects.equals(tag,k.tag);",
    "return item==k.item && Objects.equals(components,k.components);"
)

# --- 1.21.x Forge (1.21.1, 1.21.4) ---
# Same as 1.20.5+ (isSameItemSameComponents + DataComponentMap)
SRC_121_FORGE = SRC_1205_FORGE

# --- 1.21.9-1.21.11 Forge ---
# net.minecraftforge.eventbus.api moved to net.neoforged.bus.api in Forge 1.21.x
# net.minecraftforge.client.event → net.neoforged.neoforge.client.event
# net.minecraftforge.common → net.neoforged.neoforge.common
# net.minecraftforge.fml.common → net.neoforged.fml.common
SRC_12111_FORGE = SRC_121_FORGE.replace(
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

# --- NeoForge 1.20.x ---
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

# --- NeoForge 1.20.5-1.20.6 ---
SRC_1205_NEOFORGE = SRC_1205_FORGE.replace(
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

# --- NeoForge 1.21.x ---
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

# ============================================================
# FABRIC SOURCES
# ============================================================

# --- Fabric 1.16.5 (presplit → src/main/java) ---
# HandledScreen.x/y/backgroundWidth are PROTECTED → use reflection
# ButtonWidget.builder doesn't exist → new ButtonWidget(x,y,w,h,text,handler)
# ItemStack.canCombine doesn't exist → ItemStack.areItemsEqual + tag check
# getNbt() doesn't exist → getTag() returns NbtCompound
# TranslatableText still exists in 1.16.5
SRC_FABRIC_1165 = """\
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SortChestMod implements ClientModInitializer {
    public static final String MOD_ID = "sortchest";

    private static int getInt(HandledScreen<?> h, String name) {
        try { Field f = HandledScreen.class.getDeclaredField(name); f.setAccessible(true); return f.getInt(h); }
        catch (Exception e) { return 0; }
    }

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen)) return;
            HandledScreen<?> hs = (HandledScreen<?>) screen;
            int x = getInt(hs,"x") + getInt(hs,"backgroundWidth") - 44;
            int y = getInt(hs,"y") + 6;
            Screens.getButtons(screen).add(new ButtonWidget(x, y, 40, 14,
                    new TranslatableText("sortchest.button.sort"),
                    btn -> sortContainer(hs)));
        });
    }

    private static void sortContainer(HandledScreen<?> screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != screen) return;
        ScreenHandler handler = screen.getScreenHandler();
        if (!handler.getCursorStack().isEmpty()) return;
        List<Integer> slots = getSlots(handler, mc.player.getInventory());
        if (slots.isEmpty()) return;
        merge(handler, slots, mc);
        if (!handler.getCursorStack().isEmpty()) return;
        List<ItemStack> layout = buildLayout(handler, slots);
        reorder(handler, slots, layout, mc);
    }

    private static List<Integer> getSlots(ScreenHandler handler,
            net.minecraft.entity.player.PlayerInventory inv) {
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.slots.get(i).inventory != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a,b) && ItemStack.areNbtEqual(a,b);
    }

    private static void merge(ScreenHandler handler, List<Integer> slots, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = handler.slots.get(slots.get(i)).getStack();
            if (a.isEmpty() || a.getCount() >= a.getMaxCount()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.getCount() >= a.getMaxCount()) break;
                ItemStack b = handler.slots.get(slots.get(j)).getStack();
                if (b.isEmpty()) continue;
                if (sameItem(a,b)) {
                    click(handler, slots.get(j), mc); click(handler, slots.get(i), mc);
                    if (!handler.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(ScreenHandler handler, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = handler.slots.get(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> g = groups.get(key);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(key,g); }
            g.add(s.copy());
        }
        List<ItemStack> r = new ArrayList<ItemStack>();
        for (List<ItemStack> g : groups.values()) r.addAll(g);
        while (r.size() < slots.size()) r.add(ItemStack.EMPTY);
        return r;
    }

    private static void reorder(ScreenHandler handler, List<Integer> slots,
            List<ItemStack> layout, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = handler.slots.get(slots.get(i)).getStack();
            ItemStack des = layout.get(i);
            if (match(cur,des)) continue;
            int from = find(handler, slots, i+1, des);
            if (from == -1) continue;
            swap(handler, slots.get(i), slots.get(from), mc);
        }
    }

    private static int find(ScreenHandler handler, List<Integer> slots, int start, ItemStack t) {
        for (int i = start; i < slots.size(); i++)
            if (match(handler.slots.get(slots.get(i)).getStack(), t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return sameItem(a,b);
    }

    private static void swap(ScreenHandler handler, int a, int b, MinecraftClient mc) {
        click(handler,a,mc); click(handler,b,mc);
        if (!handler.getCursorStack().isEmpty()) click(handler,a,mc);
    }

    private static void click(ScreenHandler handler, int slot, MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final net.minecraft.nbt.NbtCompound tag; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem();
            tag = s.getTag() != null ? s.getTag().copy() : null;
            hash = Objects.hash(item, tag);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item==k.item && Objects.equals(tag,k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# --- Fabric 1.17.1, 1.18.2 (presplit → src/main/java) ---
# HandledScreen.x/y/backgroundWidth still protected → reflection
# ButtonWidget.builder doesn't exist → new ButtonWidget
# ItemStack.canCombine doesn't exist → areItemsEqual + tag
# TranslatableText still exists in 1.17/1.18
SRC_FABRIC_117_118 = SRC_FABRIC_1165  # same API

# --- Fabric 1.19.4 (presplit → src/main/java) ---
# TranslatableText REMOVED → Text.translatable()
# HandledScreen fields still protected → reflection
# ButtonWidget.builder still not available → new ButtonWidget
# ItemStack.canCombine still doesn't exist → areItemsEqual + tag
SRC_FABRIC_119 = SRC_FABRIC_1165.replace(
    "import net.minecraft.text.TranslatableText;",
    "import net.minecraft.text.Text;"
).replace(
    "new TranslatableText(\"sortchest.button.sort\")",
    "Text.translatable(\"sortchest.button.sort\")"
)

# --- Fabric 1.20.x (fabric_split → src/CLIENT/java) ---
# HandledScreen.x/y/backgroundWidth are PUBLIC in 1.20+
# ButtonWidget.builder() available
# ItemStack.canCombine() available
# getNbt() available
# Text.translatable()
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
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
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
        List<Integer> slots = getSlots(handler, mc.player.getInventory());
        if (slots.isEmpty()) return;
        merge(handler, slots, mc);
        if (!handler.getCursorStack().isEmpty()) return;
        List<ItemStack> layout = buildLayout(handler, slots);
        reorder(handler, slots, layout, mc);
    }

    private static List<Integer> getSlots(ScreenHandler handler,
            net.minecraft.entity.player.PlayerInventory inv) {
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.slots.get(i).inventory != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static void merge(ScreenHandler handler, List<Integer> slots, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = handler.slots.get(slots.get(i)).getStack();
            if (a.isEmpty() || a.getCount() >= a.getMaxCount()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.getCount() >= a.getMaxCount()) break;
                ItemStack b = handler.slots.get(slots.get(j)).getStack();
                if (b.isEmpty()) continue;
                if (ItemStack.canCombine(a,b)) {
                    click(handler, slots.get(j), mc); click(handler, slots.get(i), mc);
                    if (!handler.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> buildLayout(ScreenHandler handler, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = handler.slots.get(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey key = new ItemKey(s);
            List<ItemStack> g = groups.get(key);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(key,g); }
            g.add(s.copy());
        }
        List<ItemStack> r = new ArrayList<ItemStack>();
        for (List<ItemStack> g : groups.values()) r.addAll(g);
        while (r.size() < slots.size()) r.add(ItemStack.EMPTY);
        return r;
    }

    private static void reorder(ScreenHandler handler, List<Integer> slots,
            List<ItemStack> layout, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = handler.slots.get(slots.get(i)).getStack();
            ItemStack des = layout.get(i);
            if (match(cur,des)) continue;
            int from = find(handler, slots, i+1, des);
            if (from == -1) continue;
            swap(handler, slots.get(i), slots.get(from), mc);
        }
    }

    private static int find(ScreenHandler handler, List<Integer> slots, int start, ItemStack t) {
        for (int i = start; i < slots.size(); i++)
            if (match(handler.slots.get(slots.get(i)).getStack(), t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemStack.canCombine(a,b);
    }

    private static void swap(ScreenHandler handler, int a, int b, MinecraftClient mc) {
        click(handler,a,mc); click(handler,b,mc);
        if (!handler.getCursorStack().isEmpty()) click(handler,a,mc);
    }

    private static void click(ScreenHandler handler, int slot, MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final net.minecraft.nbt.NbtCompound tag; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem();
            tag = s.getNbt() != null ? s.getNbt().copy() : null;
            hash = Objects.hash(item, tag);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item==k.item && Objects.equals(tag,k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# --- Fabric 1.21.x (fabric_split → src/CLIENT/java) ---
# areItemsAndComponentsEqual replaces canCombine (1.20.5+)
# getNbt() removed → getComponents()
# net.minecraft.component.ComponentMap
SRC_FABRIC_121 = SRC_FABRIC_120.replace(
    "ItemStack.canCombine(a,b)",
    "ItemStack.areItemsAndComponentsEqual(a,b)"
).replace(
    "final net.minecraft.nbt.NbtCompound tag; final int hash;\n        ItemKey(ItemStack s) {\n            item = s.getItem();\n            tag = s.getNbt() != null ? s.getNbt().copy() : null;\n            hash = Objects.hash(item, tag);",
    "final net.minecraft.component.ComponentMap components; final int hash;\n        ItemKey(ItemStack s) {\n            item = s.getItem();\n            components = s.getComponents();\n            hash = Objects.hash(item, components);"
).replace(
    "return item==k.item && Objects.equals(tag,k.tag);",
    "return item==k.item && Objects.equals(components,k.components);"
)

# ============================================================
# BUNDLE TARGETS
# is_split=True → src/client/java (fabric_split adapter, 1.20+)
# is_split=False → src/main/java (fabric_presplit or forge)
# ============================================================
targets = [
    # (folder, java_src, loader, mc_ver, is_fabric, is_split, fabric_mc_dep)
    ("SortChest189Forge",       SRC_189_FORGE,       "forge",     "1.8.9",    False, False, None),
    ("SortChest1122Forge",      SRC_1122_FORGE,      "forge",     "1.12.2",   False, False, None),
    ("SortChest1165Forge",      SRC_1165_FORGE,      "forge",     "1.16.5",   False, False, None),
    ("SortChest1165Fabric",     SRC_FABRIC_1165,     "fabric",    "1.16.5",   True,  False, ">=1.16.5 <1.17"),
    ("SortChest1171Forge",      SRC_1171_FORGE,      "forge",     "1.17.1",   False, False, None),
    ("SortChest1171Fabric",     SRC_FABRIC_117_118,  "fabric",    "1.17.1",   True,  False, ">=1.17 <1.18"),
    ("SortChest1182Forge",      SRC_118_FORGE,       "forge",     "1.18.2",   False, False, None),
    ("SortChest1182Fabric",     SRC_FABRIC_117_118,  "fabric",    "1.18.2",   True,  False, ">=1.18 <1.19"),
    ("SortChest1194Forge",      SRC_119_FORGE,       "forge",     "1.19.4",   False, False, None),
    ("SortChest1194Fabric",     SRC_FABRIC_119,      "fabric",    "1.19.4",   True,  False, ">=1.19 <1.20"),
    ("SortChest1201Forge",      SRC_120_FORGE,       "forge",     "1.20.1",   False, False, None),
    ("SortChest1201Fabric",     SRC_FABRIC_120,      "fabric",    "1.20.1",   True,  True,  ">=1.20.1 <1.20.2"),
    ("SortChest1204Forge",      SRC_120_FORGE,       "forge",     "1.20.4",   False, False, None),
    ("SortChest1204Fabric",     SRC_FABRIC_120,      "fabric",    "1.20.4",   True,  True,  ">=1.20.4 <1.20.5"),
    ("SortChest1206Forge",      SRC_1205_FORGE,      "forge",     "1.20.6",   False, False, None),
    ("SortChest1206Fabric",     SRC_FABRIC_120,      "fabric",    "1.20.6",   True,  True,  ">=1.20.6 <1.21"),
    ("SortChest1202NeoForge",   SRC_120_NEOFORGE,    "neoforge",  "1.20.2",   False, False, None),
    ("SortChest1204NeoForge",   SRC_120_NEOFORGE,    "neoforge",  "1.20.4",   False, False, None),
    ("SortChest1206NeoForge",   SRC_1205_NEOFORGE,   "neoforge",  "1.20.6",   False, False, None),
    ("SortChest121Forge",       SRC_121_FORGE,       "forge",     "1.21.1",   False, False, None),
    ("SortChest121Fabric",      SRC_FABRIC_121,      "fabric",    "1.21.1",   True,  True,  ">=1.21 <1.21.2"),
    ("SortChest121NeoForge",    SRC_121_NEOFORGE,    "neoforge",  "1.21.1",   False, False, None),
    ("SortChest1214Forge",      SRC_121_FORGE,       "forge",     "1.21.4",   False, False, None),
    ("SortChest1214Fabric",     SRC_FABRIC_121,      "fabric",    "1.21.4",   True,  True,  ">=1.21.2 <1.21.9"),
    ("SortChest1214NeoForge",   SRC_121_NEOFORGE,    "neoforge",  "1.21.4",   False, False, None),
    ("SortChest12111Forge",     SRC_12111_FORGE,     "forge",     "1.21.11",  False, False, None),
    ("SortChest12111Fabric",    SRC_FABRIC_121,      "fabric",    "1.21.11",  True,  True,  ">=1.21.9 <1.22"),
    ("SortChest12111NeoForge",  SRC_121_NEOFORGE,    "neoforge",  "1.21.11",  False, False, None),
]

# Generate
if BUNDLE.exists():
    shutil.rmtree(BUNDLE)

for (folder, java_src, loader, mc_ver, is_fabric, is_split, fab_dep) in targets:
    base = BUNDLE / folder
    write(base / "mod.txt", mod_txt())
    write(base / "version.txt", version_txt(mc_ver, loader))

    if is_fabric and is_split:
        # fabric_split: source in src/client/java, resources in src/client/resources
        write(base / CLIENT_JAVA, java_src)
        write(base / LANG_CLIENT, lang_json())
        write(base / FAB_CLIENT_JSON, fabric_mod_json(fab_dep or "*"))
    elif is_fabric:
        # fabric_presplit: source in src/main/java, resources in src/main/resources
        write(base / JAVA_PATH, java_src)
        write(base / LANG_MAIN, lang_json())
        write(base / FAB_MAIN_JSON, fabric_mod_json(fab_dep or "*"))
    else:
        # Forge / NeoForge: src/main/java
        write(base / JAVA_PATH, java_src)
        write(base / LANG_MAIN, lang_json())

print(f"Generated {len(targets)} targets under {BUNDLE}")

# Create zip
zip_path = ROOT / "incoming" / "sort-chest-all-versions.zip"
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for path in sorted(BUNDLE.rglob("*")):
        if not path.is_file(): continue
        rel = path.relative_to(BUNDLE)
        if len(rel.parts) < 2: continue
        zf.write(path, rel)
print(f"Zip: {zip_path}")

# Sanity check
r = subprocess.run(
    ["python3", "build_mods.py", "prepare",
     "--zip-path", str(zip_path),
     "--manifest", "version-manifest.json",
     "--output-dir", "/tmp/prepare-sanity"],
    capture_output=True, text=True, cwd=str(ROOT)
)
if r.returncode == 0:
    matrix = json.loads(r.stdout)
    print(f"Prepare OK — {len(matrix.get('include',[]))} build targets")
else:
    print(f"Prepare FAILED:\n{r.stderr[:800]}")
