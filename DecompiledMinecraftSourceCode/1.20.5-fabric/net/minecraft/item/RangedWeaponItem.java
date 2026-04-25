/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.Unit;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public abstract class RangedWeaponItem
extends Item {
    public static final Predicate<ItemStack> BOW_PROJECTILES = stack -> stack.isIn(ItemTags.ARROWS);
    public static final Predicate<ItemStack> CROSSBOW_HELD_PROJECTILES = BOW_PROJECTILES.or(stack -> stack.isOf(Items.FIREWORK_ROCKET));

    public RangedWeaponItem(Item.Settings settings) {
        super(settings);
    }

    public Predicate<ItemStack> getHeldProjectiles() {
        return this.getProjectiles();
    }

    public abstract Predicate<ItemStack> getProjectiles();

    public static ItemStack getHeldProjectile(LivingEntity entity, Predicate<ItemStack> predicate) {
        if (predicate.test(entity.getStackInHand(Hand.OFF_HAND))) {
            return entity.getStackInHand(Hand.OFF_HAND);
        }
        if (predicate.test(entity.getStackInHand(Hand.MAIN_HAND))) {
            return entity.getStackInHand(Hand.MAIN_HAND);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getEnchantability() {
        return 1;
    }

    public abstract int getRange();

    protected void shootAll(World world, LivingEntity shooter, Hand hand, ItemStack stack, List<ItemStack> projectiles, float speed, float divergence, boolean critical, @Nullable LivingEntity target) {
        float f = 10.0f;
        float g = projectiles.size() == 1 ? 0.0f : 20.0f / (float)(projectiles.size() - 1);
        float h = (float)((projectiles.size() - 1) % 2) * g / 2.0f;
        float i = 1.0f;
        for (int j = 0; j < projectiles.size(); ++j) {
            ItemStack itemStack = projectiles.get(j);
            if (itemStack.isEmpty()) continue;
            float k = h + i * (float)((j + 1) / 2) * g;
            i = -i;
            stack.damage(this.getWeaponStackDamage(itemStack), shooter, LivingEntity.getSlotForHand(hand));
            ProjectileEntity projectileEntity = this.createArrowEntity(world, shooter, stack, itemStack, critical);
            this.shoot(shooter, projectileEntity, j, speed, divergence, k, target);
            world.spawnEntity(projectileEntity);
        }
    }

    protected int getWeaponStackDamage(ItemStack projectile) {
        return 1;
    }

    protected abstract void shoot(LivingEntity var1, ProjectileEntity var2, int var3, float var4, float var5, float var6, @Nullable LivingEntity var7);

    protected ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical) {
        int k;
        int j;
        int i;
        ArrowItem arrowItem;
        Item item = projectileStack.getItem();
        ArrowItem arrowItem2 = item instanceof ArrowItem ? (arrowItem = (ArrowItem)item) : (ArrowItem)Items.ARROW;
        PersistentProjectileEntity persistentProjectileEntity = arrowItem2.createArrow(world, projectileStack, shooter);
        if (critical) {
            persistentProjectileEntity.setCritical(true);
        }
        if ((i = EnchantmentHelper.getLevel(Enchantments.POWER, weaponStack)) > 0) {
            persistentProjectileEntity.setDamage(persistentProjectileEntity.getDamage() + (double)i * 0.5 + 0.5);
        }
        if ((j = EnchantmentHelper.getLevel(Enchantments.PUNCH, weaponStack)) > 0) {
            persistentProjectileEntity.setPunch(j);
        }
        if (EnchantmentHelper.getLevel(Enchantments.FLAME, weaponStack) > 0) {
            persistentProjectileEntity.setOnFireFor(100);
        }
        if ((k = EnchantmentHelper.getLevel(Enchantments.PIERCING, weaponStack)) > 0) {
            persistentProjectileEntity.setPierceLevel((byte)k);
        }
        return persistentProjectileEntity;
    }

    protected static boolean isInfinity(ItemStack weaponStack, ItemStack projectileStack, boolean creative) {
        return creative || projectileStack.isOf(Items.ARROW) && EnchantmentHelper.getLevel(Enchantments.INFINITY, weaponStack) > 0;
    }

    protected static List<ItemStack> load(ItemStack weaponStack, ItemStack projectileStack, LivingEntity shooter) {
        if (projectileStack.isEmpty()) {
            return List.of();
        }
        int i = EnchantmentHelper.getLevel(Enchantments.MULTISHOT, weaponStack);
        int j = i == 0 ? 1 : 3;
        ArrayList<ItemStack> list = new ArrayList<ItemStack>(j);
        ItemStack itemStack = projectileStack.copy();
        for (int k = 0; k < j; ++k) {
            list.add(RangedWeaponItem.getProjectile(weaponStack, k == 0 ? projectileStack : itemStack, shooter, k > 0));
        }
        return list;
    }

    protected static ItemStack getProjectile(ItemStack weaponStack, ItemStack projectileStack, LivingEntity shooter, boolean multishot) {
        boolean bl;
        boolean bl2 = bl = !multishot && !RangedWeaponItem.isInfinity(weaponStack, projectileStack, shooter.isInCreativeMode());
        if (!bl) {
            ItemStack itemStack = projectileStack.copyWithCount(1);
            itemStack.set(DataComponentTypes.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            return itemStack;
        }
        ItemStack itemStack = projectileStack.split(1);
        if (projectileStack.isEmpty() && shooter instanceof PlayerEntity) {
            PlayerEntity playerEntity = (PlayerEntity)shooter;
            playerEntity.getInventory().removeOne(projectileStack);
        }
        return itemStack;
    }
}

