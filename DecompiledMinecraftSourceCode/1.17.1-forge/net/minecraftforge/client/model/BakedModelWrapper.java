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

package net.minecraftforge.client.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraftforge.client.model.data.IModelData;

public abstract class BakedModelWrapper<T extends BakedModel> implements BakedModel
{
    protected final T originalModel;

    public BakedModelWrapper(T originalModel)
    {
        this.originalModel = originalModel;
    }

    @Override
    public List<BakedQuad> m_6840_(@Nullable BlockState state, @Nullable Direction side, Random rand)
    {
        return originalModel.m_6840_(state, side, rand);
    }

    @Override
    public boolean m_7541_()
    {
        return originalModel.m_7541_();
    }

    @Override
    public boolean isAmbientOcclusion(BlockState state)
    {
        return originalModel.isAmbientOcclusion(state);
    }

    @Override
    public boolean m_7539_()
    {
        return originalModel.m_7539_();
    }

    @Override
    public boolean m_7547_()
    {
        return originalModel.m_7547_();
    }

    @Override
    public boolean m_7521_()
    {
        return originalModel.m_7521_();
    }

    @Override
    public TextureAtlasSprite m_6160_()
    {
        return originalModel.m_6160_();
    }

    @Override
    public ItemTransforms m_7442_()
    {
        return originalModel.m_7442_();
    }

    @Override
    public ItemOverrides m_7343_()
    {
        return originalModel.m_7343_();
    }

    @Override
    public boolean doesHandlePerspectives()
    {
        return originalModel.doesHandlePerspectives();
    }

    @Override
    public BakedModel handlePerspective(ItemTransforms.TransformType cameraTransformType, PoseStack poseStack)
    {
        return originalModel.handlePerspective(cameraTransformType, poseStack);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(@Nonnull IModelData data)
    {
        return originalModel.getParticleIcon(data);
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull Random rand, @Nonnull IModelData extraData)
    {
        return originalModel.getQuads(state, side, rand, extraData);
    }

    @Nonnull
    @Override
    public IModelData getModelData(@Nonnull BlockAndTintGetter world, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull IModelData tileData)
    {
        return originalModel.getModelData(world, pos, state, tileData);
    }
}
