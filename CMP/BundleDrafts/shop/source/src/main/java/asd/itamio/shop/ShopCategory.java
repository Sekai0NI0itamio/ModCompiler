package asd.itamio.shop;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;

import net.minecraft.util.NonNullList;

import java.util.*;

public class ShopCategory {

    private final String name;
    private final ItemStack icon;
    private final List<ItemStack> items = new ArrayList<>();

    public ShopCategory(String name, ItemStack icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public void addItem(ItemStack stack) {
        items.add(stack);
    }

    public static List<ShopCategory> buildFromCreativeTabs() {
        List<ShopCategory> categories = new ArrayList<>();

        for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
            if (tab == null || tab == CreativeTabs.INVENTORY) {
                continue;
            }

            ItemStack icon;
            try {
                icon = tab.getIconItemStack();
            } catch (Exception e) {
                // Some mod tabs crash when getting icon (e.g. Doggy Talents with empty list)
                continue;
            }
            if (icon == null || icon.isEmpty()) {
                continue;
            }

            String tabName = tab.getTabLabel();
            ShopCategory category = new ShopCategory(tabName, icon.copy());

            NonNullList<ItemStack> tabItems = NonNullList.create();
            try {
                tab.displayAllRelevantItems(tabItems);
            } catch (Exception e) {
                // Some tabs crash during item population (e.g. Hotbar, mod tabs with bugs)
                continue;
            }

            for (ItemStack item : tabItems) {
                if (item != null && !item.isEmpty()) {
                    category.addItem(item.copy());
                }
            }

            if (!category.getItems().isEmpty()) {
                categories.add(category);
            }
        }

        return categories;
    }
}
