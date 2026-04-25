package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.ColdPigModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.PigVariant;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PigRenderer extends MobRenderer<Pig, PigRenderState, PigModel> {
    private final Map<PigVariant.ModelType, AdultAndBabyModelPair<PigModel>> models;

    public PigRenderer(EntityRendererProvider.Context p_174340_) {
        super(p_174340_, new PigModel(p_174340_.bakeLayer(ModelLayers.PIG)), 0.7F);
        this.models = bakeModels(p_174340_);
        this.addLayer(
            new SimpleEquipmentLayer<>(
                this,
                p_174340_.getEquipmentRenderer(),
                EquipmentClientInfo.LayerType.PIG_SADDLE,
                p_392579_ -> p_392579_.saddle,
                new PigModel(p_174340_.bakeLayer(ModelLayers.PIG_SADDLE)),
                new PigModel(p_174340_.bakeLayer(ModelLayers.PIG_BABY_SADDLE))
            )
        );
    }

    private static Map<PigVariant.ModelType, AdultAndBabyModelPair<PigModel>> bakeModels(EntityRendererProvider.Context p_396029_) {
        return Maps.newEnumMap(
            Map.of(
                PigVariant.ModelType.NORMAL,
                new AdultAndBabyModelPair<>(new PigModel(p_396029_.bakeLayer(ModelLayers.PIG)), new PigModel(p_396029_.bakeLayer(ModelLayers.PIG_BABY))),
                PigVariant.ModelType.COLD,
                new AdultAndBabyModelPair<>(
                    new ColdPigModel(p_396029_.bakeLayer(ModelLayers.COLD_PIG)), new ColdPigModel(p_396029_.bakeLayer(ModelLayers.COLD_PIG_BABY))
                )
            )
        );
    }

    public void render(PigRenderState p_391765_, PoseStack p_396989_, MultiBufferSource p_392541_, int p_391492_) {
        if (p_391765_.variant != null) {
            this.model = this.models.get(p_391765_.variant.modelAndTexture().model()).getModel(p_391765_.isBaby);
            super.render(p_391765_, p_396989_, p_392541_, p_391492_);
        }
    }

    public ResourceLocation getTextureLocation(PigRenderState p_363892_) {
        return p_363892_.variant == null ? MissingTextureAtlasSprite.getLocation() : p_363892_.variant.modelAndTexture().asset().texturePath();
    }

    public PigRenderState createRenderState() {
        return new PigRenderState();
    }

    public void extractRenderState(Pig p_364955_, PigRenderState p_370177_, float p_367094_) {
        super.extractRenderState(p_364955_, p_370177_, p_367094_);
        p_370177_.saddle = p_364955_.getItemBySlot(EquipmentSlot.SADDLE).copy();
        p_370177_.variant = p_364955_.getVariant().value();
    }
}
