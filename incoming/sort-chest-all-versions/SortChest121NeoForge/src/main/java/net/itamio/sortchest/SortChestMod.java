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
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.*;

@Mod(SortChestMod.MOD_ID)
public class SortChestMod {
    public static final String MOD_ID = "sortchest";

    public SortChestMod() {
        NeoForge.EVENT_BUS.register(this);
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
