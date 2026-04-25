/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.extensions;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.blaze3d.matrix.MatrixStack;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraftforge.client.model.data.IModelData;

public interface IForgeBakedModel
{
    default IBakedModel getBakedModel()
    {
        return (IBakedModel) this;
    }

    @Nonnull
    default List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull Random rand, @Nonnull IModelData extraData)
    {
        return getBakedModel().func_200117_a(state, side, rand);
    }

    default boolean isAmbientOcclusion(BlockState state) { return getBakedModel().func_177555_b(); }

    /**
     * Override to tell the new model loader that it shouldn't wrap this model
     */
    default boolean doesHandlePerspectives() { return false; }

    /*
     * Returns the pair of the model for the given perspective, and the matrix that
     * should be applied to the GL state before rendering it (matrix may be null).
     */
    default IBakedModel handlePerspective(ItemCameraTransforms.TransformType cameraTransformType, MatrixStack mat)
    {
        return net.minecraftforge.client.ForgeHooksClient.handlePerspective(getBakedModel(), cameraTransformType, mat);
    }

    default @Nonnull IModelData getModelData(@Nonnull IBlockDisplayReader world, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull IModelData tileData)
    {
        return tileData;
    }

    default TextureAtlasSprite getParticleTexture(@Nonnull IModelData data)
    {
        return getBakedModel().func_177554_e();
    }

    /**
     * Override to true, to tell forge to call the getLayerModels method below.
     */
    default boolean isLayered()
    {
        return false;
    }

    /**
     * If {@see isLayered()} returns true, this is called to get the list of layers to draw.
     */
    default List<Pair<IBakedModel, RenderType>> getLayerModels(ItemStack itemStack, boolean fabulous)
    {
        return Collections.singletonList(Pair.of(getBakedModel(), RenderTypeLookup.func_239219_a_(itemStack, fabulous)));
    }
}
