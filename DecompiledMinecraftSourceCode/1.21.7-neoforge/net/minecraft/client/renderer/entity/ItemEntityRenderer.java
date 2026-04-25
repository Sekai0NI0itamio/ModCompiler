package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemEntityRenderer extends EntityRenderer<ItemEntity, ItemEntityRenderState> {
    private static final float ITEM_MIN_HOVER_HEIGHT = 0.0625F;
    private static final float ITEM_BUNDLE_OFFSET_SCALE = 0.15F;
    private static final float FLAT_ITEM_DEPTH_THRESHOLD = 0.0625F;
    private final ItemModelResolver itemModelResolver;
    private final RandomSource random = RandomSource.create();

    public ItemEntityRenderer(EntityRendererProvider.Context p_174198_) {
        super(p_174198_);
        this.itemModelResolver = p_174198_.getItemModelResolver();
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    public ItemEntityRenderState createRenderState() {
        return new ItemEntityRenderState();
    }

    public void extractRenderState(ItemEntity p_365788_, ItemEntityRenderState p_361751_, float p_369533_) {
        super.extractRenderState(p_365788_, p_361751_, p_369533_);
        p_361751_.ageInTicks = p_365788_.getAge() + p_369533_;
        p_361751_.bobOffset = p_365788_.bobOffs;
        p_361751_.extractItemGroupRenderState(p_365788_, p_365788_.getItem(), this.itemModelResolver);
    }

    public void render(ItemEntityRenderState p_365095_, PoseStack p_115030_, MultiBufferSource p_115031_, int p_115032_) {
        if (!p_365095_.item.isEmpty()) {
            p_115030_.pushPose();
            AABB aabb = p_365095_.item.getModelBoundingBox();
            float f = -((float)aabb.minY) + 0.0625F;
            float f1 = Mth.sin(p_365095_.ageInTicks / 10.0F + p_365095_.bobOffset) * 0.1F + 0.1F;
            p_115030_.translate(0.0F, f1 + f, 0.0F);
            float f2 = ItemEntity.getSpin(p_365095_.ageInTicks, p_365095_.bobOffset);
            p_115030_.mulPose(Axis.YP.rotation(f2));
            renderMultipleFromCount(p_115030_, p_115031_, p_115032_, p_365095_, this.random, aabb);
            p_115030_.popPose();
            super.render(p_365095_, p_115030_, p_115031_, p_115032_);
        }
    }

    public static void renderMultipleFromCount(PoseStack p_330844_, MultiBufferSource p_333382_, int p_334169_, ItemClusterRenderState p_377874_, RandomSource p_331892_) {
        renderMultipleFromCount(p_330844_, p_333382_, p_334169_, p_377874_, p_331892_, p_377874_.item.getModelBoundingBox());
    }

    public static void renderMultipleFromCount(
        PoseStack p_393139_, MultiBufferSource p_391270_, int p_395923_, ItemClusterRenderState p_394871_, RandomSource p_392388_, AABB p_394505_
    ) {
        int i = p_394871_.count;
        if (i != 0) {
            p_392388_.setSeed(p_394871_.seed);
            ItemStackRenderState itemstackrenderstate = p_394871_.item;
            float f = (float)p_394505_.getZsize();
            if (f > 0.0625F) {
                itemstackrenderstate.render(p_393139_, p_391270_, p_395923_, OverlayTexture.NO_OVERLAY);

                for (int j = 1; j < i; j++) {
                    p_393139_.pushPose();
                    float f1 = (p_392388_.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f2 = (p_392388_.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f3 = (p_392388_.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    p_393139_.translate(f1, f2, f3);
                    itemstackrenderstate.render(p_393139_, p_391270_, p_395923_, OverlayTexture.NO_OVERLAY);
                    p_393139_.popPose();
                }
            } else {
                float f4 = f * 1.5F;
                p_393139_.translate(0.0F, 0.0F, -(f4 * (i - 1) / 2.0F));
                itemstackrenderstate.render(p_393139_, p_391270_, p_395923_, OverlayTexture.NO_OVERLAY);
                p_393139_.translate(0.0F, 0.0F, f4);

                for (int k = 1; k < i; k++) {
                    p_393139_.pushPose();
                    float f5 = (p_392388_.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    float f6 = (p_392388_.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    p_393139_.translate(f5, f6, 0.0F);
                    itemstackrenderstate.render(p_393139_, p_391270_, p_395923_, OverlayTexture.NO_OVERLAY);
                    p_393139_.popPose();
                    p_393139_.translate(0.0F, 0.0F, f4);
                }
            }
        }
    }
}
