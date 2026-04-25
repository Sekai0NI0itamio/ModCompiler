/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.extensions;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

public interface IForgePlayer
{

    private Player self()
    {
        return (Player) this;
    }

    /**
     * The attack range is increased by 3 for creative players, unless it is currently zero, which disables attacks.
     * @return The attack range of this player.
     */
    default double getAttackRange()
    {
        double range = self().m_21133_(ForgeMod.ATTACK_RANGE.get());
        return range == 0 ? 0 : range + (self().m_7500_() ? 3 : 0);
    }

    /**
     * The reach distance is increased by 0.5 for creative players, unless it is currently zero, which disables interactions.
     * @return The reach distance of this player.
     */
    default double getReachDistance()
    {
        double reach = self().m_21133_(ForgeMod.REACH_DISTANCE.get());
        return reach == 0 ? 0 : reach + (self().m_7500_() ? 0.5 : 0);
    }

    /**
     * Checks if the player can attack the passed entity.<br>
     * On the server, additional leniency is added to account for movement/lag.
     * @param entity The entity being range-checked.
     * @param padding Extra validation distance.
     * @return If the player can attack the passed entity.
     */
    default boolean canHit(Entity entity, double padding) // Do not rename to canAttack - will conflict with LivingEntity#canAttack
    {
        return isCloseEnough(entity, getAttackRange() + padding);
    }

    /**
     * Checks if the player can reach (right-click) the passed entity.<br>
     * On the server, additional leniency is added to account for movement/lag.
     * @param entity The entity being range-checked.
     * @param padding Extra validation distance.
     * @return If the player can interact with the passed entity.
     */
    default boolean canInteractWith(Entity entity, double padding)
    {
        return isCloseEnough(entity, getReachDistance() + padding);
    }

    /**
     * Utility check to see if the player is close enough to a target entity.
     * @param entity The entity being checked against
     * @param dist The max distance allowed
     * @return If the eye-to-center distance between this player and the passed entity is less than dist.
     */
    default boolean isCloseEnough(Entity entity, double dist)
    {
        Vec3 eye = self().m_146892_();
        Vec3 targetCenter = entity.m_20318_(1.0F).m_82520_(0, entity.m_20206_() / 2, 0);
        Optional<Vec3> hit = entity.m_142469_().m_82371_(eye, targetCenter); //This hit should almost always be present, but we have a fallback just in case.  It likely indicates lag if it is not present.
        return (hit.isPresent() ? eye.m_82557_(hit.get()) : self().m_20280_(entity)) < dist * dist;
    }

    /**
     * Checks if the player can reach (right-click) a block.<br>
     * On the server, additional leniency is added to account for movement/lag.
     * @param pos The position being range-checked.
     * @param padding Extra validation distance.
     * @return If the player can interact with this location.
     */
    default boolean canInteractWith(BlockPos pos, double padding)
    {
        double reach = getReachDistance() + padding;
        return self().m_146892_().m_82531_(pos.m_123341_() + 0.5D, pos.m_123342_() + 0.5D, pos.m_123343_() + 0.5D) < reach * reach;
    }

}
