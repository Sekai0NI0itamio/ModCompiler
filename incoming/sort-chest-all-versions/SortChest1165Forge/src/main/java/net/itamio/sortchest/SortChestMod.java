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
