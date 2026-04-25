/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fluids.capability.wrappers;

import javax.annotation.Nonnull;
import net.minecraft.block.IBucketPickupHandler;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class BucketPickupHandlerWrapper implements IFluidHandler
{
    private static final Logger LOGGER = LogManager.getLogger();

    protected final IBucketPickupHandler bucketPickupHandler;
    protected final World world;
    protected final BlockPos blockPos;

    public BucketPickupHandlerWrapper(IBucketPickupHandler bucketPickupHandler, World world, BlockPos blockPos)
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
            FluidState fluidState = world.func_204610_c(blockPos);
            if (!fluidState.func_206888_e())
            {
                return new FluidStack(fluidState.func_206886_c(), FluidAttributes.BUCKET_VOLUME);
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
            FluidState fluidState = world.func_204610_c(blockPos);
            if (!fluidState.func_206888_e() && resource.getFluid() == fluidState.func_206886_c())
            {
                if (action.execute())
                {
                    Fluid fluid = bucketPickupHandler.func_204508_a(world, blockPos, world.func_180495_p(blockPos));
                    if (fluid != Fluids.field_204541_a)
                    {
                        FluidStack extracted = new FluidStack(fluid, FluidAttributes.BUCKET_VOLUME);
                        if (!resource.isFluidEqual(extracted))
                        {
                            //Be loud if something went wrong
                            LOGGER.error("Fluid removed without successfully being picked up. Fluid {} at {} in {} matched requested type, but after performing pickup was {}.",
                                  fluidState.func_206886_c().getRegistryName(), blockPos, world.func_234923_W_().func_240901_a_(), fluid.getRegistryName());
                            return FluidStack.EMPTY;
                        }
                        return extracted;
                    }
                }
                else
                {
                    FluidStack extracted = new FluidStack(fluidState.func_206886_c(), FluidAttributes.BUCKET_VOLUME);
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
            FluidState fluidState = world.func_204610_c(blockPos);
            if (!fluidState.func_206888_e())
            {
                if (action.simulate())
                {
                    return new FluidStack(fluidState.func_206886_c(), FluidAttributes.BUCKET_VOLUME);
                }
                Fluid fluid = bucketPickupHandler.func_204508_a(world, blockPos, world.func_180495_p(blockPos));
                if (fluid != Fluids.field_204541_a)
                {
                    return new FluidStack(fluid, FluidAttributes.BUCKET_VOLUME);
                }
            }
        }
        return FluidStack.EMPTY;
    }
}
