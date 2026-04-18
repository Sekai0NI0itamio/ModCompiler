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
                if (ItemStack.areItemsAndComponentsEqual(a,b)) {
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
        return ItemStack.areItemsAndComponentsEqual(a,b);
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
        final net.minecraft.component.ComponentMap components; final int hash;
        ItemKey(ItemStack s) {
            item = s.getItem();
            components = s.getComponents();
            hash = Objects.hash(item, components);
        }
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey)) return false;
            ItemKey k = (ItemKey)o;
            return item==k.item && Objects.equals(components,k.components);
        }
        public int hashCode() { return hash; }
    }
}
