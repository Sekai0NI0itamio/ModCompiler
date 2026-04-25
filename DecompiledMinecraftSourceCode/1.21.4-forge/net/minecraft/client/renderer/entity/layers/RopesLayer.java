package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HappyGhastModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RopesLayer<M extends HappyGhastModel> extends RenderLayer<HappyGhastRenderState, M> {
    private final RenderType ropes;
    private final HappyGhastModel adultModel;
    private final HappyGhastModel babyModel;

    public RopesLayer(RenderLayerParent<HappyGhastRenderState, M> p_408307_, EntityModelSet p_408757_, ResourceLocation p_409983_) {
        super(p_408307_);
        this.ropes = RenderType.entityCutoutNoCull(p_409983_);
        this.adultModel = new HappyGhastModel(p_408757_.bakeLayer(ModelLayers.HAPPY_GHAST_ROPES));
        this.babyModel = new HappyGhastModel(p_408757_.bakeLayer(ModelLayers.HAPPY_GHAST_BABY_ROPES));
    }

    public void render(PoseStack p_406636_, MultiBufferSource p_408288_, int p_407465_, HappyGhastRenderState p_407153_, float p_409973_, float p_406171_) {
        if (p_407153_.isLeashHolder && p_407153_.bodyItem.is(ItemTags.HARNESSES)) {
            HappyGhastModel happyghastmodel = p_407153_.isBaby ? this.babyModel : this.adultModel;
            happyghastmodel.setupAnim(p_407153_);
            happyghastmodel.renderToBuffer(p_406636_, p_408288_.getBuffer(this.ropes), p_407465_, OverlayTexture.NO_OVERLAY);
        }
    }
}
