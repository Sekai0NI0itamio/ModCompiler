package net.minecraft.client.renderer.entity.layers;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Markings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HorseMarkingLayer extends RenderLayer<HorseRenderState, HorseModel> {
    private static final ResourceLocation INVISIBLE_TEXTURE = ResourceLocation.withDefaultNamespace("invisible");
    private static final Map<Markings, ResourceLocation> LOCATION_BY_MARKINGS = Maps.newEnumMap(
        Map.of(
            Markings.NONE,
            INVISIBLE_TEXTURE,
            Markings.WHITE,
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_white.png"),
            Markings.WHITE_FIELD,
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_whitefield.png"),
            Markings.WHITE_DOTS,
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_whitedots.png"),
            Markings.BLACK_DOTS,
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_blackdots.png")
        )
    );

    public HorseMarkingLayer(RenderLayerParent<HorseRenderState, HorseModel> p_117045_) {
        super(p_117045_);
    }

    public void render(PoseStack p_117058_, MultiBufferSource p_117059_, int p_117060_, HorseRenderState p_366742_, float p_117062_, float p_117063_) {
        ResourceLocation resourcelocation = LOCATION_BY_MARKINGS.get(p_366742_.markings);
        if (resourcelocation != INVISIBLE_TEXTURE && !p_366742_.isInvisible) {
            VertexConsumer vertexconsumer = p_117059_.getBuffer(RenderType.entityTranslucent(resourcelocation));
            this.getParentModel().renderToBuffer(p_117058_, vertexconsumer, p_117060_, LivingEntityRenderer.getOverlayCoords(p_366742_, 0.0F));
        }
    }
}
