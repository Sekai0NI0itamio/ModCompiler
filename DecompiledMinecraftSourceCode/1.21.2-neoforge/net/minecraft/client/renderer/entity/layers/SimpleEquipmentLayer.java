package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SimpleEquipmentLayer<S extends LivingEntityRenderState, RM extends EntityModel<? super S>, EM extends EntityModel<? super S>>
    extends RenderLayer<S, RM> {
    private final EquipmentLayerRenderer equipmentRenderer;
    private final EquipmentClientInfo.LayerType layer;
    private final Function<S, ItemStack> itemGetter;
    private final EM adultModel;
    private final EM babyModel;

    public SimpleEquipmentLayer(
        RenderLayerParent<S, RM> p_391337_,
        EquipmentLayerRenderer p_396277_,
        EquipmentClientInfo.LayerType p_393091_,
        Function<S, ItemStack> p_394811_,
        EM p_392402_,
        EM p_397424_
    ) {
        super(p_391337_);
        this.equipmentRenderer = p_396277_;
        this.layer = p_393091_;
        this.itemGetter = p_394811_;
        this.adultModel = p_392402_;
        this.babyModel = p_397424_;
    }

    public SimpleEquipmentLayer(
        RenderLayerParent<S, RM> p_397316_,
        EquipmentLayerRenderer p_393279_,
        EM p_397758_,
        EquipmentClientInfo.LayerType p_393287_,
        Function<S, ItemStack> p_395803_
    ) {
        this(p_397316_, p_393279_, p_393287_, p_395803_, p_397758_, p_397758_);
    }

    public void render(PoseStack p_392971_, MultiBufferSource p_393255_, int p_396211_, S p_396431_, float p_392529_, float p_395422_) {
        ItemStack itemstack = this.itemGetter.apply(p_396431_);
        Equippable equippable = itemstack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && !equippable.assetId().isEmpty()) {
            EM em = p_396431_.isBaby ? this.babyModel : this.adultModel;
            em.setupAnim(p_396431_);
            this.equipmentRenderer.renderLayers(this.layer, equippable.assetId().get(), em, itemstack, p_392971_, p_393255_, p_396211_);
        }
    }
}
