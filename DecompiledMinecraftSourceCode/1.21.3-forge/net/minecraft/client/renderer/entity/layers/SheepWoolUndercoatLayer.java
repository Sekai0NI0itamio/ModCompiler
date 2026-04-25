package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SheepWoolUndercoatLayer extends RenderLayer<SheepRenderState, SheepModel> {
    private static final ResourceLocation SHEEP_WOOL_UNDERCOAT_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep_wool_undercoat.png");
    private final EntityModel<SheepRenderState> adultModel;
    private final EntityModel<SheepRenderState> babyModel;

    public SheepWoolUndercoatLayer(RenderLayerParent<SheepRenderState, SheepModel> p_393062_, EntityModelSet p_395881_) {
        super(p_393062_);
        this.adultModel = new SheepFurModel(p_395881_.bakeLayer(ModelLayers.SHEEP_WOOL_UNDERCOAT));
        this.babyModel = new SheepFurModel(p_395881_.bakeLayer(ModelLayers.SHEEP_BABY_WOOL_UNDERCOAT));
    }

    public void render(PoseStack p_392145_, MultiBufferSource p_393949_, int p_397816_, SheepRenderState p_391419_, float p_396262_, float p_393180_) {
        if (!p_391419_.isInvisible && (p_391419_.isJebSheep() || p_391419_.woolColor != DyeColor.WHITE)) {
            EntityModel<SheepRenderState> entitymodel = p_391419_.isBaby ? this.babyModel : this.adultModel;
            coloredCutoutModelCopyLayerRender(entitymodel, SHEEP_WOOL_UNDERCOAT_LOCATION, p_392145_, p_393949_, p_397816_, p_391419_, p_391419_.getWoolColor());
        }
    }
}
