/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.block;

import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class DecoratedPotPatterns {
    private static final String DECORATED_POT_BASE = "decorated_pot_base";
    public static final RegistryKey<String> DECORATED_POT_BASE_KEY = DecoratedPotPatterns.of("decorated_pot_base");
    private static final String DECORATED_POT_SIDE = "decorated_pot_side";
    private static final String ANGLER_POTTERY_PATTERN = "angler_pottery_pattern";
    private static final String ARCHER_POTTERY_PATTERN = "archer_pottery_pattern";
    private static final String ARMS_UP_POTTERY_PATTERN = "arms_up_pottery_pattern";
    private static final String BLADE_POTTERY_PATTERN = "blade_pottery_pattern";
    private static final String BREWER_POTTERY_PATTERN = "brewer_pottery_pattern";
    private static final String BURN_POTTERY_PATTERN = "burn_pottery_pattern";
    private static final String DANGER_POTTERY_PATTERN = "danger_pottery_pattern";
    private static final String EXPLORER_POTTERY_PATTERN = "explorer_pottery_pattern";
    private static final String FLOW_POTTERY_PATTERN = "flow_pottery_pattern";
    private static final String FRIEND_POTTERY_PATTERN = "friend_pottery_pattern";
    private static final String GUSTER_POTTERY_PATTERN = "guster_pottery_pattern";
    private static final String HEART_POTTERY_PATTERN = "heart_pottery_pattern";
    private static final String HEARTBREAK_POTTERY_PATTERN = "heartbreak_pottery_pattern";
    private static final String HOWL_POTTERY_PATTERN = "howl_pottery_pattern";
    private static final String MINER_POTTERY_PATTERN = "miner_pottery_pattern";
    private static final String MOURNER_POTTERY_PATTERN = "mourner_pottery_pattern";
    private static final String PLENTY_POTTERY_PATTERN = "plenty_pottery_pattern";
    private static final String PRIZE_POTTERY_PATTERN = "prize_pottery_pattern";
    private static final String SCRAPE_POTTERY_PATTERN = "scrape_pottery_pattern";
    private static final String SHEAF_POTTERY_PATTERN = "sheaf_pottery_pattern";
    private static final String SHELTER_POTTERY_PATTERN = "shelter_pottery_pattern";
    private static final String SKULL_POTTERY_PATTERN = "skull_pottery_pattern";
    private static final String SNORT_POTTERY_PATTERN = "snort_pottery_pattern";
    private static final RegistryKey<String> DECORATED_POT_SIDE_KEY = DecoratedPotPatterns.of("decorated_pot_side");
    private static final RegistryKey<String> ANGLER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("angler_pottery_pattern");
    private static final RegistryKey<String> ARCHER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("archer_pottery_pattern");
    private static final RegistryKey<String> ARMS_UP_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("arms_up_pottery_pattern");
    private static final RegistryKey<String> BLADE_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("blade_pottery_pattern");
    private static final RegistryKey<String> BREWER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("brewer_pottery_pattern");
    private static final RegistryKey<String> BURN_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("burn_pottery_pattern");
    private static final RegistryKey<String> DANGER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("danger_pottery_pattern");
    private static final RegistryKey<String> EXPLORER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("explorer_pottery_pattern");
    private static final RegistryKey<String> FLOW_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("flow_pottery_pattern");
    private static final RegistryKey<String> FRIEND_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("friend_pottery_pattern");
    private static final RegistryKey<String> GUSTER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("guster_pottery_pattern");
    private static final RegistryKey<String> HEART_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("heart_pottery_pattern");
    private static final RegistryKey<String> HEARTBREAK_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("heartbreak_pottery_pattern");
    private static final RegistryKey<String> HOWL_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("howl_pottery_pattern");
    private static final RegistryKey<String> MINER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("miner_pottery_pattern");
    private static final RegistryKey<String> MOURNER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("mourner_pottery_pattern");
    private static final RegistryKey<String> PLENTY_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("plenty_pottery_pattern");
    private static final RegistryKey<String> POTTERY_PATTERN_PRIZE_KEY = DecoratedPotPatterns.of("prize_pottery_pattern");
    private static final RegistryKey<String> SCRAPE_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("scrape_pottery_pattern");
    private static final RegistryKey<String> SHEAF_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("sheaf_pottery_pattern");
    private static final RegistryKey<String> SHELTER_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("shelter_pottery_pattern");
    private static final RegistryKey<String> SKULL_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("skull_pottery_pattern");
    private static final RegistryKey<String> SNORT_POTTERY_PATTERN_KEY = DecoratedPotPatterns.of("snort_pottery_pattern");
    private static final Map<Item, RegistryKey<String>> SHERD_TO_PATTERN = Map.ofEntries(Map.entry(Items.BRICK, DECORATED_POT_SIDE_KEY), Map.entry(Items.ANGLER_POTTERY_SHERD, ANGLER_POTTERY_PATTERN_KEY), Map.entry(Items.ARCHER_POTTERY_SHERD, ARCHER_POTTERY_PATTERN_KEY), Map.entry(Items.ARMS_UP_POTTERY_SHERD, ARMS_UP_POTTERY_PATTERN_KEY), Map.entry(Items.BLADE_POTTERY_SHERD, BLADE_POTTERY_PATTERN_KEY), Map.entry(Items.BREWER_POTTERY_SHERD, BREWER_POTTERY_PATTERN_KEY), Map.entry(Items.BURN_POTTERY_SHERD, BURN_POTTERY_PATTERN_KEY), Map.entry(Items.DANGER_POTTERY_SHERD, DANGER_POTTERY_PATTERN_KEY), Map.entry(Items.EXPLORER_POTTERY_SHERD, EXPLORER_POTTERY_PATTERN_KEY), Map.entry(Items.FLOW_POTTERY_SHERD, FLOW_POTTERY_PATTERN_KEY), Map.entry(Items.FRIEND_POTTERY_SHERD, FRIEND_POTTERY_PATTERN_KEY), Map.entry(Items.GUSTER_POTTERY_SHERD, GUSTER_POTTERY_PATTERN_KEY), Map.entry(Items.HEART_POTTERY_SHERD, HEART_POTTERY_PATTERN_KEY), Map.entry(Items.HEARTBREAK_POTTERY_SHERD, HEARTBREAK_POTTERY_PATTERN_KEY), Map.entry(Items.HOWL_POTTERY_SHERD, HOWL_POTTERY_PATTERN_KEY), Map.entry(Items.MINER_POTTERY_SHERD, MINER_POTTERY_PATTERN_KEY), Map.entry(Items.MOURNER_POTTERY_SHERD, MOURNER_POTTERY_PATTERN_KEY), Map.entry(Items.PLENTY_POTTERY_SHERD, PLENTY_POTTERY_PATTERN_KEY), Map.entry(Items.PRIZE_POTTERY_SHERD, POTTERY_PATTERN_PRIZE_KEY), Map.entry(Items.SCRAPE_POTTERY_SHERD, SCRAPE_POTTERY_PATTERN_KEY), Map.entry(Items.SHEAF_POTTERY_SHERD, SHEAF_POTTERY_PATTERN_KEY), Map.entry(Items.SHELTER_POTTERY_SHERD, SHELTER_POTTERY_PATTERN_KEY), Map.entry(Items.SKULL_POTTERY_SHERD, SKULL_POTTERY_PATTERN_KEY), Map.entry(Items.SNORT_POTTERY_SHERD, SNORT_POTTERY_PATTERN_KEY));

    private static RegistryKey<String> of(String path) {
        return RegistryKey.of(RegistryKeys.DECORATED_POT_PATTERN, new Identifier(path));
    }

    public static Identifier getTextureId(RegistryKey<String> key) {
        return key.getValue().withPrefixedPath("entity/decorated_pot/");
    }

    @Nullable
    public static RegistryKey<String> fromSherd(Item sherd) {
        return SHERD_TO_PATTERN.get(sherd);
    }

    public static String registerAndGetDefault(Registry<String> registry) {
        Registry.register(registry, DECORATED_POT_SIDE_KEY, DECORATED_POT_SIDE);
        Registry.register(registry, ANGLER_POTTERY_PATTERN_KEY, ANGLER_POTTERY_PATTERN);
        Registry.register(registry, ARCHER_POTTERY_PATTERN_KEY, ARCHER_POTTERY_PATTERN);
        Registry.register(registry, ARMS_UP_POTTERY_PATTERN_KEY, ARMS_UP_POTTERY_PATTERN);
        Registry.register(registry, BLADE_POTTERY_PATTERN_KEY, BLADE_POTTERY_PATTERN);
        Registry.register(registry, BREWER_POTTERY_PATTERN_KEY, BREWER_POTTERY_PATTERN);
        Registry.register(registry, BURN_POTTERY_PATTERN_KEY, BURN_POTTERY_PATTERN);
        Registry.register(registry, DANGER_POTTERY_PATTERN_KEY, DANGER_POTTERY_PATTERN);
        Registry.register(registry, EXPLORER_POTTERY_PATTERN_KEY, EXPLORER_POTTERY_PATTERN);
        Registry.register(registry, FLOW_POTTERY_PATTERN_KEY, FLOW_POTTERY_PATTERN);
        Registry.register(registry, FRIEND_POTTERY_PATTERN_KEY, FRIEND_POTTERY_PATTERN);
        Registry.register(registry, GUSTER_POTTERY_PATTERN_KEY, GUSTER_POTTERY_PATTERN);
        Registry.register(registry, HEART_POTTERY_PATTERN_KEY, HEART_POTTERY_PATTERN);
        Registry.register(registry, HEARTBREAK_POTTERY_PATTERN_KEY, HEARTBREAK_POTTERY_PATTERN);
        Registry.register(registry, HOWL_POTTERY_PATTERN_KEY, HOWL_POTTERY_PATTERN);
        Registry.register(registry, MINER_POTTERY_PATTERN_KEY, MINER_POTTERY_PATTERN);
        Registry.register(registry, MOURNER_POTTERY_PATTERN_KEY, MOURNER_POTTERY_PATTERN);
        Registry.register(registry, PLENTY_POTTERY_PATTERN_KEY, PLENTY_POTTERY_PATTERN);
        Registry.register(registry, POTTERY_PATTERN_PRIZE_KEY, PRIZE_POTTERY_PATTERN);
        Registry.register(registry, SCRAPE_POTTERY_PATTERN_KEY, SCRAPE_POTTERY_PATTERN);
        Registry.register(registry, SHEAF_POTTERY_PATTERN_KEY, SHEAF_POTTERY_PATTERN);
        Registry.register(registry, SHELTER_POTTERY_PATTERN_KEY, SHELTER_POTTERY_PATTERN);
        Registry.register(registry, SKULL_POTTERY_PATTERN_KEY, SKULL_POTTERY_PATTERN);
        Registry.register(registry, SNORT_POTTERY_PATTERN_KEY, SNORT_POTTERY_PATTERN);
        return Registry.register(registry, DECORATED_POT_BASE_KEY, DECORATED_POT_BASE);
    }
}

