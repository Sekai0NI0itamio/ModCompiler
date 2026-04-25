package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SheepWoolLayer extends RenderLayer<SheepRenderState, SheepModel> {
    private static final ResourceLocation SHEEP_WOOL_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep_wool.png");
    private final EntityModel<SheepRenderState> adultModel;
    private final EntityModel<SheepRenderState> babyModel;

    public SheepWoolLayer(RenderLayerParent<SheepRenderState, SheepModel> p_367510_, EntityModelSet p_367850_) {
        super(p_367510_);
        this.adultModel = new SheepFurModel(p_367850_.bakeLayer(ModelLayers.SHEEP_WOOL));
        this.babyModel = new SheepFurModel(p_367850_.bakeLayer(ModelLayers.SHEEP_BABY_WOOL));
    }

    public void render(PoseStack p_362211_, MultiBufferSource p_366726_, int p_362383_, SheepRenderState p_366463_, float p_364799_, float p_361838_) {
        if (!p_366463_.isSheared) {
            EntityModel<SheepRenderState> entitymodel = p_366463_.isBaby ? this.babyModel : this.adultModel;
            if (p_366463_.isInvisible) {
                if (p_366463_.appearsGlowing) {
                    entitymodel.setupAnim(p_366463_);
                    VertexConsumer vertexconsumer = p_366726_.getBuffer(RenderType.outline(SHEEP_WOOL_LOCATION));
                    entitymodel.renderToBuffer(p_362211_, vertexconsumer, p_362383_, LivingEntityRenderer.getOverlayCoords(p_366463_, 0.0F), -16777216);
                }
            } else {
                coloredCutoutModelCopyLayerRender(entitymodel, SHEEP_WOOL_LOCATION, p_362211_, p_366726_, p_362383_, p_366463_, p_366463_.getWoolColor());
            }
        }
    }
}
