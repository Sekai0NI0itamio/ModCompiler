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
