package net.mcreator.ashenremains.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mcreator.ashenremains.entity.GrieferEntity;
import net.mcreator.ashenremains.procedures.GrieferSubmergedProcedure;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class GrieferRenderer extends MobRenderer<GrieferEntity, CreeperModel<GrieferEntity>> {
   public GrieferRenderer(Context context) {
      super(context, new CreeperModel(context.m_174023_(ModelLayers.f_171285_)), 0.5F);
      this.m_115326_(
         new RenderLayer<GrieferEntity, CreeperModel<GrieferEntity>>(this) {
            final ResourceLocation LAYER_TEXTURE = new ResourceLocation("ashenremains:textures/entities/darkestglow.png");

            public void render(
               PoseStack poseStack,
               MultiBufferSource bufferSource,
               int light,
               GrieferEntity entity,
               float limbSwing,
               float limbSwingAmount,
               float partialTicks,
               float ageInTicks,
               float netHeadYaw,
               float headPitch
            ) {
               VertexConsumer vertexConsumer = bufferSource.m_6299_(RenderType.m_110488_(this.LAYER_TEXTURE));
               ((CreeperModel)this.m_117386_()).m_7695_(poseStack, vertexConsumer, light, LivingEntityRenderer.m_115338_(entity, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
            }
         }
      );
   }

   public ResourceLocation getTextureLocation(GrieferEntity entity) {
      return new ResourceLocation("ashenremains:textures/entities/griefed.png");
   }

   protected boolean isShaking(GrieferEntity entity) {
      Level world = entity.m_9236_();
      double x = entity.m_20185_();
      double y = entity.m_20186_();
      double z = entity.m_20189_();
      return GrieferSubmergedProcedure.execute(world, x, y, z);
   }
}
