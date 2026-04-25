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
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.Direction;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.geometry.IModelGeometry;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class SeparatePerspectiveModel implements IModelGeometry<SeparatePerspectiveModel>
{
    private final BlockModel baseModel;
    private final ImmutableMap<ItemCameraTransforms.TransformType, BlockModel> perspectives;

    public SeparatePerspectiveModel(BlockModel baseModel, ImmutableMap<ItemCameraTransforms.TransformType, BlockModel> perspectives)
    {
        this.baseModel = baseModel;
        this.perspectives = perspectives;
    }

    @Override
    public IBakedModel bake(IModelConfiguration owner, ModelBakery bakery, Function<RenderMaterial, TextureAtlasSprite> spriteGetter, IModelTransform modelTransform, ItemOverrideList overrides, ResourceLocation modelLocation)
    {
        return new BakedModel(
                owner.useSmoothLighting(), owner.isShadedInGui(), owner.isSideLit(),
                spriteGetter.apply(owner.resolveTexture("particle")), overrides,
                baseModel.func_228813_a_(bakery, baseModel, spriteGetter, modelTransform, modelLocation, owner.isSideLit()),
                ImmutableMap.copyOf(Maps.transformValues(perspectives, value -> {
                    return value.func_228813_a_(bakery, value, spriteGetter, modelTransform, modelLocation, owner.isSideLit());
                }))
        );
    }

    @Override
    public Collection<RenderMaterial> getTextures(IModelConfiguration owner, Function<ResourceLocation, IUnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors)
    {
        Set<RenderMaterial> textures = Sets.newHashSet();
        textures.addAll(baseModel.func_225614_a_(modelGetter, missingTextureErrors));
        for(BlockModel model : perspectives.values())
            textures.addAll(model.func_225614_a_(modelGetter, missingTextureErrors));
        return textures;
    }

    public static class BakedModel implements IBakedModel
    {
        private final boolean isAmbientOcclusion;
        private final boolean isGui3d;
        private final boolean isSideLit;
        private final TextureAtlasSprite particle;
        private final ItemOverrideList overrides;
        private final IBakedModel baseModel;
        private final ImmutableMap<ItemCameraTransforms.TransformType, IBakedModel> perspectives;

        public BakedModel(boolean isAmbientOcclusion, boolean isGui3d, boolean isSideLit, TextureAtlasSprite particle, ItemOverrideList overrides, IBakedModel baseModel, ImmutableMap<ItemCameraTransforms.TransformType, IBakedModel> perspectives)
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
        public List<BakedQuad> func_200117_a(@Nullable BlockState state, @Nullable Direction side, Random rand)
        {
            return Collections.emptyList();
        }

        @Override
        public boolean func_177555_b()
        {
            return isAmbientOcclusion;
        }

        @Override
        public boolean func_177556_c()
        {
            return isGui3d;
        }

        @Override
        public boolean func_230044_c_()
        {
            return isSideLit;
        }

        @Override
        public boolean func_188618_c()
        {
            return false;
        }

        @Override
        public TextureAtlasSprite func_177554_e()
        {
            return particle;
        }

        @Override
        public ItemOverrideList func_188617_f()
        {
            return overrides;
        }

        @Override
        public boolean doesHandlePerspectives()
        {
            return true;
        }

        @Override
        public ItemCameraTransforms func_177552_f()
        {
            return ItemCameraTransforms.field_178357_a;
        }

        @Override
        public IBakedModel handlePerspective(ItemCameraTransforms.TransformType cameraTransformType, MatrixStack mat)
        {
            if (perspectives.containsKey(cameraTransformType))
            {
                IBakedModel p = perspectives.get(cameraTransformType);
                return p.handlePerspective(cameraTransformType, mat);
            }
            return baseModel.handlePerspective(cameraTransformType, mat);
        }
    }

    public static class Loader implements IModelLoader<SeparatePerspectiveModel>
    {
        public static final Loader INSTANCE = new Loader();

        public static final ImmutableBiMap<String, ItemCameraTransforms.TransformType> PERSPECTIVES = ImmutableBiMap.<String, ItemCameraTransforms.TransformType>builder()
                .put("none", ItemCameraTransforms.TransformType.NONE)
                .put("third_person_left_hand", ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND)
                .put("third_person_right_hand", ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND)
                .put("first_person_left_hand", ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND)
                .put("first_person_right_hand", ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND)
                .put("head", ItemCameraTransforms.TransformType.HEAD)
                .put("gui", ItemCameraTransforms.TransformType.GUI)
                .put("ground", ItemCameraTransforms.TransformType.GROUND)
                .put("fixed", ItemCameraTransforms.TransformType.FIXED)
                .build();

        @Override
        public void func_195410_a(IResourceManager resourceManager)
        {
            // Not used
        }

        @Override
        public SeparatePerspectiveModel read(JsonDeserializationContext deserializationContext, JsonObject modelContents)
        {
            BlockModel baseModel = deserializationContext.deserialize(JSONUtils.func_152754_s(modelContents, "base"), BlockModel.class);

            JsonObject perspectiveData = JSONUtils.func_152754_s(modelContents, "perspectives");

            ImmutableMap.Builder<ItemCameraTransforms.TransformType, BlockModel> perspectives = ImmutableMap.builder();
            for(Map.Entry<String, ItemCameraTransforms.TransformType> perspective : PERSPECTIVES.entrySet())
            {
                if (perspectiveData.has(perspective.getKey()))
                {
                    BlockModel perspectiveModel = deserializationContext.deserialize(JSONUtils.func_152754_s(perspectiveData, perspective.getKey()), BlockModel.class);
                    perspectives.put(perspective.getValue(), perspectiveModel);
                }
            }

            return new SeparatePerspectiveModel(baseModel, perspectives.build());
        }
    }
}
