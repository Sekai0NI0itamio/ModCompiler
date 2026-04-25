package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.ColdChickenModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.ChickenVariant;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChickenRenderer extends MobRenderer<Chicken, ChickenRenderState, ChickenModel> {
    private final Map<ChickenVariant.ModelType, AdultAndBabyModelPair<ChickenModel>> models;

    public ChickenRenderer(EntityRendererProvider.Context p_173952_) {
        super(p_173952_, new ChickenModel(p_173952_.bakeLayer(ModelLayers.CHICKEN)), 0.3F);
        this.models = bakeModels(p_173952_);
    }

    private static Map<ChickenVariant.ModelType, AdultAndBabyModelPair<ChickenModel>> bakeModels(EntityRendererProvider.Context p_396360_) {
        return Maps.newEnumMap(
            Map.of(
                ChickenVariant.ModelType.NORMAL,
                new AdultAndBabyModelPair<>(
                    new ChickenModel(p_396360_.bakeLayer(ModelLayers.CHICKEN)), new ChickenModel(p_396360_.bakeLayer(ModelLayers.CHICKEN_BABY))
                ),
                ChickenVariant.ModelType.COLD,
                new AdultAndBabyModelPair<>(
                    new ColdChickenModel(p_396360_.bakeLayer(ModelLayers.COLD_CHICKEN)), new ColdChickenModel(p_396360_.bakeLayer(ModelLayers.COLD_CHICKEN_BABY))
                )
            )
        );
    }

    public void render(ChickenRenderState p_391646_, PoseStack p_393754_, MultiBufferSource p_392182_, int p_395133_) {
        if (p_391646_.variant != null) {
            this.model = this.models.get(p_391646_.variant.modelAndTexture().model()).getModel(p_391646_.isBaby);
            super.render(p_391646_, p_393754_, p_392182_, p_395133_);
        }
    }

    public ResourceLocation getTextureLocation(ChickenRenderState p_368820_) {
        return p_368820_.variant == null ? MissingTextureAtlasSprite.getLocation() : p_368820_.variant.modelAndTexture().asset().texturePath();
    }

    public ChickenRenderState createRenderState() {
        return new ChickenRenderState();
    }

    public void extractRenderState(Chicken p_368951_, ChickenRenderState p_368780_, float p_370144_) {
        super.extractRenderState(p_368951_, p_368780_, p_370144_);
        p_368780_.flap = Mth.lerp(p_370144_, p_368951_.oFlap, p_368951_.flap);
        p_368780_.flapSpeed = Mth.lerp(p_370144_, p_368951_.oFlapSpeed, p_368951_.flapSpeed);
        p_368780_.variant = p_368951_.getVariant().value();
    }
}
