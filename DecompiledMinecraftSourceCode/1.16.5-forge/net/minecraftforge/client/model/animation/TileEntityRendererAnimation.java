/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.model.animation;

import java.util.Random;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Atlases;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.ModelDataManager;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.common.animation.Event;
import net.minecraftforge.common.animation.IEventHandler;
import net.minecraftforge.common.model.animation.CapabilityAnimation;
import net.minecraftforge.common.model.animation.IAnimationStateMachine;
import net.minecraftforge.common.property.Properties;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Generic {@link TileGameRenderer} that works with the Forge model system and animations.
 */
public class TileEntityRendererAnimation<T extends TileEntity> extends TileEntityRenderer<T> implements IEventHandler<T>
{
    public TileEntityRendererAnimation(TileEntityRendererDispatcher rendererDispatcherIn)
    {
        super(rendererDispatcherIn);
    }

    protected static BlockRendererDispatcher blockRenderer;

    @Override
    public void func_225616_a_(T te, float partialTick, MatrixStack mat, IRenderTypeBuffer renderer, int light, int otherlight)
    {
        LazyOptional<IAnimationStateMachine> cap = te.getCapability(CapabilityAnimation.ANIMATION_CAPABILITY);
        if(!cap.isPresent())
        {
            return;
        }
        if(blockRenderer == null) blockRenderer = Minecraft.func_71410_x().func_175602_ab();
        BlockPos pos = te.func_174877_v();
        IBlockDisplayReader world = MinecraftForgeClient.getRegionRenderCacheOptional(te.func_145831_w(), pos)
            .map(IBlockDisplayReader.class::cast).orElseGet(() -> te.func_145831_w());
        BlockState state = world.func_180495_p(pos);
        IBakedModel model = blockRenderer.func_175023_a().func_178125_b(state);
        IModelData data = model.getModelData(world, pos, state, ModelDataManager.getModelData(te.func_145831_w(), pos));
        if (data.hasProperty(Properties.AnimationProperty))
        {
            @SuppressWarnings("resource")
            float time = Animation.getWorldTime(Minecraft.func_71410_x().field_71441_e, partialTick);
            cap
                .map(asm -> asm.apply(time))
                .ifPresent(pair -> {
                    handleEvents(te, time, pair.getRight());

                    // TODO: caching?
                    data.setData(Properties.AnimationProperty, pair.getLeft());
                    blockRenderer.func_175019_b().renderModel(world, model, state, pos, mat, renderer.getBuffer(Atlases.func_228782_g_()), false, new Random(), 42, light, data);
                });
        }
    }

    @Override
    public void handleEvents(T te, float time, Iterable<Event> pastEvents) {}
}
