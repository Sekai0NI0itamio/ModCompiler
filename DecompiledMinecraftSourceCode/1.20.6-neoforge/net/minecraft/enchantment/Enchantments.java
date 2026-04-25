/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import java.util.Optional;
import net.minecraft.enchantment.BindingCurseEnchantment;
import net.minecraft.enchantment.BreachEnchantment;
import net.minecraft.enchantment.DamageEnchantment;
import net.minecraft.enchantment.DensityEnchantment;
import net.minecraft.enchantment.DepthStriderEnchantment;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.FrostWalkerEnchantment;
import net.minecraft.enchantment.InfinityEnchantment;
import net.minecraft.enchantment.LuckEnchantment;
import net.minecraft.enchantment.MendingEnchantment;
import net.minecraft.enchantment.MultishotEnchantment;
import net.minecraft.enchantment.PiercingEnchantment;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.enchantment.RiptideEnchantment;
import net.minecraft.enchantment.SilkTouchEnchantment;
import net.minecraft.enchantment.SoulSpeedEnchantment;
import net.minecraft.enchantment.SwiftSneakEnchantment;
import net.minecraft.enchantment.ThornsEnchantment;
import net.minecraft.enchantment.UnbreakingEnchantment;
import net.minecraft.enchantment.VanishingCurseEnchantment;
import net.minecraft.enchantment.WindBurstEnchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.ItemTags;

public class Enchantments {
    private static final EquipmentSlot[] ALL_ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final Enchantment PROTECTION = Enchantments.register("protection", new ProtectionEnchantment(Enchantment.properties(ItemTags.ARMOR_ENCHANTABLE, 10, 4, Enchantment.leveledCost(1, 11), Enchantment.leveledCost(12, 11), 1, ALL_ARMOR), ProtectionEnchantment.Type.ALL));
    public static final Enchantment FIRE_PROTECTION = Enchantments.register("fire_protection", new ProtectionEnchantment(Enchantment.properties(ItemTags.ARMOR_ENCHANTABLE, 5, 4, Enchantment.leveledCost(10, 8), Enchantment.leveledCost(18, 8), 2, ALL_ARMOR), ProtectionEnchantment.Type.FIRE));
    public static final Enchantment FEATHER_FALLING = Enchantments.register("feather_falling", new ProtectionEnchantment(Enchantment.properties(ItemTags.FOOT_ARMOR_ENCHANTABLE, 5, 4, Enchantment.leveledCost(5, 6), Enchantment.leveledCost(11, 6), 2, ALL_ARMOR), ProtectionEnchantment.Type.FALL));
    public static final Enchantment BLAST_PROTECTION = Enchantments.register("blast_protection", new ProtectionEnchantment(Enchantment.properties(ItemTags.ARMOR_ENCHANTABLE, 2, 4, Enchantment.leveledCost(5, 8), Enchantment.leveledCost(13, 8), 4, ALL_ARMOR), ProtectionEnchantment.Type.EXPLOSION));
    public static final Enchantment PROJECTILE_PROTECTION = Enchantments.register("projectile_protection", new ProtectionEnchantment(Enchantment.properties(ItemTags.ARMOR_ENCHANTABLE, 5, 4, Enchantment.leveledCost(3, 6), Enchantment.leveledCost(9, 6), 2, ALL_ARMOR), ProtectionEnchantment.Type.PROJECTILE));
    public static final Enchantment RESPIRATION = Enchantments.register("respiration", new Enchantment(Enchantment.properties(ItemTags.HEAD_ARMOR_ENCHANTABLE, 2, 3, Enchantment.leveledCost(10, 10), Enchantment.leveledCost(40, 10), 4, ALL_ARMOR)));
    public static final Enchantment AQUA_AFFINITY = Enchantments.register("aqua_affinity", new Enchantment(Enchantment.properties(ItemTags.HEAD_ARMOR_ENCHANTABLE, 2, 1, Enchantment.constantCost(1), Enchantment.constantCost(41), 4, ALL_ARMOR)));
    public static final Enchantment THORNS = Enchantments.register("thorns", new ThornsEnchantment(Enchantment.properties(ItemTags.ARMOR_ENCHANTABLE, ItemTags.CHEST_ARMOR_ENCHANTABLE, 1, 3, Enchantment.leveledCost(10, 20), Enchantment.leveledCost(60, 20), 8, ALL_ARMOR)));
    public static final Enchantment DEPTH_STRIDER = Enchantments.register("depth_strider", new DepthStriderEnchantment(Enchantment.properties(ItemTags.FOOT_ARMOR_ENCHANTABLE, 2, 3, Enchantment.leveledCost(10, 10), Enchantment.leveledCost(25, 10), 4, ALL_ARMOR)));
    public static final Enchantment FROST_WALKER = Enchantments.register("frost_walker", new FrostWalkerEnchantment(Enchantment.properties(ItemTags.FOOT_ARMOR_ENCHANTABLE, 2, 2, Enchantment.leveledCost(10, 10), Enchantment.leveledCost(25, 10), 4, EquipmentSlot.FEET)));
    public static final Enchantment BINDING_CURSE = Enchantments.register("binding_curse", new BindingCurseEnchantment(Enchantment.properties(ItemTags.EQUIPPABLE_ENCHANTABLE, 1, 1, Enchantment.constantCost(25), Enchantment.constantCost(50), 8, ALL_ARMOR)));
    public static final Enchantment SOUL_SPEED = Enchantments.register("soul_speed", new SoulSpeedEnchantment(Enchantment.properties(ItemTags.FOOT_ARMOR_ENCHANTABLE, 1, 3, Enchantment.leveledCost(10, 10), Enchantment.leveledCost(25, 10), 8, EquipmentSlot.FEET)));
    public static final Enchantment SWIFT_SNEAK = Enchantments.register("swift_sneak", new SwiftSneakEnchantment(Enchantment.properties(ItemTags.LEG_ARMOR_ENCHANTABLE, 1, 3, Enchantment.leveledCost(25, 25), Enchantment.leveledCost(75, 25), 8, EquipmentSlot.LEGS)));
    public static final Enchantment SHARPNESS = Enchantments.register("sharpness", new DamageEnchantment(Enchantment.properties(ItemTags.SHARP_WEAPON_ENCHANTABLE, ItemTags.SWORD_ENCHANTABLE, 10, 5, Enchantment.leveledCost(1, 11), Enchantment.leveledCost(21, 11), 1, EquipmentSlot.MAINHAND), Optional.empty()));
    public static final Enchantment SMITE = Enchantments.register("smite", new DamageEnchantment(Enchantment.properties(ItemTags.WEAPON_ENCHANTABLE, ItemTags.SWORD_ENCHANTABLE, 5, 5, Enchantment.leveledCost(5, 8), Enchantment.leveledCost(25, 8), 2, EquipmentSlot.MAINHAND), Optional.of(EntityTypeTags.SENSITIVE_TO_SMITE)));
    public static final Enchantment BANE_OF_ARTHROPODS = Enchantments.register("bane_of_arthropods", new DamageEnchantment(Enchantment.properties(ItemTags.WEAPON_ENCHANTABLE, ItemTags.SWORD_ENCHANTABLE, 5, 5, Enchantment.leveledCost(5, 8), Enchantment.leveledCost(25, 8), 2, EquipmentSlot.MAINHAND), Optional.of(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)));
    public static final Enchantment KNOCKBACK = Enchantments.register("knockback", new Enchantment(Enchantment.properties(ItemTags.SWORD_ENCHANTABLE, 5, 2, Enchantment.leveledCost(5, 20), Enchantment.leveledCost(55, 20), 2, EquipmentSlot.MAINHAND)));
    public static final Enchantment FIRE_ASPECT = Enchantments.register("fire_aspect", new Enchantment(Enchantment.properties(ItemTags.FIRE_ASPECT_ENCHANTABLE, 2, 2, Enchantment.leveledCost(10, 20), Enchantment.leveledCost(60, 20), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment LOOTING = Enchantments.register("looting", new LuckEnchantment(Enchantment.properties(ItemTags.SWORD_ENCHANTABLE, 2, 3, Enchantment.leveledCost(15, 9), Enchantment.leveledCost(65, 9), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment SWEEPING_EDGE = Enchantments.register("sweeping_edge", new Enchantment(Enchantment.properties(ItemTags.SWORD_ENCHANTABLE, 2, 3, Enchantment.leveledCost(5, 9), Enchantment.leveledCost(20, 9), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment EFFICIENCY = Enchantments.register("efficiency", new Enchantment(Enchantment.properties(ItemTags.MINING_ENCHANTABLE, 10, 5, Enchantment.leveledCost(1, 10), Enchantment.leveledCost(51, 10), 1, EquipmentSlot.MAINHAND)));
    public static final Enchantment SILK_TOUCH = Enchantments.register("silk_touch", new SilkTouchEnchantment(Enchantment.properties(ItemTags.MINING_LOOT_ENCHANTABLE, 1, 1, Enchantment.constantCost(15), Enchantment.constantCost(65), 8, EquipmentSlot.MAINHAND)));
    public static final Enchantment UNBREAKING = Enchantments.register("unbreaking", new UnbreakingEnchantment(Enchantment.properties(ItemTags.DURABILITY_ENCHANTABLE, 5, 3, Enchantment.leveledCost(5, 8), Enchantment.leveledCost(55, 8), 2, EquipmentSlot.MAINHAND)));
    public static final Enchantment FORTUNE = Enchantments.register("fortune", new LuckEnchantment(Enchantment.properties(ItemTags.MINING_LOOT_ENCHANTABLE, 2, 3, Enchantment.leveledCost(15, 9), Enchantment.leveledCost(65, 9), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment POWER = Enchantments.register("power", new Enchantment(Enchantment.properties(ItemTags.BOW_ENCHANTABLE, 10, 5, Enchantment.leveledCost(1, 10), Enchantment.leveledCost(16, 10), 1, EquipmentSlot.MAINHAND)));
    public static final Enchantment PUNCH = Enchantments.register("punch", new Enchantment(Enchantment.properties(ItemTags.BOW_ENCHANTABLE, 2, 2, Enchantment.leveledCost(12, 20), Enchantment.leveledCost(37, 20), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment FLAME = Enchantments.register("flame", new Enchantment(Enchantment.properties(ItemTags.BOW_ENCHANTABLE, 2, 1, Enchantment.constantCost(20), Enchantment.constantCost(50), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment INFINITY = Enchantments.register("infinity", new InfinityEnchantment(Enchantment.properties(ItemTags.BOW_ENCHANTABLE, 1, 1, Enchantment.constantCost(20), Enchantment.constantCost(50), 8, EquipmentSlot.MAINHAND)));
    public static final Enchantment LUCK_OF_THE_SEA = Enchantments.register("luck_of_the_sea", new LuckEnchantment(Enchantment.properties(ItemTags.FISHING_ENCHANTABLE, 2, 3, Enchantment.leveledCost(15, 9), Enchantment.leveledCost(65, 9), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment LURE = Enchantments.register("lure", new Enchantment(Enchantment.properties(ItemTags.FISHING_ENCHANTABLE, 2, 3, Enchantment.leveledCost(15, 9), Enchantment.leveledCost(65, 9), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment LOYALTY = Enchantments.register("loyalty", new Enchantment(Enchantment.properties(ItemTags.TRIDENT_ENCHANTABLE, 5, 3, Enchantment.leveledCost(12, 7), Enchantment.constantCost(50), 2, EquipmentSlot.MAINHAND)));
    public static final Enchantment IMPALING = Enchantments.register("impaling", new DamageEnchantment(Enchantment.properties(ItemTags.TRIDENT_ENCHANTABLE, 2, 5, Enchantment.leveledCost(1, 8), Enchantment.leveledCost(21, 8), 4, EquipmentSlot.MAINHAND), Optional.of(EntityTypeTags.SENSITIVE_TO_IMPALING)));
    public static final Enchantment RIPTIDE = Enchantments.register("riptide", new RiptideEnchantment(Enchantment.properties(ItemTags.TRIDENT_ENCHANTABLE, 2, 3, Enchantment.leveledCost(17, 7), Enchantment.constantCost(50), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment CHANNELING = Enchantments.register("channeling", new Enchantment(Enchantment.properties(ItemTags.TRIDENT_ENCHANTABLE, 1, 1, Enchantment.constantCost(25), Enchantment.constantCost(50), 8, EquipmentSlot.MAINHAND)));
    public static final Enchantment MULTISHOT = Enchantments.register("multishot", new MultishotEnchantment(Enchantment.properties(ItemTags.CROSSBOW_ENCHANTABLE, 2, 1, Enchantment.constantCost(20), Enchantment.constantCost(50), 4, EquipmentSlot.MAINHAND)));
    public static final Enchantment QUICK_CHARGE = Enchantments.register("quick_charge", new Enchantment(Enchantment.properties(ItemTags.CROSSBOW_ENCHANTABLE, 5, 3, Enchantment.leveledCost(12, 20), Enchantment.constantCost(50), 2, EquipmentSlot.MAINHAND)));
    public static final Enchantment PIERCING = Enchantments.register("piercing", new PiercingEnchantment(Enchantment.properties(ItemTags.CROSSBOW_ENCHANTABLE, 10, 4, Enchantment.leveledCost(1, 10), Enchantment.constantCost(50), 1, EquipmentSlot.MAINHAND)));
    public static final Enchantment DENSITY = Enchantments.register("density", new DensityEnchantment());
    public static final Enchantment BREACH = Enchantments.register("breach", new BreachEnchantment());
    public static final Enchantment WIND_BURST = Enchantments.register("wind_burst", new WindBurstEnchantment());
    public static final Enchantment MENDING = Enchantments.register("mending", new MendingEnchantment(Enchantment.properties(ItemTags.DURABILITY_ENCHANTABLE, 2, 1, Enchantment.leveledCost(25, 25), Enchantment.leveledCost(75, 25), 4, EquipmentSlot.values())));
    public static final Enchantment VANISHING_CURSE = Enchantments.register("vanishing_curse", new VanishingCurseEnchantment(Enchantment.properties(ItemTags.VANISHING_ENCHANTABLE, 1, 1, Enchantment.constantCost(25), Enchantment.constantCost(50), 8, EquipmentSlot.values())));

    private static Enchantment register(String name, Enchantment enchantment) {
        return Registry.register(Registries.ENCHANTMENT, name, enchantment);
    }
}

