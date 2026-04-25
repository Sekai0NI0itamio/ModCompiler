package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.AbstractEquineModel;
import net.minecraft.client.model.EquineSaddleModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class UndeadHorseRenderer extends AbstractHorseRenderer<AbstractHorse, EquineRenderState, AbstractEquineModel<EquineRenderState>> {
    private final ResourceLocation texture;

    public UndeadHorseRenderer(EntityRendererProvider.Context p_174432_, UndeadHorseRenderer.Type p_391877_) {
        super(p_174432_, new HorseModel(p_174432_.bakeLayer(p_391877_.model)), new HorseModel(p_174432_.bakeLayer(p_391877_.babyModel)));
        this.texture = p_391877_.texture;
        this.addLayer(
            new SimpleEquipmentLayer<>(
                this,
                p_174432_.getEquipmentRenderer(),
                p_391877_.saddleLayer,
                p_394269_ -> p_394269_.saddle,
                new EquineSaddleModel(p_174432_.bakeLayer(p_391877_.saddleModel)),
                new EquineSaddleModel(p_174432_.bakeLayer(p_391877_.babySaddleModel))
            )
        );
    }

    public ResourceLocation getTextureLocation(EquineRenderState p_369447_) {
        return this.texture;
    }

    public EquineRenderState createRenderState() {
        return new EquineRenderState();
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Type {
        SKELETON(
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_skeleton.png"),
            ModelLayers.SKELETON_HORSE,
            ModelLayers.SKELETON_HORSE_BABY,
            EquipmentClientInfo.LayerType.SKELETON_HORSE_SADDLE,
            ModelLayers.SKELETON_HORSE_SADDLE,
            ModelLayers.SKELETON_HORSE_BABY_SADDLE
        ),
        ZOMBIE(
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_zombie.png"),
            ModelLayers.ZOMBIE_HORSE,
            ModelLayers.ZOMBIE_HORSE_BABY,
            EquipmentClientInfo.LayerType.ZOMBIE_HORSE_SADDLE,
            ModelLayers.ZOMBIE_HORSE_SADDLE,
            ModelLayers.ZOMBIE_HORSE_BABY_SADDLE
        );

        final ResourceLocation texture;
        final ModelLayerLocation model;
        final ModelLayerLocation babyModel;
        final EquipmentClientInfo.LayerType saddleLayer;
        final ModelLayerLocation saddleModel;
        final ModelLayerLocation babySaddleModel;

        private Type(
            final ResourceLocation p_396908_,
            final ModelLayerLocation p_395485_,
            final ModelLayerLocation p_393739_,
            final EquipmentClientInfo.LayerType p_396241_,
            final ModelLayerLocation p_394781_,
            final ModelLayerLocation p_395729_
        ) {
            this.texture = p_396908_;
            this.model = p_395485_;
            this.babyModel = p_393739_;
            this.saddleLayer = p_396241_;
            this.saddleModel = p_394781_;
            this.babySaddleModel = p_395729_;
        }
    }
}
