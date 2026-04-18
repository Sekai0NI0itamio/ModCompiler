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
