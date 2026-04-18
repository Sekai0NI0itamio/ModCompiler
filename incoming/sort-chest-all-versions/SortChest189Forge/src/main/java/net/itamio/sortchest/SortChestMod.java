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
