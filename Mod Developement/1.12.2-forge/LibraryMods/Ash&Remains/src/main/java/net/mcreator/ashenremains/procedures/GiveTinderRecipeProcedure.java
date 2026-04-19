package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class GiveTinderRecipeProcedure {
   @SubscribeEvent
   public static void onPickup(EntityItemPickupEvent event) {
      execute(event, event.getEntity(), event.getItem().m_32055_());
   }

   public static void execute(Entity entity, ItemStack itemstack) {
      execute(null, entity, itemstack);
   }

   private static void execute(@Nullable Event event, Entity entity, ItemStack itemstack) {
      if (entity != null) {
         if (itemstack.m_41720_() == Items.f_42484_ && entity instanceof ServerPlayer _serverPlayer) {
            _serverPlayer.m_7902_(new ResourceLocation[]{new ResourceLocation("ashenremains:flint_and_tinder")});
         }
      }
   }
}
