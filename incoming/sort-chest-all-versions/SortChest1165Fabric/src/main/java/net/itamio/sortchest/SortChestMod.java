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
