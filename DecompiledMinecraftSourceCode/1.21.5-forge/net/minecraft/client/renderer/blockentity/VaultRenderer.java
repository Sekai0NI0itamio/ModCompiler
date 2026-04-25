package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultClientData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VaultRenderer implements BlockEntityRenderer<VaultBlockEntity> {
    private final ItemModelResolver itemModelResolver;
    private final RandomSource random = RandomSource.create();
    private final ItemClusterRenderState renderState = new ItemClusterRenderState();

    public VaultRenderer(BlockEntityRendererProvider.Context p_335617_) {
        this.itemModelResolver = p_335617_.getItemModelResolver();
    }

    public void render(
        VaultBlockEntity p_397631_, float p_334714_, PoseStack p_335379_, MultiBufferSource p_329059_, int p_333594_, int p_334931_, Vec3 p_396690_
    ) {
        if (VaultBlockEntity.Client.shouldDisplayActiveEffects(p_397631_.getSharedData())) {
            Level level = p_397631_.getLevel();
            if (level != null) {
                ItemStack itemstack = p_397631_.getSharedData().getDisplayItem();
                if (!itemstack.isEmpty()) {
                    this.itemModelResolver.updateForTopItem(this.renderState.item, itemstack, ItemDisplayContext.GROUND, level, null, 0);
                    this.renderState.count = ItemClusterRenderState.getRenderedAmount(itemstack.getCount());
                    this.renderState.seed = ItemClusterRenderState.getSeedForItemStack(itemstack);
                    VaultClientData vaultclientdata = p_397631_.getClientData();
                    p_335379_.pushPose();
                    p_335379_.translate(0.5F, 0.4F, 0.5F);
                    p_335379_.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(p_334714_, vaultclientdata.previousSpin(), vaultclientdata.currentSpin())));
                    ItemEntityRenderer.renderMultipleFromCount(p_335379_, p_329059_, p_333594_, this.renderState, this.random);
                    p_335379_.popPose();
                }
            }
        }
    }
}
