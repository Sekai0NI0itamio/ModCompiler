package net.mcreator.ashenremains.init;

import net.mcreator.ashenremains.client.renderer.GrieferRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public class AshenremainsModEntityRenderers {
   @SubscribeEvent
   public static void registerEntityRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)AshenremainsModEntities.GRIEFER.get(), GrieferRenderer::new);
      event.registerEntityRenderer((EntityType)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), ThrownItemRenderer::new);
   }
}
