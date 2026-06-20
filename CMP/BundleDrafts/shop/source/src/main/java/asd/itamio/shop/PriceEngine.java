package asd.itamio.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;

import java.util.*;

public class PriceEngine {

    public static final double BASE_MATERIAL_PRICE = 2.0;
    public static final double UNCRAFTABLE_PRICE = 1000.0;
    public static final double BUY_MULTIPLIER = 1.2;
    public static final double SELL_MULTIPLIER = 0.8;

    private static final Map<String, Double> MANUAL_PRICES = new HashMap<>();

    static {
        // Common building blocks
        MANUAL_PRICES.put("minecraft:dirt", 1.0);
        MANUAL_PRICES.put("minecraft:cobblestone", 1.0);
        MANUAL_PRICES.put("minecraft:stone", 3.0);
        MANUAL_PRICES.put("minecraft:sand", 1.0);
        MANUAL_PRICES.put("minecraft:gravel", 1.0);
        MANUAL_PRICES.put("minecraft:glass", 3.0);
        MANUAL_PRICES.put("minecraft:sandstone", 3.0);
        MANUAL_PRICES.put("minecraft:clay", 12.0);
        MANUAL_PRICES.put("minecraft:brick", 21.0);
        MANUAL_PRICES.put("minecraft:stick", 0.25);
        MANUAL_PRICES.put("minecraft:planks", 0.5);
        MANUAL_PRICES.put("minecraft:oak_planks", 0.5);
        MANUAL_PRICES.put("minecraft:spruce_planks", 0.5);
        MANUAL_PRICES.put("minecraft:birch_planks", 0.5);
        MANUAL_PRICES.put("minecraft:jungle_planks", 0.5);
        MANUAL_PRICES.put("minecraft:acacia_planks", 0.5);
        MANUAL_PRICES.put("minecraft:dark_oak_planks", 0.5);

        // Ores / materials
        MANUAL_PRICES.put("minecraft:coal", 15.0);
        MANUAL_PRICES.put("minecraft:iron_ingot", 22.0);
        MANUAL_PRICES.put("minecraft:gold_ingot", 105.0);
        MANUAL_PRICES.put("minecraft:diamond", 200.0);
        MANUAL_PRICES.put("minecraft:emerald", 200.0);
        MANUAL_PRICES.put("minecraft:redstone", 32.0);
        MANUAL_PRICES.put("minecraft:lapis_ore", 100.0);
        MANUAL_PRICES.put("minecraft:glowstone_dust", 10.0);

        // Ore blocks
        MANUAL_PRICES.put("minecraft:iron_block", 190.0);
        MANUAL_PRICES.put("minecraft:gold_block", 450.0);
        MANUAL_PRICES.put("minecraft:diamond_block", 2000.0);
        MANUAL_PRICES.put("minecraft:lapis_block", 950.0);

        // Wood tools
        MANUAL_PRICES.put("minecraft:wooden_pickaxe", 2.0);
        MANUAL_PRICES.put("minecraft:wooden_axe", 2.0);
        MANUAL_PRICES.put("minecraft:wooden_sword", 1.0);
        MANUAL_PRICES.put("minecraft:wooden_shovel", 1.0);
        MANUAL_PRICES.put("minecraft:wooden_hoe", 1.0);

        // Stone tools
        MANUAL_PRICES.put("minecraft:stone_pickaxe", 4.0);
        MANUAL_PRICES.put("minecraft:stone_axe", 4.0);
        MANUAL_PRICES.put("minecraft:stone_sword", 2.0);
        MANUAL_PRICES.put("minecraft:stone_shovel", 2.0);
        MANUAL_PRICES.put("minecraft:stone_hoe", 2.0);

        // Iron tools
        MANUAL_PRICES.put("minecraft:iron_pickaxe", 22.0);
        MANUAL_PRICES.put("minecraft:iron_axe", 22.0);
        MANUAL_PRICES.put("minecraft:iron_sword", 22.0);
        MANUAL_PRICES.put("minecraft:iron_shovel", 22.0);
        MANUAL_PRICES.put("minecraft:iron_hoe", 22.0);

        // Gold tools
        MANUAL_PRICES.put("minecraft:golden_pickaxe", 6.0);
        MANUAL_PRICES.put("minecraft:golden_axe", 6.0);
        MANUAL_PRICES.put("minecraft:golden_sword", 6.0);
        MANUAL_PRICES.put("minecraft:golden_shovel", 6.0);
        MANUAL_PRICES.put("minecraft:golden_hoe", 6.0);

        // Diamond tools
        MANUAL_PRICES.put("minecraft:diamond_pickaxe", 650.0);
        MANUAL_PRICES.put("minecraft:diamond_axe", 650.0);
        MANUAL_PRICES.put("minecraft:diamond_sword", 420.0);
        MANUAL_PRICES.put("minecraft:diamond_shovel", 420.0);
        MANUAL_PRICES.put("minecraft:diamond_hoe", 420.0);

        // Leather armor
        MANUAL_PRICES.put("minecraft:leather_helmet", 42.0);
        MANUAL_PRICES.put("minecraft:leather_chestplate", 85.0);
        MANUAL_PRICES.put("minecraft:leather_leggings", 70.0);
        MANUAL_PRICES.put("minecraft:leather_boots", 42.0);

        // Iron armor
        MANUAL_PRICES.put("minecraft:iron_helmet", 22.0);
        MANUAL_PRICES.put("minecraft:iron_chestplate", 22.0);
        MANUAL_PRICES.put("minecraft:iron_leggings", 22.0);
        MANUAL_PRICES.put("minecraft:iron_boots", 22.0);

        // Gold armor
        MANUAL_PRICES.put("minecraft:golden_helmet", 6.0);
        MANUAL_PRICES.put("minecraft:golden_chestplate", 6.0);
        MANUAL_PRICES.put("minecraft:golden_leggings", 6.0);
        MANUAL_PRICES.put("minecraft:golden_boots", 6.0);

        // Diamond armor
        MANUAL_PRICES.put("minecraft:diamond_helmet", 850.0);
        MANUAL_PRICES.put("minecraft:diamond_chestplate", 1750.0);
        MANUAL_PRICES.put("minecraft:diamond_leggings", 1400.0);
        MANUAL_PRICES.put("minecraft:diamond_boots", 850.0);

        // Chain armor
        MANUAL_PRICES.put("minecraft:chainmail_helmet", 30.0);
        MANUAL_PRICES.put("minecraft:chainmail_chestplate", 50.0);
        MANUAL_PRICES.put("minecraft:chainmail_leggings", 40.0);
        MANUAL_PRICES.put("minecraft:chainmail_boots", 30.0);

        // Food
        MANUAL_PRICES.put("minecraft:apple", 10.0);
        MANUAL_PRICES.put("minecraft:bread", 30.0);
        MANUAL_PRICES.put("minecraft:cooked_fish", 7.0);
        MANUAL_PRICES.put("minecraft:cooked_salmon", 7.0);
        MANUAL_PRICES.put("minecraft:cooked_beef", 7.0);
        MANUAL_PRICES.put("minecraft:cooked_porkchop", 7.0);
        MANUAL_PRICES.put("minecraft:cooked_mutton", 7.0);
        MANUAL_PRICES.put("minecraft:cooked_chicken", 7.0);
        MANUAL_PRICES.put("minecraft:fish", 5.0);
        MANUAL_PRICES.put("minecraft:salmon", 5.0);
        MANUAL_PRICES.put("minecraft:wheat", 9.0);
        MANUAL_PRICES.put("minecraft:sugar", 10.0);

        // Useful items
        MANUAL_PRICES.put("minecraft:torch", 4.0);
        MANUAL_PRICES.put("minecraft:ladder", 0.5);
        MANUAL_PRICES.put("minecraft:chest", 4.0);
        MANUAL_PRICES.put("minecraft:furnace", 8.5);
        MANUAL_PRICES.put("minecraft:crafting_table", 2.5);
        MANUAL_PRICES.put("minecraft:bucket", 22.0);
        MANUAL_PRICES.put("minecraft:compass", 22.0);
        MANUAL_PRICES.put("minecraft:clock", 6.0);

        // Valuables
        MANUAL_PRICES.put("minecraft:tnt", 100.0);
        MANUAL_PRICES.put("minecraft:golden_apple", 100.0);
        MANUAL_PRICES.put("minecraft:saddle", 100.0);
        MANUAL_PRICES.put("minecraft:slime_ball", 50.0);
        MANUAL_PRICES.put("minecraft:ender_pearl", 100.0);
        MANUAL_PRICES.put("minecraft:book", 45.0);
        MANUAL_PRICES.put("minecraft:bookshelf", 140.0);

        // Mob drops
        MANUAL_PRICES.put("minecraft:bone", 2.0);
        MANUAL_PRICES.put("minecraft:feather", 3.0);
        MANUAL_PRICES.put("minecraft:string", 5.0);
        MANUAL_PRICES.put("minecraft:leather", 10.0);
        MANUAL_PRICES.put("minecraft:dye", 10.0);
        MANUAL_PRICES.put("minecraft:arrow", 3.5);
        MANUAL_PRICES.put("minecraft:egg", 1.0);

        // Redstone
        MANUAL_PRICES.put("minecraft:dispenser", 58.0);
        MANUAL_PRICES.put("minecraft:note_block", 36.0);
        MANUAL_PRICES.put("minecraft:repeater", 110.0);
        MANUAL_PRICES.put("minecraft:piston", 25.0);
        MANUAL_PRICES.put("minecraft:observer", 30.0);

        // Rails
        MANUAL_PRICES.put("minecraft:rail", 22.0);
        MANUAL_PRICES.put("minecraft:golden_rail", 45.0);
        MANUAL_PRICES.put("minecraft:detector_rail", 40.0);
        MANUAL_PRICES.put("minecraft:activator_rail", 40.0);
        MANUAL_PRICES.put("minecraft:minecart", 23.0);

        // Nether
        MANUAL_PRICES.put("minecraft:netherrack", 1.0);
        MANUAL_PRICES.put("minecraft:soul_sand", 10.0);
        MANUAL_PRICES.put("minecraft:nether_brick", 5.0);
        MANUAL_PRICES.put("minecraft:blaze_rod", 50.0);
        MANUAL_PRICES.put("minecraft:ghast_tear", 100.0);
        MANUAL_PRICES.put("minecraft:magma_cream", 40.0);

        // End
        MANUAL_PRICES.put("minecraft:end_stone", 10.0);
        MANUAL_PRICES.put("minecraft:chorus_fruit", 20.0);
        MANUAL_PRICES.put("minecraft:purpur_block", 15.0);
        MANUAL_PRICES.put("minecraft:shulker_shell", 500.0);
        MANUAL_PRICES.put("minecraft:elytra", 10000.0);
        MANUAL_PRICES.put("minecraft:end_crystal", 500.0);
    }

    private final Map<String, Double> priceCache = new HashMap<>();
    private final Set<String> computing = new HashSet<>();

    public double getBuyPrice(ItemStack stack) {
        double base = getBasePrice(stack);
        return Math.round(base * BUY_MULTIPLIER * 100.0) / 100.0;
    }

    public double getSellPrice(ItemStack stack) {
        double base = getBasePrice(stack);
        return Math.round(base * SELL_MULTIPLIER * 100.0) / 100.0;
    }

    public double getBasePrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }

        String key = getItemKey(stack);
        if (priceCache.containsKey(key)) {
            return priceCache.get(key);
        }

        double price = computeBasePrice(stack);
        priceCache.put(key, price);
        return price;
    }

    private double computeBasePrice(ItemStack stack) {
        String key = getItemKey(stack);
        String registryName = stack.getItem().getRegistryName().toString();

        // Step 1: Check manual overrides
        Double manualPrice = MANUAL_PRICES.get(registryName);
        if (manualPrice != null) {
            return manualPrice;
        }

        if (computing.contains(key)) {
            return BASE_MATERIAL_PRICE;
        }

        computing.add(key);

        try {
            // Step 2: Try recipe-based pricing
            List<IRecipe> recipes = findRecipesFor(stack);
            if (!recipes.isEmpty()) {
                IRecipe recipe = recipes.get(0);
                double totalIngredientPrice = 0.0;
                boolean hasIngredients = false;

                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient == Ingredient.EMPTY) {
                        continue;
                    }
                    ItemStack[] matchingStacks = ingredient.getMatchingStacks();
                    if (matchingStacks == null || matchingStacks.length == 0) {
                        continue;
                    }

                    ItemStack ingredientStack = matchingStacks[0];
                    double ingredientPrice = getBasePrice(ingredientStack);
                    totalIngredientPrice += ingredientPrice;
                    hasIngredients = true;
                }

                if (hasIngredients) {
                    int outputCount = recipe.getRecipeOutput().getCount();
                    if (outputCount <= 0) {
                        outputCount = 1;
                    }

                    double perItemPrice = totalIngredientPrice / outputCount;
                    return Math.round(perItemPrice * 100.0) / 100.0;
                }
            }

            // Step 3: Rarity tier fallback
            double rarityPrice = estimateRarityPrice(registryName, stack);
            if (rarityPrice > 0) {
                return rarityPrice;
            }

            // Step 4: Uncraftable fallback
            return UNCRAFTABLE_PRICE;
        } finally {
            computing.remove(key);
        }
    }

    private double estimateRarityPrice(String registryName, ItemStack stack) {
        String itemName = registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName;

        // Spawn eggs and command blocks
        if (itemName.contains("spawn_egg") || itemName.contains("command_block")) {
            return 5000.0;
        }

        // Tier 4 (Epic): diamond-tier items, nether star
        if (itemName.contains("diamond") || itemName.contains("nether_star")) {
            return 200.0;
        }

        // Tier 3 (Rare): iron and gold items
        if (itemName.contains("iron")) {
            return 50.0;
        }
        if (itemName.contains("gold") || itemName.contains("golden")) {
            return 100.0;
        }

        // Tier 2 (Uncommon): identifiable harder items
        if (itemName.contains("prismarine") || itemName.contains("packed_ice") || itemName.contains("sea_lantern")) {
            return 10.0;
        }

        // Tier 1 (Common): basic block variants
        if (itemName.contains("dirt") || itemName.contains("stone") || itemName.contains("netherrack")
                || itemName.contains("cobblestone") || itemName.contains("granite") || itemName.contains("diorite")
                || itemName.contains("andesite") || itemName.contains("basalt")) {
            return 2.0;
        }

        // No rarity match found - return 0 to signal no estimate
        return 0.0;
    }

    public void setManualPrice(String registryName, double price) {
        MANUAL_PRICES.put(registryName, price);
        priceCache.clear();
    }

    @SuppressWarnings("unchecked")
    private List<IRecipe> findRecipesFor(ItemStack stack) {
        List<IRecipe> result = new ArrayList<>();
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            ItemStack output = recipe.getRecipeOutput();
            if (output != null && !output.isEmpty() && isSameItem(output, stack)) {
                result.add(recipe);
            }
        }
        return result;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) {
            return false;
        }
        return a.getMetadata() == b.getMetadata() || a.getMetadata() == 32767 || b.getMetadata() == 32767;
    }

    private String getItemKey(ItemStack stack) {
        return stack.getItem().getRegistryName() + ":" + stack.getMetadata();
    }

    public void clearCache() {
        priceCache.clear();
        computing.clear();
    }
}
