package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.CowVariant;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CowRenderer extends MobRenderer<Cow, CowRenderState, CowModel> {
    private final Map<CowVariant.ModelType, AdultAndBabyModelPair<CowModel>> models;

    public CowRenderer(EntityRendererProvider.Context p_173956_) {
        super(p_173956_, new CowModel(p_173956_.bakeLayer(ModelLayers.COW)), 0.7F);
        this.models = bakeModels(p_173956_);
    }

    private static Map<CowVariant.ModelType, AdultAndBabyModelPair<CowModel>> bakeModels(EntityRendererProvider.Context p_395482_) {
        return Maps.newEnumMap(
            Map.of(
                CowVariant.ModelType.NORMAL,
                new AdultAndBabyModelPair<>(new CowModel(p_395482_.bakeLayer(ModelLayers.COW)), new CowModel(p_395482_.bakeLayer(ModelLayers.COW_BABY))),
                CowVariant.ModelType.WARM,
                new AdultAndBabyModelPair<>(new CowModel(p_395482_.bakeLayer(ModelLayers.WARM_COW)), new CowModel(p_395482_.bakeLayer(ModelLayers.WARM_COW_BABY))),
                CowVariant.ModelType.COLD,
                new AdultAndBabyModelPair<>(new CowModel(p_395482_.bakeLayer(ModelLayers.COLD_COW)), new CowModel(p_395482_.bakeLayer(ModelLayers.COLD_COW_BABY)))
            )
        );
    }

    public ResourceLocation getTextureLocation(CowRenderState p_393210_) {
        return p_393210_.variant == null ? MissingTextureAtlasSprite.getLocation() : p_393210_.variant.modelAndTexture().asset().texturePath();
    }

    public CowRenderState createRenderState() {
        return new CowRenderState();
    }

    public void extractRenderState(Cow p_368549_, CowRenderState p_395845_, float p_367056_) {
        super.extractRenderState(p_368549_, p_395845_, p_367056_);
        p_395845_.variant = p_368549_.getVariant().value();
    }

    public void render(CowRenderState p_397658_, PoseStack p_396960_, MultiBufferSource p_396130_, int p_397526_) {
        if (p_397658_.variant != null) {
            this.model = this.models.get(p_397658_.variant.modelAndTexture().model()).getModel(p_397658_.isBaby);
            super.render(p_397658_, p_396960_, p_396130_, p_397526_);
        }
    }
}
