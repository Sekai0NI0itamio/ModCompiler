/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.model;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IModelGeometry;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;

public class SeparatePerspectiveModel implements IModelGeometry<SeparatePerspectiveModel>
{
    private final BlockModel baseModel;
    private final ImmutableMap<ItemTransforms.TransformType, BlockModel> perspectives;

    public SeparatePerspectiveModel(BlockModel baseModel, ImmutableMap<ItemTransforms.TransformType, BlockModel> perspectives)
    {
        this.baseModel = baseModel;
        this.perspectives = perspectives;
    }

    @Override
    public net.minecraft.client.resources.model.BakedModel bake(IModelConfiguration owner, ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform, ItemOverrides overrides, ResourceLocation modelLocation)
    {
        return new BakedModel(
                owner.useSmoothLighting(), owner.isShadedInGui(), owner.isSideLit(),
                spriteGetter.apply(owner.resolveTexture("particle")), overrides,
                baseModel.m_111449_(bakery, baseModel, spriteGetter, modelTransform, modelLocation, owner.isSideLit()),
                ImmutableMap.copyOf(Maps.transformValues(perspectives, value -> {
                    return value.m_111449_(bakery, value, spriteGetter, modelTransform, modelLocation, owner.isSideLit());
                }))
        );
    }

    @Override
    public Collection<Material> getTextures(IModelConfiguration owner, Function<ResourceLocation, UnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors)
    {
        Set<Material> textures = Sets.newHashSet();
        textures.addAll(baseModel.m_5500_(modelGetter, missingTextureErrors));
        for(BlockModel model : perspectives.values())
            textures.addAll(model.m_5500_(modelGetter, missingTextureErrors));
        return textures;
    }

    public static class BakedModel implements net.minecraft.client.resources.model.BakedModel
    {
        private final boolean isAmbientOcclusion;
        private final boolean isGui3d;
        private final boolean isSideLit;
        private final TextureAtlasSprite particle;
        private final ItemOverrides overrides;
        private final net.minecraft.client.resources.model.BakedModel baseModel;
        private final ImmutableMap<ItemTransforms.TransformType, net.minecraft.client.resources.model.BakedModel> perspectives;

        public BakedModel(boolean isAmbientOcclusion, boolean isGui3d, boolean isSideLit, TextureAtlasSprite particle, ItemOverrides overrides, net.minecraft.client.resources.model.BakedModel baseModel, ImmutableMap<ItemTransforms.TransformType, net.minecraft.client.resources.model.BakedModel> perspectives)
        {
            this.isAmbientOcclusion = isAmbientOcclusion;
            this.isGui3d = isGui3d;
            this.isSideLit = isSideLit;
            this.particle = particle;
            this.overrides = overrides;
            this.baseModel = baseModel;
            this.perspectives = perspectives;
        }

        @Override
        public List<BakedQuad> m_6840_(@Nullable BlockState state, @Nullable Direction side, Random rand)
        {
            return Collections.emptyList();
        }

        @Override
        public boolean m_7541_()
        {
            return isAmbientOcclusion;
        }

        @Override
        public boolean m_7539_()
        {
            return isGui3d;
        }

        @Override
        public boolean m_7547_()
        {
            return isSideLit;
        }

        @Override
        public boolean m_7521_()
        {
            return false;
        }

        @Override
        public TextureAtlasSprite m_6160_()
        {
            return particle;
        }

        @Override
        public ItemOverrides m_7343_()
        {
            return overrides;
        }

        @Override
        public boolean doesHandlePerspectives()
        {
            return true;
        }

        @Override
        public ItemTransforms m_7442_()
        {
            return ItemTransforms.f_111786_;
        }

        @Override
        public net.minecraft.client.resources.model.BakedModel handlePerspective(ItemTransforms.TransformType cameraTransformType, PoseStack poseStack)
        {
            if (perspectives.containsKey(cameraTransformType))
            {
                net.minecraft.client.resources.model.BakedModel p = perspectives.get(cameraTransformType);
                return p.handlePerspective(cameraTransformType, poseStack);
            }
            return baseModel.handlePerspective(cameraTransformType, poseStack);
        }
    }

    public static class Loader implements IModelLoader<SeparatePerspectiveModel>
    {
        public static final Loader INSTANCE = new Loader();

        @Deprecated(forRemoval = true, since = "1.18.2")
        public static final ImmutableBiMap<String, ItemTransforms.TransformType> PERSPECTIVES = ImmutableBiMap.<String, ItemTransforms.TransformType>builder()
                .put("none", ItemTransforms.TransformType.NONE)
                .put("third_person_left_hand", ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND)
                .put("third_person_right_hand", ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND)
                .put("first_person_left_hand", ItemTransforms.TransformType.FIRST_PERSON_LEFT_HAND)
                .put("first_person_right_hand", ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND)
                .put("head", ItemTransforms.TransformType.HEAD)
                .put("gui", ItemTransforms.TransformType.GUI)
                .put("ground", ItemTransforms.TransformType.GROUND)
                .put("fixed", ItemTransforms.TransformType.FIXED)
                .build();

        @Deprecated(forRemoval = true, since = "1.18.2")
        private static final ImmutableBiMap<String, ItemTransforms.TransformType> BACKWARD_COMPATIBILITY = ImmutableBiMap.<String, ItemTransforms.TransformType>builder()
                .put("third_person_left_hand", ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND)
                .put("third_person_right_hand", ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND)
                .put("first_person_left_hand", ItemTransforms.TransformType.FIRST_PERSON_LEFT_HAND)
                .put("first_person_right_hand", ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND)
                .build();

        @Override
        public void m_6213_(final ResourceManager p_10758_) {

        }

        @Override
        public SeparatePerspectiveModel read(JsonDeserializationContext deserializationContext, JsonObject modelContents)
        {
            BlockModel baseModel = deserializationContext.deserialize(GsonHelper.m_13930_(modelContents, "base"), BlockModel.class);

            JsonObject perspectiveData = GsonHelper.m_13930_(modelContents, "perspectives");

            Map<ItemTransforms.TransformType, BlockModel> perspectives = new HashMap<>();
            for(Map.Entry<String, ItemTransforms.TransformType> entry : BACKWARD_COMPATIBILITY.entrySet())
            {
                if (perspectiveData.has(entry.getKey()))
                {
                    BlockModel perspectiveModel = deserializationContext.deserialize(GsonHelper.m_13930_(perspectiveData, entry.getKey()), BlockModel.class);
                    perspectives.put(entry.getValue(), perspectiveModel);
                }
            }
            for(ItemTransforms.TransformType perspective : ItemTransforms.TransformType.values())
            {
                if (perspectiveData.has(perspective.getSerializeName()))
                {
                    BlockModel perspectiveModel = deserializationContext.deserialize(GsonHelper.m_13930_(perspectiveData, perspective.getSerializeName()), BlockModel.class);
                    perspectives.put(perspective, perspectiveModel);
                }
            }

            return new SeparatePerspectiveModel(baseModel, ImmutableMap.copyOf(perspectives));
        }
    }
}
