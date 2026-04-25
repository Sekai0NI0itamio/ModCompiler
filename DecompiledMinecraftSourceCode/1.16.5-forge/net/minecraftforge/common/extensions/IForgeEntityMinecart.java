/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.extensions;

import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.IMinecartCollisionHandler;

public interface IForgeEntityMinecart
{
    public static float DEFAULT_MAX_SPEED_AIR_LATERAL = 0.4f;
    public static float DEFAULT_MAX_SPEED_AIR_VERTICAL = -1.0f;
    public static double DEFAULT_AIR_DRAG = 0.95f;
    /** TODO: Remove 1.17 */
    public static IMinecartCollisionHandler COLLISIONS = null;

    default AbstractMinecartEntity getMinecart() {
        return (AbstractMinecartEntity)this;
    }

    /**
     * Gets the current global Minecart Collision handler if none
     * is registered, returns null
     * TODO: remove 1.17
     * @return The collision handler or null
     */
    default IMinecartCollisionHandler getCollisionHandler() {
        return COLLISIONS;
    }

    /**
     * Internal, returns the current spot to look for the attached rail.
     */
    default BlockPos getCurrentRailPosition()
    {
        int x = MathHelper.func_76128_c(getMinecart().func_226277_ct_());
        int y = MathHelper.func_76128_c(getMinecart().func_226278_cu_());
        int z = MathHelper.func_76128_c(getMinecart().func_226281_cx_());
        BlockPos pos = new BlockPos(x, y, z);
        if (getMinecart().field_70170_p.func_180495_p(pos.func_177977_b()).func_235714_a_(BlockTags.field_203437_y)) pos = pos.func_177977_b();
        return pos;
    }

    double getMaxSpeedWithRail();

    /**
     * Moved to allow overrides.
     * This code handles minecart movement and speed capping when on a rail.
     */
    void moveMinecartOnRail(BlockPos pos);

    /**
     * This function returns an ItemStack that represents this cart.
     * This should be an ItemStack that can be used by the player to place the cart,
     * but is not necessary the item the cart drops when destroyed.
     * @return An ItemStack that can be used to place the cart.
     */
    default ItemStack getCartItem()
    {
        switch (getMinecart().func_184264_v())
        {
            case FURNACE: return new ItemStack(Items.field_151109_aJ);
            case CHEST:   return new ItemStack(Items.field_151108_aI);
            case TNT:     return new ItemStack(Items.field_151142_bV);
            case HOPPER:  return new ItemStack(Items.field_151140_bW);
            case COMMAND_BLOCK: return new ItemStack(Items.field_151095_cc);
            default:      return new ItemStack(Items.field_151143_au);
        }
    }

    /**
     * Returns true if this cart can currently use rails.
     * This function is mainly used to gracefully detach a minecart from a rail.
     * @return True if the minecart can use rails.
     */
    boolean canUseRail();

    /**
     * Set whether the minecart can use rails.
     * This function is mainly used to gracefully detach a minecart from a rail.
     * @param use Whether the minecart can currently use rails.
     */
    void setCanUseRail(boolean use);

    /**
     * Return false if this cart should not call onMinecartPass() and should ignore Powered Rails.
     * @return True if this cart should call onMinecartPass().
     */
    default boolean shouldDoRailFunctions() {
        return true;
    }

    /**
     * Returns true if this cart is self propelled.
     * @return True if powered.
     */
    default boolean isPoweredCart() {
        return getMinecart().func_184264_v() == AbstractMinecartEntity.Type.FURNACE;
    }

    /**
     * Returns true if this cart can be ridden by an Entity.
     * @return True if this cart can be ridden.
     */
    default boolean canBeRidden() {
        return getMinecart().func_184264_v() == AbstractMinecartEntity.Type.RIDEABLE;
    }

    /**
     * Returns the carts max speed when traveling on rails. Carts going faster
     * than 1.1 cause issues with chunk loading. Carts cant traverse slopes or
     * corners at greater than 0.5 - 0.6. This value is compared with the rails
     * max speed and the carts current speed cap to determine the carts current
     * max speed. A normal rail's max speed is 0.4.
     *
     * @return Carts max speed.
     */
    default float getMaxCartSpeedOnRail() {
        return 1.2f;
    }

    /**
     * Returns the current speed cap for the cart when traveling on rails. This
     * functions differs from getMaxCartSpeedOnRail() in that it controls
     * current movement and cannot be overridden. The value however can never be
     * higher than getMaxCartSpeedOnRail().
     *
     * @return
     */
    float getCurrentCartSpeedCapOnRail();
    void setCurrentCartSpeedCapOnRail(float value);
    float getMaxSpeedAirLateral();
    void setMaxSpeedAirLateral(float value);
    float getMaxSpeedAirVertical();
    void setMaxSpeedAirVertical(float value);
    double getDragAir();
    void setDragAir(double value);

    default double getSlopeAdjustment() {
        return 0.0078125D;
    }

    /**
     * Called from Detector Rails to retrieve a redstone power level for comparators.
     */
    default int getComparatorLevel() {
        return -1;
    }
}
