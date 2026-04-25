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

package net.minecraftforge.fluids;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public abstract class ForgeFlowingFluid extends FlowingFluid
{
    private final Supplier<? extends Fluid> flowing;
    private final Supplier<? extends Fluid> still;
    @Nullable
    private final Supplier<? extends Item> bucket;
    @Nullable
    private final Supplier<? extends LiquidBlock> block;
    private final FluidAttributes.Builder builder;
    private final boolean canMultiply;
    private final int slopeFindDistance;
    private final int levelDecreasePerBlock;
    private final float explosionResistance;
    private final int tickRate;

    protected ForgeFlowingFluid(Properties properties)
    {
        this.flowing = properties.flowing;
        this.still = properties.still;
        this.builder = properties.attributes;
        this.canMultiply = properties.canMultiply;
        this.bucket = properties.bucket;
        this.block = properties.block;
        this.slopeFindDistance = properties.slopeFindDistance;
        this.levelDecreasePerBlock = properties.levelDecreasePerBlock;
        this.explosionResistance = properties.explosionResistance;
        this.tickRate = properties.tickRate;
    }

    @Override
    public Fluid m_5615_()
    {
        return flowing.get();
    }

    @Override
    public Fluid m_5613_()
    {
        return still.get();
    }

    @Override
    protected boolean m_6760_()
    {
        return canMultiply;
    }

    @Override
    protected void m_7456_(LevelAccessor worldIn, BlockPos pos, BlockState state)
    {
        BlockEntity blockEntity = state.m_155947_() ? worldIn.m_7702_(pos) : null;
        Block.m_49892_(state, worldIn, pos, blockEntity);
    }

    @Override
    protected int m_6719_(LevelReader worldIn)
    {
        return slopeFindDistance;
    }

    @Override
    protected int m_6713_(LevelReader worldIn)
    {
        return levelDecreasePerBlock;
    }

    @Override
    public Item m_6859_()
    {
        return bucket != null ? bucket.get() : Items.f_41852_;
    }

    @Override
    protected boolean m_5486_(FluidState state, BlockGetter world, BlockPos pos, Fluid fluidIn, Direction direction)
    {
        // Based on the water implementation, may need to be overriden for mod fluids that shouldn't behave like water.
        return direction == Direction.DOWN && !m_6212_(fluidIn);
    }

    @Override
    public int m_6718_(LevelReader world)
    {
        return tickRate;
    }

    @Override
    protected float m_6752_()
    {
        return explosionResistance;
    }

    @Override
    protected BlockState m_5804_(FluidState state)
    {
        if (block != null)
            return block.get().m_49966_().m_61124_(LiquidBlock.f_54688_, m_76092_(state));
        return Blocks.f_50016_.m_49966_();
    }

    @Override
    public boolean m_6212_(Fluid fluidIn) {
        return fluidIn == still.get() || fluidIn == flowing.get();
    }

    @Override
    protected FluidAttributes createAttributes()
    {
        return builder.build(this);
    }

    public static class Flowing extends ForgeFlowingFluid
    {
        public Flowing(Properties properties)
        {
            super(properties);
            m_76142_(m_76144_().m_61090_().m_61124_(f_75948_, 7));
        }

        protected void m_7180_(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.m_7180_(builder);
            builder.m_61104_(f_75948_);
        }

        public int m_7430_(FluidState state) {
            return state.m_61143_(f_75948_);
        }

        public boolean m_7444_(FluidState state) {
            return false;
        }
    }

    public static class Source extends ForgeFlowingFluid
    {
        public Source(Properties properties)
        {
            super(properties);
        }

        public int m_7430_(FluidState state) {
            return 8;
        }

        public boolean m_7444_(FluidState state) {
            return true;
        }
    }

    public static class Properties
    {
        private Supplier<? extends Fluid> still;
        private Supplier<? extends Fluid> flowing;
        private FluidAttributes.Builder attributes;
        private boolean canMultiply;
        private Supplier<? extends Item> bucket;
        private Supplier<? extends LiquidBlock> block;
        private int slopeFindDistance = 4;
        private int levelDecreasePerBlock = 1;
        private float explosionResistance = 1;
        private int tickRate = 5;

        public Properties(Supplier<? extends Fluid> still, Supplier<? extends Fluid> flowing, FluidAttributes.Builder attributes)
        {
            this.still = still;
            this.flowing = flowing;
            this.attributes = attributes;
        }

        public Properties canMultiply()
        {
            canMultiply = true;
            return this;
        }

        public Properties bucket(Supplier<? extends Item> bucket)
        {
            this.bucket = bucket;
            return this;
        }

        public Properties block(Supplier<? extends LiquidBlock> block)
        {
            this.block = block;
            return this;
        }

        public Properties slopeFindDistance(int slopeFindDistance)
        {
            this.slopeFindDistance = slopeFindDistance;
            return this;
        }

        public Properties levelDecreasePerBlock(int levelDecreasePerBlock)
        {
            this.levelDecreasePerBlock = levelDecreasePerBlock;
            return this;
        }

        public Properties explosionResistance(float explosionResistance)
        {
            this.explosionResistance = explosionResistance;
            return this;
        }

        public Properties tickRate(int tickRate)
        {
            this.tickRate = tickRate;
            return this;
        }
    }
}
