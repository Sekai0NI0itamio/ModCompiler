/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.item.trim;

import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.item.trim.ArmorTrimPatterns;
import net.minecraft.registry.Registerable;

public class OneTwentyOneArmorTrimPatterns {
    public static void bootstrap(Registerable<ArmorTrimPattern> armorTrimPatternRegisterable) {
        ArmorTrimPatterns.register(armorTrimPatternRegisterable, Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.FLOW);
        ArmorTrimPatterns.register(armorTrimPatternRegisterable, Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.BOLT);
    }
}

