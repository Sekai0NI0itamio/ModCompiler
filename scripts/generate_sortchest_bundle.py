#!/usr/bin/env python3
"""
Generates the Sort Chest all-versions bundle.
Run: python3 scripts/generate_sortchest_bundle.py

Mapping reference:
  Fabric 1.16.5-1.20.x  → Yarn mappings  (HandledScreen, ScreenHandler, etc.)
  Fabric 1.21.x         → Mojang mappings (AbstractContainerScreen, AbstractContainerMenu, etc.)
  Forge/NeoForge all    → Mojang mappings always
"""
import shutil, subprocess, zipfile, json
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

PKG = GROUP.replace('.', '/')
JAVA_MAIN   = f"src/main/java/{PKG}/SortChestMod.java"
JAVA_CLIENT = f"src/client/java/{PKG}/SortChestMod.java"
LANG_MAIN   = f"src/main/resources/assets/{MOD_ID}/lang/en_us.json"
FAB_JSON    = "src/main/resources/fabric.mod.json"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt() -> str:
    return (f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
            f"group={GROUP}\nentrypoint_class={ENTRYPOINT}\n"
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
# FORGE / NEOFORGE SOURCES  (all use Mojang mappings)
# ============================================================

# 1.8.9 — no ClickType, no diamonds/lambdas, reflection for protected fields
SRC_189_FORGE = """\
package net.itamio.sortchest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
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

@Mod(modid=SortChestMod.MOD_ID,name="Sort Chest",version="1.0.0",clientSideOnly=true,acceptedMinecraftVersions="[1.8.9]")
public class SortChestMod {
    public static final String MOD_ID = "sortchest";
    public SortChestMod() { MinecraftForge.EVENT_BUS.register(this); }

    private static int gi(GuiContainer g, String n) {
        try { Field f=GuiContainer.class.getDeclaredField(n); f.setAccessible(true); return f.getInt(g); }
        catch(Exception e) { return 0; }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (!(e.gui instanceof GuiContainer)) return;
        GuiContainer gui=(GuiContainer)e.gui;
        e.buttonList.add(new GuiButton(9001, gi(gui,"guiLeft")+gi(gui,"xSize")-44, gi(gui,"guiTop")+6, 40, 14, "Sort"));
    }

    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (!(e.gui instanceof GuiContainer)) return;
        if (e.button.id != 9001) return;
        e.setCanceled(true);
        sort((GuiContainer)e.gui);
    }

    private static void sort(GuiContainer gui) {
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.thePlayer==null||mc.playerController==null) return;
        if (mc.currentScreen!=gui) return;
        Container c=gui.inventorySlots;
        List slots=slots(c,mc.thePlayer.inventory);
        if (slots.isEmpty()) return;
        merge(c,slots,mc);
        List layout=layout(c,slots);
        reorder(c,slots,layout,mc);
    }

    private static List slots(Container c, net.minecraft.entity.player.InventoryPlayer inv) {
        List r=new ArrayList();
        for (int i=0;i<c.inventorySlots.size();i++) { Slot s=c.getSlot(i); if(s.inventory!=inv) r.add(Integer.valueOf(i)); }
        return r;
    }

    private static void merge(Container c, List slots, Minecraft mc) {
        for (int i=0;i<slots.size();i++) {
            ItemStack a=c.getSlot(((Integer)slots.get(i)).intValue()).getStack();
            if (a==null||a.stackSize>=a.getMaxStackSize()) continue;
            for (int j=i+1;j<slots.size();j++) {
                if (a.stackSize>=a.getMaxStackSize()) break;
                ItemStack b=c.getSlot(((Integer)slots.get(j)).intValue()).getStack();
                if (b==null) continue;
                if (ItemStack.areItemsEqual(a,b)&&ItemStack.areItemStackTagsEqual(a,b)) {
                    click(c,((Integer)slots.get(j)).intValue(),mc);
                    click(c,((Integer)slots.get(i)).intValue(),mc);
                    ItemStack h=mc.thePlayer.inventory.getItemStack();
                    if (h!=null&&h.stackSize>0) click(c,((Integer)slots.get(j)).intValue(),mc);
                }
            }
        }
    }

    private static List layout(Container c, List slots) {
        Map groups=new LinkedHashMap();
        for (int i=0;i<slots.size();i++) {
            ItemStack s=c.getSlot(((Integer)slots.get(i)).intValue()).getStack();
            if (s==null) continue;
            ItemKey k=new ItemKey(s);
            List g=(List)groups.get(k);
            if (g==null){g=new ArrayList();groups.put(k,g);}
            g.add(s.copy());
        }
        List r=new ArrayList();
        for (Object v:groups.values()) r.addAll((List)v);
        while(r.size()<slots.size()) r.add(null);
        return r;
    }

    private static void reorder(Container c, List slots, List layout, Minecraft mc) {
        for (int i=0;i<slots.size();i++) {
            ItemStack cur=c.getSlot(((Integer)slots.get(i)).intValue()).getStack();
            ItemStack des=(ItemStack)layout.get(i);
            if (match(cur,des)) continue;
            int from=find(c,slots,i+1,des);
            if (from==-1) continue;
            swap(c,((Integer)slots.get(i)).intValue(),((Integer)slots.get(from)).intValue(),mc);
        }
    }

    private static int find(Container c, List slots, int start, ItemStack t) {
        for (int i=start;i<slots.size();i++) if(match(c.getSlot(((Integer)slots.get(i)).intValue()).getStack(),t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a==null&&b==null) return true;
        if (a==null||b==null) return false;
        if (a.stackSize!=b.stackSize) return false;
        return ItemStack.areItemsEqual(a,b)&&ItemStack.areItemStackTagsEqual(a,b);
    }

    private static void swap(Container c, int a, int b, Minecraft mc) {
        click(c,a,mc); click(c,b,mc);
        ItemStack h=mc.thePlayer.inventory.getItemStack();
        if (h!=null&&h.stackSize>0) click(c,a,mc);
    }

    private static void click(Container c, int slot, Minecraft mc) {
        if (mc.thePlayer==null||mc.playerController==null) return;
        mc.playerController.windowClick(c.windowId, slot, 0, 0, mc.thePlayer);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item; final int meta;
        final net.minecraft.nbt.NBTTagCompound tag; final int hash;
        ItemKey(ItemStack s) {
            item=s.getItem(); meta=s.getMetadata();
            net.minecraft.nbt.NBTTagCompound raw=s.getTagCompound();
            tag=raw!=null?(net.minecraft.nbt.NBTTagCompound)raw.copy():null;
            hash=Objects.hash(item,Integer.valueOf(meta),tag);
        }
        public boolean equals(Object o) {
            if(!(o instanceof ItemKey)) return false;
            ItemKey k=(ItemKey)o;
            return item==k.item&&meta==k.meta&&Objects.equals(tag,k.tag);
        }
        public int hashCode(){return hash;}
    }
}
"""

# 1.12.2 — reflection for protected fields, ClickType.PICKUP exists
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

@Mod(modid=SortChestMod.MOD_ID,name="Sort Chest",version="1.0.0",clientSideOnly=true,acceptedMinecraftVersions="[1.12,1.12.2]")
public class SortChestMod {
    public static final String MOD_ID = "sortchest";
    public SortChestMod() { MinecraftForge.EVENT_BUS.register(this); }

    private static int gi(GuiContainer g, String n) {
        try { Field f=GuiContainer.class.getDeclaredField(n); f.setAccessible(true); return f.getInt(g); }
        catch(Exception e) { return 0; }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (!(e.getGui() instanceof GuiContainer)) return;
        GuiContainer gui=(GuiContainer)e.getGui();
        e.getButtonList().add(new GuiButton(9001, gi(gui,"guiLeft")+gi(gui,"xSize")-44, gi(gui,"guiTop")+6, 40, 14, "Sort"));
    }

    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (!(e.getGui() instanceof GuiContainer)) return;
        if (e.getButton().id!=9001) return;
        e.setCanceled(true);
        sort((GuiContainer)e.getGui());
    }

    private static void sort(GuiContainer gui) {
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.player==null||mc.playerController==null) return;
        if (mc.currentScreen!=gui) return;
        Container c=gui.inventorySlots;
        List<Integer> slots=slots(c,mc.player.inventory);
        if (slots.isEmpty()) return;
        merge(c,slots,mc);
        List<ItemStack> layout=layout(c,slots);
        reorder(c,slots,layout,mc);
    }

    private static List<Integer> slots(Container c, net.minecraft.entity.player.InventoryPlayer inv) {
        List<Integer> r=new ArrayList<Integer>();
        for (int i=0;i<c.inventorySlots.size();i++) { if(c.getSlot(i).inventory!=inv) r.add(Integer.valueOf(i)); }
        return r;
    }

    private static void merge(Container c, List<Integer> slots, Minecraft mc) {
        for (int i=0;i<slots.size();i++) {
            ItemStack a=c.getSlot(slots.get(i)).getStack();
            if (a.isEmpty()||a.getCount()>=a.getMaxStackSize()) continue;
            for (int j=i+1;j<slots.size();j++) {
                if (a.getCount()>=a.getMaxStackSize()) break;
                ItemStack b=c.getSlot(slots.get(j)).getStack();
                if (b.isEmpty()) continue;
                if (ItemStack.areItemsEqual(a,b)&&ItemStack.areItemStackTagsEqual(a,b)) {
                    click(c,slots.get(j),mc); click(c,slots.get(i),mc);
                    if (!mc.player.inventory.getItemStack().isEmpty()) click(c,slots.get(j),mc);
                }
            }
        }
    }

    private static List<ItemStack> layout(Container c, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups=new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i=0;i<slots.size();i++) {
            ItemStack s=c.getSlot(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey k=new ItemKey(s);
            List<ItemStack> g=groups.get(k);
            if (g==null){g=new ArrayList<ItemStack>();groups.put(k,g);}
            g.add(s.copy());
        }
        List<ItemStack> r=new ArrayList<ItemStack>();
        for (List<ItemStack> g:groups.values()) r.addAll(g);
        while(r.size()<slots.size()) r.add(ItemStack.EMPTY);
        return r;
    }

    private static void reorder(Container c, List<Integer> slots, List<ItemStack> layout, Minecraft mc) {
        for (int i=0;i<slots.size();i++) {
            ItemStack cur=c.getSlot(slots.get(i)).getStack();
            ItemStack des=layout.get(i);
            if (match(cur,des)) continue;
            int from=find(c,slots,i+1,des);
            if (from==-1) continue;
            swap(c,slots.get(i),slots.get(from),mc);
        }
    }

    private static int find(Container c, List<Integer> slots, int start, ItemStack t) {
        for (int i=start;i<slots.size();i++) if(match(c.getSlot(slots.get(i)).getStack(),t)) return i;
        return -1;
    }

    private static boolean match(ItemStack a, ItemStack b) {
        if (a.isEmpty()&&b.isEmpty()) return true;
        if (a.isEmpty()||b.isEmpty()) return false;
        if (a.getCount()!=b.getCount()) return false;
        return ItemStack.areItemsEqual(a,b)&&ItemStack.areItemStackTagsEqual(a,b);
    }

    private static void swap(Container c, int a, int b, Minecraft mc) {
        click(c,a,mc); click(c,b,mc);
        if (!mc.player.inventory.getItemStack().isEmpty()) click(c,a,mc);
    }

    private static void click(Container c, int slot, Minecraft mc) {
        if (mc.player==null||mc.playerController==null) return;
        mc.playerController.windowClick(c.windowId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item; final int meta;
        final net.minecraft.nbt.NBTTagCompound tag; final int hash;
        ItemKey(ItemStack s) {
            item=s.getItem(); meta=s.getMetadata();
            tag=s.getTagCompound()!=null?s.getTagCompound().copy():null;
            hash=Objects.hash(item,Integer.valueOf(meta),tag);
        }
        public boolean equals(Object o) {
            if(!(o instanceof ItemKey)) return false;
            ItemKey k=(ItemKey)o;
            return item==k.item&&meta==k.meta&&Objects.equals(tag,k.tag);
        }
        public int hashCode(){return hash;}
    }
}
"""

# 1.16.5 Forge — old MCP names
# getCarried() DOES exist in 1.16.5 MCP (it's the correct name)
# isSameItemSameTags DOES exist in 1.16.5 Forge (added in 1.16)
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
                new StringTextComponent("Sort"), btn -> sort(cs)));
    }

    private static void sort(ContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        Container menu = screen.getMenu();
        if (!menu.getDraggedStack().isEmpty()) return;
        List<Integer> slots = slots(menu, mc.player.inventory);
        if (slots.isEmpty()) return;
        merge(menu, slots, mc);
        if (!menu.getDraggedStack().isEmpty()) return;
        List<ItemStack> layout = layout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> slots(Container menu, net.minecraft.entity.player.PlayerInventory inv) {
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    private static void merge(Container menu, List<Integer> slots, Minecraft mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = menu.slots.get(slots.get(i)).getItem();
            if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.getCount() >= a.getMaxStackSize()) break;
                ItemStack b = menu.slots.get(slots.get(j)).getItem();
                if (b.isEmpty()) continue;
                if (same(a, b)) {
                    click(menu, slots.get(j), mc); click(menu, slots.get(i), mc);
                    if (!menu.getDraggedStack().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> layout(Container menu, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey k = new ItemKey(s);
            List<ItemStack> g = groups.get(k);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(k, g); }
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
            if (match(cur, des)) continue;
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
        return same(a, b);
    }

    private static void swap(Container menu, int a, int b, Minecraft mc) {
        click(menu, a, mc); click(menu, b, mc);
        if (!menu.getDraggedStack().isEmpty()) click(menu, a, mc);
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
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# 1.17.1 Forge — GuiScreenEvent.InitGuiEvent.Post (ScreenEvent didn't exist until 1.18)
# TranslatableComponent, new Button constructor
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
        if (!(screen instanceof AbstractContainerScreen)) return;
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int x = cs.getGuiLeft() + cs.getXSize() - 44;
        int y = cs.getGuiTop() + 6;
        event.addWidget(new Button(x, y, 40, 14,
                new TranslatableComponent("sortchest.button.sort"),
                btn -> sort(cs)));
    }

    private static void sort(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;
        List<Integer> slots = slots(menu, mc.player.getInventory());
        if (slots.isEmpty()) return;
        merge(menu, slots, mc);
        if (!menu.getCarried().isEmpty()) return;
        List<ItemStack> layout = layout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> slots(AbstractContainerMenu menu,
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
                if (ItemStack.isSameItemSameTags(a, b)) {
                    click(menu, slots.get(j), mc); click(menu, slots.get(i), mc);
                    if (!menu.getCarried().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> layout(AbstractContainerMenu menu, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey k = new ItemKey(s);
            List<ItemStack> g = groups.get(k);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(k, g); }
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
            if (match(cur, des)) continue;
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
        return ItemStack.isSameItemSameTags(a, b);
    }

    private static void swap(AbstractContainerMenu menu, int a, int b, Minecraft mc) {
        click(menu, a, mc); click(menu, b, mc);
        if (!menu.getCarried().isEmpty()) click(menu, a, mc);
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
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# 1.18.x — ScreenEvent.Init.Post (renamed in 1.18), TranslatableComponent, new Button
SRC_118_FORGE = (SRC_1171_FORGE
    .replace("import net.minecraftforge.client.event.GuiScreenEvent;",
             "import net.minecraftforge.client.event.ScreenEvent;")
    .replace("public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {\n        Screen screen = event.getGui();",
             "public void onScreenInit(ScreenEvent.Init.Post event) {\n        Screen screen = event.getScreen();")
)

# 1.19.x — Init.Post (renamed), Component.translatable, Button.builder (added in 1.19.4)
SRC_119_FORGE = (SRC_1171_FORGE
    .replace("ScreenEvent.InitScreenEvent.Post", "ScreenEvent.Init.Post")
    .replace("import net.minecraft.network.chat.TranslatableComponent;",
             "import net.minecraft.network.chat.Component;")
    .replace("new TranslatableComponent(\"sortchest.button.sort\")",
             "Component.translatable(\"sortchest.button.sort\")")
    .replace("event.addListener(new Button(x, y, 40, 14,\n                Component.translatable(\"sortchest.button.sort\"),\n                btn -> sort(cs)));",
             "event.addListener(Button.builder(Component.translatable(\"sortchest.button.sort\"),\n                btn -> sort(cs)).pos(x,y).size(40,14).build());")
)

# 1.20.1-1.20.4 Forge — Button.builder, Component.translatable (already worked)
SRC_120_FORGE = (SRC_119_FORGE
    .replace("ScreenEvent.Init.Post", "ScreenEvent.Init.Post")  # same
)

# 1.20.5-1.20.6 Forge — isSameItemSameComponents + DataComponentMap
SRC_1205_FORGE = (SRC_120_FORGE
    .replace("ItemStack.isSameItemSameTags(a, b)", "ItemStack.isSameItemSameComponents(a, b)")
    .replace(
        "final net.minecraft.nbt.CompoundTag tag; final int hash;\n        ItemKey(ItemStack s) {\n            item = s.getItem();\n            tag = s.getTag() != null ? s.getTag().copy() : null;\n            hash = Objects.hash(item, tag);",
        "final net.minecraft.core.component.DataComponentMap components; final int hash;\n        ItemKey(ItemStack s) {\n            item = s.getItem();\n            components = s.getComponents();\n            hash = Objects.hash(item, components);"
    )
    .replace("return item == k.item && Objects.equals(tag, k.tag);",
             "return item == k.item && Objects.equals(components, k.components);")
)

# 1.21.x Forge (1.21.1, 1.21.4) — same as 1.20.5+
SRC_121_FORGE = SRC_1205_FORGE

def to_neoforge(src: str) -> str:
    return (src
        .replace("import net.minecraftforge.client.event.ScreenEvent;",
                 "import net.neoforged.neoforge.client.event.ScreenEvent;")
        .replace("import net.minecraftforge.common.MinecraftForge;",
                 "import net.neoforged.neoforge.common.NeoForge;")
        .replace("import net.minecraftforge.eventbus.api.SubscribeEvent;",
                 "import net.neoforged.bus.api.SubscribeEvent;")
        .replace("import net.minecraftforge.fml.common.Mod;",
                 "import net.neoforged.fml.common.Mod;")
        .replace("MinecraftForge.EVENT_BUS.register(this);",
                 "NeoForge.EVENT_BUS.register(this);")
    )

# 1.21.11 Forge — same as 1.21.x, still uses net.minecraftforge.* (NOT NeoForge)
SRC_12111_FORGE = SRC_121_FORGE

SRC_120_NEOFORGE  = to_neoforge(SRC_120_FORGE)
SRC_1205_NEOFORGE = to_neoforge(SRC_1205_FORGE)
SRC_121_NEOFORGE  = to_neoforge(SRC_121_FORGE)

# ============================================================
# FABRIC SOURCES
# ============================================================
# Fabric 1.16.5-1.20.x  → Yarn mappings
# Fabric 1.21.x         → Mojang mappings (same package names as Forge)
# ============================================================

# Fabric 1.16.5 (Yarn, presplit → src/main/java)
# getCursorStack() → getStackInCursor() in 1.16.5
# getInventory() → inventory field
# areNbtEqual → areTagsEqual
# ButtonWidget.builder not available → new ButtonWidget
# TranslatableText
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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.TranslatableText;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SortChestMod implements ClientModInitializer {
    public static final String MOD_ID = "sortchest";

    private static int gi(HandledScreen<?> h, String n) {
        try { Field f=HandledScreen.class.getDeclaredField(n); f.setAccessible(true); return f.getInt(h); }
        catch(Exception e) { return 0; }
    }

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen)) return;
            HandledScreen<?> hs = (HandledScreen<?>) screen;
            int x = gi(hs,"x") + gi(hs,"backgroundWidth") - 44;
            int y = gi(hs,"y") + 6;
            Screens.getButtons(screen).add(new ButtonWidget(x, y, 40, 14,
                    new TranslatableText("sortchest.button.sort"),
                    btn -> sort(hs)));
        });
    }

    private static void sort(HandledScreen<?> screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != screen) return;
        ScreenHandler handler = screen.getScreenHandler();
        // In 1.16.5 cursor stack is on the player inventory
        if (!mc.player.inventory.getCursorStack().isEmpty()) return;
        List<Integer> slots = slots(handler, mc.player.inventory);
        if (slots.isEmpty()) return;
        merge(handler, slots, mc);
        if (!mc.player.inventory.getCursorStack().isEmpty()) return;
        List<ItemStack> layout = layout(handler, slots);
        reorder(handler, slots, layout, mc);
    }

    private static List<Integer> slots(ScreenHandler handler,
            net.minecraft.entity.player.PlayerInventory inv) {
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.slots.get(i).inventory != inv) r.add(Integer.valueOf(i));
        }
        return r;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b) && ItemStack.areTagsEqual(a, b);
    }

    private static void merge(ScreenHandler handler, List<Integer> slots, MinecraftClient mc) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = handler.slots.get(slots.get(i)).getStack();
            if (a.isEmpty() || a.getCount() >= a.getMaxCount()) continue;
            for (int j = i+1; j < slots.size(); j++) {
                if (a.getCount() >= a.getMaxCount()) break;
                ItemStack b = handler.slots.get(slots.get(j)).getStack();
                if (b.isEmpty()) continue;
                if (same(a, b)) {
                    click(handler, slots.get(j), mc); click(handler, slots.get(i), mc);
                    if (!mc.player.inventory.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> layout(ScreenHandler handler, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = handler.slots.get(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey k = new ItemKey(s);
            List<ItemStack> g = groups.get(k);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(k, g); }
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
            if (match(cur, des)) continue;
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
        return same(a, b);
    }

    private static void swap(ScreenHandler handler, int a, int b, MinecraftClient mc) {
        click(handler, a, mc); click(handler, b, mc);
        if (!mc.player.inventory.getCursorStack().isEmpty()) click(handler, a, mc);
    }

    private static void click(ScreenHandler handler, int slot, MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.item.Item item;
        final NbtCompound tag; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem();
            tag = s.getTag() != null ? s.getTag().copy() : null;
            hash = Objects.hash(item, tag);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# Fabric 1.17.1-1.18.2 (Yarn, presplit → src/main/java)
# getCursorStack() exists from 1.17+, getInventory() exists from 1.17+
# canCombine() for comparison (areTagsEqual doesn't exist in 1.17+)
# getNbt() for tag access (getTag() was renamed to getNbt() in 1.17)
SRC_FABRIC_117_118 = (SRC_FABRIC_1165
    .replace("if (!mc.player.inventory.getCursorStack().isEmpty()) return;",
             "if (!handler.getCursorStack().isEmpty()) return;")
    .replace("List<Integer> slots = slots(handler, mc.player.inventory);",
             "List<Integer> slots = slots(handler, mc.player.getInventory());")
    .replace("if (!mc.player.inventory.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);",
             "if (!handler.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);")
    .replace("if (!mc.player.inventory.getCursorStack().isEmpty()) click(handler, a, mc);",
             "if (!handler.getCursorStack().isEmpty()) click(handler, a, mc);")
    .replace("return ItemStack.areItemsEqual(a, b) && ItemStack.areTagsEqual(a, b);",
             "return ItemStack.canCombine(a, b);")
    .replace("tag = s.getTag() != null ? s.getTag().copy() : null;",
             "tag = s.getNbt() != null ? s.getNbt().copy() : null;")
    .replace("net.minecraft.entity.player.PlayerInventory inv",
             "net.minecraft.entity.player.PlayerInventory inv")
)

# Fabric 1.19.4 (Yarn, presplit) — Text.translatable, ButtonWidget.builder, canCombine, getNbt
SRC_FABRIC_119 = (SRC_FABRIC_117_118
    .replace("import net.minecraft.text.TranslatableText;",
             "import net.minecraft.text.Text;")
    .replace("Screens.getButtons(screen).add(new ButtonWidget(x, y, 40, 14,\n                    new TranslatableText(\"sortchest.button.sort\"),\n                    btn -> sort(hs)));",
             "Screens.getButtons(screen).add(ButtonWidget.builder(\n                    Text.translatable(\"sortchest.button.sort\"),\n                    btn -> sort(hs)).dimensions(x, y, 40, 14).build());")
)

# Fabric 1.20.x (Yarn, fabric_split → src/client/java)
# HandledScreen.x/y/backgroundWidth still protected → reflection
# ButtonWidget.builder() available, Text.translatable, canCombine, getNbt
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SortChestMod implements ClientModInitializer {
    public static final String MOD_ID = "sortchest";

    private static int gi(HandledScreen<?> h, String n) {
        try { Field f=HandledScreen.class.getDeclaredField(n); f.setAccessible(true); return f.getInt(h); }
        catch(Exception e) { return 0; }
    }

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen)) return;
            HandledScreen<?> hs = (HandledScreen<?>) screen;
            int x = gi(hs,"x") + gi(hs,"backgroundWidth") - 44;
            int y = gi(hs,"y") + 6;
            Screens.getButtons(screen).add(ButtonWidget.builder(
                    Text.translatable("sortchest.button.sort"),
                    btn -> sort(hs)).dimensions(x, y, 40, 14).build());
        });
    }

    private static void sort(HandledScreen<?> screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != screen) return;
        ScreenHandler handler = screen.getScreenHandler();
        if (!handler.getCursorStack().isEmpty()) return;
        List<Integer> slots = slots(handler, mc.player.getInventory());
        if (slots.isEmpty()) return;
        merge(handler, slots, mc);
        if (!handler.getCursorStack().isEmpty()) return;
        List<ItemStack> layout = layout(handler, slots);
        reorder(handler, slots, layout, mc);
    }

    private static List<Integer> slots(ScreenHandler handler,
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
                if (ItemStack.canCombine(a, b)) {
                    click(handler, slots.get(j), mc); click(handler, slots.get(i), mc);
                    if (!handler.getCursorStack().isEmpty()) click(handler, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> layout(ScreenHandler handler, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = handler.slots.get(slots.get(i)).getStack();
            if (s.isEmpty()) continue;
            ItemKey k = new ItemKey(s);
            List<ItemStack> g = groups.get(k);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(k, g); }
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
            if (match(cur, des)) continue;
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
        return ItemStack.canCombine(a, b);
    }

    private static void swap(ScreenHandler handler, int a, int b, MinecraftClient mc) {
        click(handler, a, mc); click(handler, b, mc);
        if (!handler.getCursorStack().isEmpty()) click(handler, a, mc);
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
            return item == k.item && Objects.equals(tag, k.tag);
        }
        public int hashCode() { return hash; }
    }
}
"""

# Fabric 1.20.5-1.20.6 (Yarn, fabric_split) — areItemsAndComponentsEqual + ComponentMap
SRC_FABRIC_1205 = (SRC_FABRIC_120
    .replace("ItemStack.canCombine(a, b)", "ItemStack.areItemsAndComponentsEqual(a, b)")
    .replace(
        "final net.minecraft.nbt.NbtCompound tag; final int hash;\n        ItemKey(ItemStack s) {\n            item = s.getItem();\n            tag = s.getNbt() != null ? s.getNbt().copy() : null;\n            hash = Objects.hash(item, tag);",
        "final net.minecraft.component.ComponentMap components; final int hash;\n        ItemKey(ItemStack s) {\n            item = s.getItem();\n            components = s.getComponents();\n            hash = Objects.hash(item, components);"
    )
    .replace("return item == k.item && Objects.equals(tag, k.tag);",
             "return item == k.item && Objects.equals(components, k.components);")
)

# Fabric 1.21.x (Mojang mappings, fabric_split → src/client/java)
# Uses SAME package names as Forge (AbstractContainerScreen, AbstractContainerMenu, etc.)
# But uses Fabric API for screen events (ScreenEvents, Screens)
# ButtonWidget → net.minecraft.client.gui.components.Button (Mojang name)
# ScreenHandler → AbstractContainerMenu (Mojang name)
# SlotActionType → ClickType (Mojang name)
# PlayerInventory → Inventory (Mojang name)
SRC_FABRIC_121 = """\
package net.itamio.sortchest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SortChestMod implements ClientModInitializer {
    public static final String MOD_ID = "sortchest";

    private static int gi(AbstractContainerScreen<?> h, String n) {
        try { java.lang.reflect.Field f=AbstractContainerScreen.class.getDeclaredField(n); f.setAccessible(true); return f.getInt(h); }
        catch(Exception e) { return 0; }
    }

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen)) return;
            AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
            int x = gi(cs,"leftPos") + gi(cs,"imageWidth") - 44;
            int y = gi(cs,"topPos") + 6;
            Screens.getButtons(screen).add(Button.builder(
                    Component.translatable("sortchest.button.sort"),
                    btn -> sort(cs)).pos(x, y).size(40, 14).build());
        });
    }

    private static void sort(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen != screen) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;
        List<Integer> slots = slots(menu, mc.player.getInventory());
        if (slots.isEmpty()) return;
        merge(menu, slots, mc);
        if (!menu.getCarried().isEmpty()) return;
        List<ItemStack> layout = layout(menu, slots);
        reorder(menu, slots, layout, mc);
    }

    private static List<Integer> slots(AbstractContainerMenu menu,
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
                if (ItemStack.isSameItemSameComponents(a, b)) {
                    click(menu, slots.get(j), mc); click(menu, slots.get(i), mc);
                    if (!menu.getCarried().isEmpty()) click(menu, slots.get(j), mc);
                }
            }
        }
    }

    private static List<ItemStack> layout(AbstractContainerMenu menu, List<Integer> slots) {
        Map<ItemKey,List<ItemStack>> groups = new LinkedHashMap<ItemKey,List<ItemStack>>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = menu.slots.get(slots.get(i)).getItem();
            if (s.isEmpty()) continue;
            ItemKey k = new ItemKey(s);
            List<ItemStack> g = groups.get(k);
            if (g == null) { g = new ArrayList<ItemStack>(); groups.put(k, g); }
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
            if (match(cur, des)) continue;
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
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private static void swap(AbstractContainerMenu menu, int a, int b, Minecraft mc) {
        click(menu, a, mc); click(menu, b, mc);
        if (!menu.getCarried().isEmpty()) click(menu, a, mc);
    }

    private static void click(AbstractContainerMenu menu, int slot, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    static final class ItemKey {
        final net.minecraft.world.item.Item item;
        final net.minecraft.core.component.DataComponentMap components; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem();
            components = s.getComponents();
            hash = Objects.hash(item, components);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item == k.item && Objects.equals(components, k.components);
        }
        public int hashCode() { return hash; }
    }
}
"""

# ============================================================
# BUNDLE TARGETS
# ============================================================
targets = [
    # (folder, java_src, loader, mc_ver, is_fabric, is_split, fabric_mc_dep)
    ("SortChest189Forge",       SRC_189_FORGE,        "forge",    "1.8.9",    False, False, None),
    ("SortChest1122Forge",      SRC_1122_FORGE,       "forge",    "1.12.2",   False, False, None),
    ("SortChest1165Forge",      SRC_1165_FORGE,       "forge",    "1.16.5",   False, False, None),
    ("SortChest1165Fabric",     SRC_FABRIC_1165,      "fabric",   "1.16.5",   True,  False, ">=1.16.5 <1.17"),
    ("SortChest1171Forge",      SRC_1171_FORGE,       "forge",    "1.17.1",   False, False, None),
    ("SortChest1171Fabric",     SRC_FABRIC_117_118,   "fabric",   "1.17.1",   True,  False, ">=1.17 <1.18"),
    ("SortChest1182Forge",      SRC_118_FORGE,        "forge",    "1.18.2",   False, False, None),
    ("SortChest1182Fabric",     SRC_FABRIC_117_118,   "fabric",   "1.18.2",   True,  False, ">=1.18 <1.19"),
    ("SortChest1194Forge",      SRC_119_FORGE,        "forge",    "1.19.4",   False, False, None),
    ("SortChest1194Fabric",     SRC_FABRIC_119,       "fabric",   "1.19.4",   True,  False, ">=1.19 <1.20"),
    ("SortChest1201Forge",      SRC_120_FORGE,        "forge",    "1.20.1",   False, False, None),
    ("SortChest1201Fabric",     SRC_FABRIC_120,       "fabric",   "1.20.1",   True,  True,  ">=1.20.1 <1.20.2"),
    ("SortChest1204Forge",      SRC_120_FORGE,        "forge",    "1.20.4",   False, False, None),
    ("SortChest1204Fabric",     SRC_FABRIC_120,       "fabric",   "1.20.4",   True,  True,  ">=1.20.4 <1.20.5"),
    ("SortChest1206Forge",      SRC_1205_FORGE,       "forge",    "1.20.6",   False, False, None),
    ("SortChest1206Fabric",     SRC_FABRIC_1205,      "fabric",   "1.20.6",   True,  True,  ">=1.20.6 <1.21"),
    ("SortChest1202NeoForge",   SRC_120_NEOFORGE,     "neoforge", "1.20.2",   False, False, None),
    ("SortChest1204NeoForge",   SRC_120_NEOFORGE,     "neoforge", "1.20.4",   False, False, None),
    ("SortChest1206NeoForge",   SRC_1205_NEOFORGE,    "neoforge", "1.20.6",   False, False, None),
    ("SortChest121Forge",       SRC_121_FORGE,        "forge",    "1.21.1",   False, False, None),
    ("SortChest121Fabric",      SRC_FABRIC_121,       "fabric",   "1.21.1",   True,  True,  ">=1.21 <1.21.2"),
    ("SortChest121NeoForge",    SRC_121_NEOFORGE,     "neoforge", "1.21.1",   False, False, None),
    ("SortChest1214Forge",      SRC_121_FORGE,        "forge",    "1.21.4",   False, False, None),
    ("SortChest1214Fabric",     SRC_FABRIC_121,       "fabric",   "1.21.4",   True,  True,  ">=1.21.2 <1.21.9"),
    ("SortChest1214NeoForge",   SRC_121_NEOFORGE,     "neoforge", "1.21.4",   False, False, None),
    ("SortChest12111Forge",     SRC_12111_FORGE,      "forge",    "1.21.11",  False, False, None),
    ("SortChest12111Fabric",    SRC_FABRIC_121,       "fabric",   "1.21.11",  True,  True,  ">=1.21.9 <1.22"),
    ("SortChest12111NeoForge",  SRC_121_NEOFORGE,     "neoforge", "1.21.11",  False, False, None),
]

if BUNDLE.exists():
    shutil.rmtree(BUNDLE)

# ============================================================
# FAILED-ONLY MODE
# Pass --failed-only to only regenerate targets that failed in
# the most recent ModCompileRuns run. Skips already-green targets
# to save GitHub Actions minutes.
# ============================================================
import argparse as _ap
_args = _ap.ArgumentParser()
_args.add_argument("--failed-only", action="store_true",
    help="Only include targets that failed in the last run")
_args.add_argument("--run-dir", default="",
    help="Explicit run dir to read failures from (default: latest in ModCompileRuns/)")
_parsed = _args.parse_args()

active_targets = targets
if _parsed.failed_only:
    # Find the most recent run dir
    runs_root = ROOT / "ModCompileRuns"
    run_dir = Path(_parsed.run_dir) if _parsed.run_dir else (
        sorted(runs_root.iterdir())[-1] if runs_root.exists() and any(runs_root.iterdir()) else None
    )
    if run_dir is None:
        print("WARNING: --failed-only requested but no run dir found. Using all targets.")
    else:
        art = run_dir / "artifacts" / "all-mod-builds" / "mods"
        if not art.exists():
            print(f"WARNING: No mods artifact at {art}. Using all targets.")
        else:
            # A slug is failed if its result.json has status != "success"
            # or if it has no result.json (never ran)
            failed_slugs = set()
            for mod_dir in art.iterdir():
                if not mod_dir.is_dir(): continue
                result_file = mod_dir / "result.json"
                if result_file.exists():
                    try:
                        result = json.loads(result_file.read_text(encoding="utf-8"))
                        if result.get("status") != "success":
                            failed_slugs.add(mod_dir.name)
                    except Exception:
                        failed_slugs.add(mod_dir.name)
                else:
                    failed_slugs.add(mod_dir.name)

            # Map folder names to build slugs
            # The build system converts folder names to slugs like:
            # SortChest1165Forge → sortchest-forge-1-16-5
            # We match by checking if the slug contains the version+loader hint
            def folder_to_slug_hint(folder: str) -> str:
                """Extract a rough slug hint from a folder name for matching."""
                import re
                # e.g. SortChest1165Forge → 1165 forge → 1-16-5 forge
                m = re.search(r'(\d+)(Forge|Fabric|NeoForge)$', folder, re.IGNORECASE)
                if not m: return folder.lower()
                digits, loader = m.group(1), m.group(2).lower()
                # Insert dashes between digit groups
                ver = "-".join(digits)  # crude but works for matching
                return f"{loader}-{ver}"

            # Build a mapping from folder → slug by reading the actual build plan
            # from the last run's prepared inputs
            slug_map: dict[str, str] = {}
            prepared = run_dir.parent.parent / ".workflow_state" / "prepared" if False else None
            # Simpler: just check which slugs contain the folder's version digits
            def folder_matches_slug(folder: str, slug: str) -> bool:
                import re
                # Extract digits from folder: SortChest1165Forge → 1165
                m = re.search(r'(\d+)(Forge|Fabric|NeoForge)$', folder, re.IGNORECASE)
                if not m: return False
                digits = m.group(1)
                loader = m.group(2).lower()
                if loader == "neoforge": loader = "neoforge"
                # Convert digits to version parts: 1165 → 1-16-5, 121 → 1-21, 12111 → 1-21-11
                # Insert dashes: each digit is a version component
                ver_parts = list(digits)
                # Heuristic: first digit is major (1), rest are minor/patch
                if len(digits) == 3:   # e.g. 189 → 1.8.9
                    ver = f"1-{digits[1]}-{digits[2]}"
                elif len(digits) == 4: # e.g. 1165 → 1-16-5
                    ver = f"1-{digits[1]}{digits[2]}-{digits[3]}"
                elif len(digits) == 5: # e.g. 12111 → 1-21-11
                    ver = f"1-{digits[1]}{digits[2]}-{digits[3]}{digits[4]}"
                elif len(digits) == 2: # e.g. 12 → 1-12
                    ver = f"1-{digits[1]}"
                else:
                    ver = digits
                return loader in slug and ver in slug

            failed_folders = {
                folder for (folder, *_) in targets
                if any(folder_matches_slug(folder, slug) for slug in failed_slugs)
            }

            if failed_folders:
                active_targets = [t for t in targets if t[0] in failed_folders]
                print(f"Failed-only mode: {len(active_targets)} targets to rebuild "
                      f"(skipping {len(targets)-len(active_targets)} already-green)")
                for t in active_targets:
                    print(f"  → {t[0]}")
            else:
                print("No failed targets found — all targets already green!")
                active_targets = []

for (folder, java_src, loader, mc_ver, is_fabric, is_split, fab_dep) in active_targets:
    base = BUNDLE / folder
    write(base / "mod.txt", mod_txt())
    write(base / "version.txt", version_txt(mc_ver, loader))
    write(base / LANG_MAIN, lang_json())

    if is_fabric and is_split:
        write(base / JAVA_CLIENT, java_src)
        write(base / FAB_JSON, fabric_mod_json(fab_dep or "*"))
    elif is_fabric:
        write(base / JAVA_MAIN, java_src)
        write(base / FAB_JSON, fabric_mod_json(fab_dep or "*"))
    else:
        write(base / JAVA_MAIN, java_src)

print(f"Generated {len(active_targets)} targets")

zip_path = ROOT / "incoming" / "sort-chest-all-versions.zip"
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for path in sorted(BUNDLE.rglob("*")):
        if not path.is_file(): continue
        rel = path.relative_to(BUNDLE)
        if len(rel.parts) < 2: continue
        zf.write(path, rel)
print(f"Zip: {zip_path}")

r = subprocess.run(
    ["python3", "build_mods.py", "prepare",
     "--zip-path", str(zip_path),
     "--manifest", "version-manifest.json",
     "--output-dir", "/tmp/prepare-sanity"],
    capture_output=True, text=True, cwd=str(ROOT)
)
if r.returncode == 0:
    matrix = json.loads(r.stdout)
    print(f"Prepare OK — {len(matrix.get('include',[]))} targets")
else:
    print(f"Prepare FAILED:\n{r.stderr[:500]}")
