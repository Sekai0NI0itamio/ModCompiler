/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.entity.effect;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.AbsorptionStatusEffect;
import net.minecraft.entity.effect.BadOmenStatusEffect;
import net.minecraft.entity.effect.HungerStatusEffect;
import net.minecraft.entity.effect.InfestedStatusEffect;
import net.minecraft.entity.effect.InstantHealthOrDamageStatusEffect;
import net.minecraft.entity.effect.OozingStatusEffect;
import net.minecraft.entity.effect.PoisonStatusEffect;
import net.minecraft.entity.effect.RaidOmenStatusEffect;
import net.minecraft.entity.effect.RegenerationStatusEffect;
import net.minecraft.entity.effect.SaturationStatusEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.WeavingStatusEffect;
import net.minecraft.entity.effect.WindChargedStatusEffect;
import net.minecraft.entity.effect.WitherStatusEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class StatusEffects {
    private static final int DARKNESS_PADDING_DURATION = 22;
    public static final RegistryEntry<StatusEffect> SPEED = StatusEffects.register("speed", new StatusEffect(StatusEffectCategory.BENEFICIAL, 3402751).addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, "91AEAA56-376B-4498-935B-2F7F68070635", 0.2f, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final RegistryEntry<StatusEffect> SLOWNESS = StatusEffects.register("slowness", new StatusEffect(StatusEffectCategory.HARMFUL, 9154528).addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, "7107DE5E-7CE8-4030-940E-514C1F160890", -0.15f, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final RegistryEntry<StatusEffect> HASTE = StatusEffects.register("haste", new StatusEffect(StatusEffectCategory.BENEFICIAL, 14270531).addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED, "AF8B6E3F-3328-4C0A-AA36-5BA2BB9DBEF3", 0.1f, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final RegistryEntry<StatusEffect> MINING_FATIGUE = StatusEffects.register("mining_fatigue", new StatusEffect(StatusEffectCategory.HARMFUL, 4866583).addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED, "55FCED67-E92A-486E-9800-B47F202C4386", -0.1f, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final RegistryEntry<StatusEffect> STRENGTH = StatusEffects.register("strength", new StatusEffect(StatusEffectCategory.BENEFICIAL, 16762624).addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, "648D7064-6A60-4F59-8ABE-C2C23A6DD7A9", 3.0, EntityAttributeModifier.Operation.ADD_VALUE));
    public static final RegistryEntry<StatusEffect> INSTANT_HEALTH = StatusEffects.register("instant_health", new InstantHealthOrDamageStatusEffect(StatusEffectCategory.BENEFICIAL, 16262179, false));
    public static final RegistryEntry<StatusEffect> INSTANT_DAMAGE = StatusEffects.register("instant_damage", new InstantHealthOrDamageStatusEffect(StatusEffectCategory.HARMFUL, 11101546, true));
    public static final RegistryEntry<StatusEffect> JUMP_BOOST = StatusEffects.register("jump_boost", new StatusEffect(StatusEffectCategory.BENEFICIAL, 16646020).addAttributeModifier(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, "C0105BF3-AEF8-46B0-9EBC-92943757CCBE", 1.0, EntityAttributeModifier.Operation.ADD_VALUE));
    public static final RegistryEntry<StatusEffect> NAUSEA = StatusEffects.register("nausea", new StatusEffect(StatusEffectCategory.HARMFUL, 5578058));
    public static final RegistryEntry<StatusEffect> REGENERATION = StatusEffects.register("regeneration", new RegenerationStatusEffect(StatusEffectCategory.BENEFICIAL, 13458603));
    public static final RegistryEntry<StatusEffect> RESISTANCE = StatusEffects.register("resistance", new StatusEffect(StatusEffectCategory.BENEFICIAL, 9520880));
    public static final RegistryEntry<StatusEffect> FIRE_RESISTANCE = StatusEffects.register("fire_resistance", new StatusEffect(StatusEffectCategory.BENEFICIAL, 0xFF9900));
    public static final RegistryEntry<StatusEffect> WATER_BREATHING = StatusEffects.register("water_breathing", new StatusEffect(StatusEffectCategory.BENEFICIAL, 10017472));
    public static final RegistryEntry<StatusEffect> INVISIBILITY = StatusEffects.register("invisibility", new StatusEffect(StatusEffectCategory.BENEFICIAL, 0xF6F6F6));
    public static final RegistryEntry<StatusEffect> BLINDNESS = StatusEffects.register("blindness", new StatusEffect(StatusEffectCategory.HARMFUL, 2039587));
    public static final RegistryEntry<StatusEffect> NIGHT_VISION = StatusEffects.register("night_vision", new StatusEffect(StatusEffectCategory.BENEFICIAL, 12779366));
    public static final RegistryEntry<StatusEffect> HUNGER = StatusEffects.register("hunger", new HungerStatusEffect(StatusEffectCategory.HARMFUL, 5797459));
    public static final RegistryEntry<StatusEffect> WEAKNESS = StatusEffects.register("weakness", new StatusEffect(StatusEffectCategory.HARMFUL, 0x484D48).addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, "22653B89-116E-49DC-9B6B-9971489B5BE5", -4.0, EntityAttributeModifier.Operation.ADD_VALUE));
    public static final RegistryEntry<StatusEffect> POISON = StatusEffects.register("poison", new PoisonStatusEffect(StatusEffectCategory.HARMFUL, 8889187));
    public static final RegistryEntry<StatusEffect> WITHER = StatusEffects.register("wither", new WitherStatusEffect(StatusEffectCategory.HARMFUL, 7561558));
    public static final RegistryEntry<StatusEffect> HEALTH_BOOST = StatusEffects.register("health_boost", new StatusEffect(StatusEffectCategory.BENEFICIAL, 16284963).addAttributeModifier(EntityAttributes.GENERIC_MAX_HEALTH, "5D6F0BA2-1186-46AC-B896-C61C5CEE99CC", 4.0, EntityAttributeModifier.Operation.ADD_VALUE));
    public static final RegistryEntry<StatusEffect> ABSORPTION = StatusEffects.register("absorption", new AbsorptionStatusEffect(StatusEffectCategory.BENEFICIAL, 0x2552A5).addAttributeModifier(EntityAttributes.GENERIC_MAX_ABSORPTION, "EAE29CF0-701E-4ED6-883A-96F798F3DAB5", 4.0, EntityAttributeModifier.Operation.ADD_VALUE));
    public static final RegistryEntry<StatusEffect> SATURATION = StatusEffects.register("saturation", new SaturationStatusEffect(StatusEffectCategory.BENEFICIAL, 16262179));
    public static final RegistryEntry<StatusEffect> GLOWING = StatusEffects.register("glowing", new StatusEffect(StatusEffectCategory.NEUTRAL, 9740385));
    public static final RegistryEntry<StatusEffect> LEVITATION = StatusEffects.register("levitation", new StatusEffect(StatusEffectCategory.HARMFUL, 0xCEFFFF));
    public static final RegistryEntry<StatusEffect> LUCK = StatusEffects.register("luck", new StatusEffect(StatusEffectCategory.BENEFICIAL, 5882118).addAttributeModifier(EntityAttributes.GENERIC_LUCK, "03C3C89D-7037-4B42-869F-B146BCB64D2E", 1.0, EntityAttributeModifier.Operation.ADD_VALUE));
    public static final RegistryEntry<StatusEffect> UNLUCK = StatusEffects.register("unluck", new StatusEffect(StatusEffectCategory.HARMFUL, 12624973).addAttributeModifier(EntityAttributes.GENERIC_LUCK, "CC5AF142-2BD2-4215-B636-2605AED11727", -1.0, EntityAttributeModifier.Operation.ADD_VALUE));
    public static final RegistryEntry<StatusEffect> SLOW_FALLING = StatusEffects.register("slow_falling", new StatusEffect(StatusEffectCategory.BENEFICIAL, 15978425));
    public static final RegistryEntry<StatusEffect> CONDUIT_POWER = StatusEffects.register("conduit_power", new StatusEffect(StatusEffectCategory.BENEFICIAL, 1950417));
    public static final RegistryEntry<StatusEffect> DOLPHINS_GRACE = StatusEffects.register("dolphins_grace", new StatusEffect(StatusEffectCategory.BENEFICIAL, 8954814));
    public static final RegistryEntry<StatusEffect> BAD_OMEN = StatusEffects.register("bad_omen", new BadOmenStatusEffect(StatusEffectCategory.NEUTRAL, 745784).applySound(SoundEvents.EVENT_MOB_EFFECT_BAD_OMEN));
    public static final RegistryEntry<StatusEffect> HERO_OF_THE_VILLAGE = StatusEffects.register("hero_of_the_village", new StatusEffect(StatusEffectCategory.BENEFICIAL, 0x44FF44));
    public static final RegistryEntry<StatusEffect> DARKNESS = StatusEffects.register("darkness", new StatusEffect(StatusEffectCategory.HARMFUL, 2696993).fadeTicks(22));
    public static final RegistryEntry<StatusEffect> TRIAL_OMEN = StatusEffects.register("trial_omen", new StatusEffect(StatusEffectCategory.NEUTRAL, 0x16A6A6, ParticleTypes.TRIAL_OMEN).applySound(SoundEvents.EVENT_MOB_EFFECT_TRIAL_OMEN).requires(FeatureFlags.UPDATE_1_21));
    public static final RegistryEntry<StatusEffect> RAID_OMEN = StatusEffects.register("raid_omen", new RaidOmenStatusEffect(StatusEffectCategory.NEUTRAL, 14565464, ParticleTypes.RAID_OMEN).applySound(SoundEvents.EVENT_MOB_EFFECT_RAID_OMEN).requires(FeatureFlags.UPDATE_1_21));
    public static final RegistryEntry<StatusEffect> WIND_CHARGED = StatusEffects.register("wind_charged", new WindChargedStatusEffect(StatusEffectCategory.HARMFUL, 12438015).requires(FeatureFlags.UPDATE_1_21));
    public static final RegistryEntry<StatusEffect> WEAVING = StatusEffects.register("weaving", new WeavingStatusEffect(StatusEffectCategory.HARMFUL, 7891290, random -> MathHelper.nextBetween(random, 2, 3)).requires(FeatureFlags.UPDATE_1_21));
    public static final RegistryEntry<StatusEffect> OOZING = StatusEffects.register("oozing", new OozingStatusEffect(StatusEffectCategory.HARMFUL, 10092451, random -> 2).requires(FeatureFlags.UPDATE_1_21));
    public static final RegistryEntry<StatusEffect> INFESTED = StatusEffects.register("infested", new InfestedStatusEffect(StatusEffectCategory.HARMFUL, 9214860, 0.1f, random -> MathHelper.nextBetween(random, 1, 2)).requires(FeatureFlags.UPDATE_1_21));

    private static RegistryEntry<StatusEffect> register(String id, StatusEffect statusEffect) {
        return Registry.registerReference(Registries.STATUS_EFFECT, new Identifier(id), statusEffect);
    }

    public static RegistryEntry<StatusEffect> registerAndGetDefault(Registry<StatusEffect> registry) {
        return SPEED;
    }
}

