/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.item;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.enchantment.DensityEnchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

public class MaceItem
extends Item {
    private static final int ATTACK_DAMAGE_MODIFIER_VALUE = 6;
    private static final float ATTACK_SPEED_MODIFIER_VALUE = -2.4f;
    private static final float MINING_SPEED_MULTIPLIER = 1.5f;
    private static final float field_50141 = 5.0f;
    public static final float KNOCKBACK_RANGE = 3.5f;
    private static final float KNOCKBACK_POWER = 0.7f;
    private static final float FALL_DISTANCE_MULTIPLIER = 3.0f;

    public MaceItem(Item.Settings settings) {
        super(settings);
    }

    public static AttributeModifiersComponent createAttributeModifiers() {
        return AttributeModifiersComponent.builder().add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier", 6.0, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND).add(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Weapon modifier", -2.4f, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND).build();
    }

    public static ToolComponent createToolComponent() {
        return new ToolComponent(List.of(), 1.0f, 2);
    }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return !miner.isCreative();
    }

    @Override
    public int getEnchantability() {
        return 15;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        ServerPlayerEntity serverPlayerEntity;
        stack.damage(1, attacker, EquipmentSlot.MAINHAND);
        if (attacker instanceof ServerPlayerEntity && MaceItem.shouldDealAdditionalDamage(serverPlayerEntity = (ServerPlayerEntity)attacker)) {
            ServerWorld serverWorld = (ServerWorld)attacker.getWorld();
            serverPlayerEntity.currentExplosionImpactPos = serverPlayerEntity.getPos();
            serverPlayerEntity.ignoreFallDamageFromCurrentExplosion = true;
            serverPlayerEntity.setVelocity(serverPlayerEntity.getVelocity().withAxis(Direction.Axis.Y, 0.01f));
            serverPlayerEntity.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(serverPlayerEntity));
            if (target.isOnGround()) {
                serverPlayerEntity.setSpawnExtraParticlesOnFall(true);
                SoundEvent soundEvent = serverPlayerEntity.fallDistance > 5.0f ? SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY : SoundEvents.ITEM_MACE_SMASH_GROUND;
                serverWorld.playSound(null, serverPlayerEntity.getX(), serverPlayerEntity.getY(), serverPlayerEntity.getZ(), soundEvent, serverPlayerEntity.getSoundCategory(), 1.0f, 1.0f);
            } else {
                serverWorld.playSound(null, serverPlayerEntity.getX(), serverPlayerEntity.getY(), serverPlayerEntity.getZ(), SoundEvents.ITEM_MACE_SMASH_AIR, serverPlayerEntity.getSoundCategory(), 1.0f, 1.0f);
            }
            MaceItem.knockbackNearbyEntities(serverWorld, serverPlayerEntity, target);
            return true;
        }
        return false;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(Items.BREEZE_ROD);
    }

    @Override
    public float getBonusAttackDamage(PlayerEntity player, float baseAttackDamage) {
        int i = EnchantmentHelper.getEquipmentLevel(Enchantments.DENSITY, player);
        float f = DensityEnchantment.getDamage(i, player.fallDistance);
        return MaceItem.shouldDealAdditionalDamage(player) ? 3.0f * player.fallDistance + f : 0.0f;
    }

    private static void knockbackNearbyEntities(World world, PlayerEntity player, Entity attacked) {
        world.syncWorldEvent(WorldEvents.SMASH_ATTACK, attacked.getSteppingPos(), 750);
        world.getEntitiesByClass(LivingEntity.class, attacked.getBoundingBox().expand(3.5), MaceItem.getKnockbackPredicate(player, attacked)).forEach(entity -> {
            Vec3d vec3d = entity.getPos().subtract(attacked.getPos());
            double d = MaceItem.getKnockback(player, entity, vec3d);
            Vec3d vec3d2 = vec3d.normalize().multiply(d);
            if (d > 0.0) {
                entity.addVelocity(vec3d2.x, 0.7f, vec3d2.z);
            }
        });
    }

    private static Predicate<LivingEntity> getKnockbackPredicate(PlayerEntity player, Entity attacked) {
        return entity -> {
            ArmorStandEntity armorStandEntity;
            boolean bl = !entity.isSpectator();
            boolean bl2 = entity != player && entity != attacked;
            boolean bl3 = !player.isTeammate((Entity)entity);
            boolean bl4 = !(entity instanceof ArmorStandEntity) || !(armorStandEntity = (ArmorStandEntity)entity).isMarker();
            boolean bl5 = attacked.squaredDistanceTo((Entity)entity) <= Math.pow(3.5, 2.0);
            return bl && bl2 && bl3 && bl4 && bl5;
        };
    }

    private static double getKnockback(PlayerEntity player, LivingEntity attacked, Vec3d distance) {
        return (3.5 - distance.length()) * (double)0.7f * (double)(player.fallDistance > 5.0f ? 2 : 1) * (1.0 - attacked.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
    }

    public static boolean shouldDealAdditionalDamage(PlayerEntity attacker) {
        return attacker.fallDistance > 1.5f && !attacker.isFallFlying();
    }
}

