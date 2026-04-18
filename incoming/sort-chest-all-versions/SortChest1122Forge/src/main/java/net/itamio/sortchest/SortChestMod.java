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
