/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fluids.capability.wrappers;

import javax.annotation.Nonnull;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class BucketPickupHandlerWrapper implements IFluidHandler
{
    private static final Logger LOGGER = LogManager.getLogger();

    protected final BucketPickup bucketPickupHandler;
    protected final Level world;
    protected final BlockPos blockPos;

    public BucketPickupHandlerWrapper(BucketPickup bucketPickupHandler, Level world, BlockPos blockPos)
    {
        this.bucketPickupHandler = bucketPickupHandler;
        this.world = world;
        this.blockPos = blockPos;
    }

    @Override
    public int getTanks()
    {
        return 1;
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank)
    {
        if (tank == 0)
        {
            //Best guess at stored fluid
            FluidState fluidState = world.m_6425_(blockPos);
            if (!fluidState.m_76178_())
            {
                return new FluidStack(fluidState.m_76152_(), FluidAttributes.BUCKET_VOLUME);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return FluidAttributes.BUCKET_VOLUME;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack)
    {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action)
    {
        return 0;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action)
    {
        if (!resource.isEmpty() && FluidAttributes.BUCKET_VOLUME <= resource.getAmount())
        {
            FluidState fluidState = world.m_6425_(blockPos);
            if (!fluidState.m_76178_() && resource.getFluid() == fluidState.m_76152_())
            {
                if (action.execute())
                {
                    ItemStack itemStack = bucketPickupHandler.m_142598_(world, blockPos, world.m_8055_(blockPos));
                    if (itemStack != ItemStack.f_41583_ && itemStack.m_41720_() instanceof BucketItem bucket)
                    {
                        FluidStack extracted = new FluidStack(bucket.getFluid(), FluidAttributes.BUCKET_VOLUME);
                        if (!resource.isFluidEqual(extracted))
                        {
                            //Be loud if something went wrong
                            LOGGER.error("Fluid removed without successfully being picked up. Fluid {} at {} in {} matched requested type, but after performing pickup was {}.",
                                  fluidState.m_76152_().getRegistryName(), blockPos, world.m_46472_().m_135782_(), bucket.getFluid().getRegistryName());
                            return FluidStack.EMPTY;
                        }
                        return extracted;
                    }
                }
                else
                {
                    FluidStack extracted = new FluidStack(fluidState.m_76152_(), FluidAttributes.BUCKET_VOLUME);
                    if (resource.isFluidEqual(extracted))
                    {
                        //Validate NBT matches
                        return extracted;
                    }
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action)
    {
        if (FluidAttributes.BUCKET_VOLUME <= maxDrain)
        {
            FluidState fluidState = world.m_6425_(blockPos);
            if (!fluidState.m_76178_())
            {
                if (action.simulate())
                {
                    return new FluidStack(fluidState.m_76152_(), FluidAttributes.BUCKET_VOLUME);
                }
                ItemStack itemStack = bucketPickupHandler.m_142598_(world, blockPos, world.m_8055_(blockPos));
                if (itemStack != ItemStack.f_41583_ && itemStack.m_41720_() instanceof BucketItem bucket)
                {
                    return new FluidStack(bucket.getFluid(), FluidAttributes.BUCKET_VOLUME);
                }
            }
        }
        return FluidStack.EMPTY;
    }
}
