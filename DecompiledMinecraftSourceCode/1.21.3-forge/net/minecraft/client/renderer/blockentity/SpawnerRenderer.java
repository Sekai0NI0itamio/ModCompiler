package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpawnerRenderer implements BlockEntityRenderer<SpawnerBlockEntity> {
    private final EntityRenderDispatcher entityRenderer;

    public SpawnerRenderer(BlockEntityRendererProvider.Context p_173673_) {
        this.entityRenderer = p_173673_.getEntityRenderer();
    }

    public void render(
        SpawnerBlockEntity p_391570_, float p_112557_, PoseStack p_112558_, MultiBufferSource p_112559_, int p_112560_, int p_112561_, Vec3 p_392238_
    ) {
        Level level = p_391570_.getLevel();
        if (level != null) {
            BaseSpawner basespawner = p_391570_.getSpawner();
            Entity entity = basespawner.getOrCreateDisplayEntity(level, p_391570_.getBlockPos());
            if (entity != null) {
                renderEntityInSpawner(p_112557_, p_112558_, p_112559_, p_112560_, entity, this.entityRenderer, basespawner.getoSpin(), basespawner.getSpin());
            }
        }
    }

    public static void renderEntityInSpawner(
        float p_311595_,
        PoseStack p_313192_,
        MultiBufferSource p_313178_,
        int p_312511_,
        Entity p_312144_,
        EntityRenderDispatcher p_311572_,
        double p_310509_,
        double p_310548_
    ) {
        p_313192_.pushPose();
        p_313192_.translate(0.5F, 0.0F, 0.5F);
        float f = 0.53125F;
        float f1 = Math.max(p_312144_.getBbWidth(), p_312144_.getBbHeight());
        if (f1 > 1.0) {
            f /= f1;
        }

        p_313192_.translate(0.0F, 0.4F, 0.0F);
        p_313192_.mulPose(Axis.YP.rotationDegrees((float)Mth.lerp(p_311595_, p_310509_, p_310548_) * 10.0F));
        p_313192_.translate(0.0F, -0.2F, 0.0F);
        p_313192_.mulPose(Axis.XP.rotationDegrees(-30.0F));
        p_313192_.scale(f, f, f);
        p_311572_.render(p_312144_, 0.0, 0.0, 0.0, p_311595_, p_313192_, p_313178_, p_312511_);
        p_313192_.popPose();
    }
}
