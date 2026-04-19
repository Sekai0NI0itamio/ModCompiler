package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class GiveAshBucketRecipeProcedure {
   @SubscribeEvent
   public static void onPickup(EntityItemPickupEvent event) {
      execute(
         event,
         event.getEntity().m_9236_(),
         event.getEntity().m_20185_(),
         event.getEntity().m_20186_(),
         event.getEntity().m_20189_(),
         event.getEntity(),
         event.getItem().m_32055_()
      );
   }

   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack) {
      execute(null, world, x, y, z, entity, itemstack);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack) {
      if (entity != null) {
         if (itemstack.m_41720_() == AshenremainsModItems.ASH_BALL.get()
            && !world.m_6443_(Player.class, AABB.m_165882_(new Vec3(x, y, z), 2.0, 2.0, 2.0), e -> true).isEmpty()
            && entity instanceof ServerPlayer _serverPlayer) {
            _serverPlayer.m_7902_(new ResourceLocation[]{new ResourceLocation("ashenremains:ash_bucket_recipe")});
         }
      }
   }
}
