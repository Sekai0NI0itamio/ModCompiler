package net.mcreator.ashenremains.init;

import net.mcreator.ashenremains.entity.AshBallProjectileEntity;
import net.mcreator.ashenremains.entity.GrieferEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@EventBusSubscriber(
   bus = Bus.MOD
)
public class AshenremainsModEntities {
   public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "ashenremains");
   public static final RegistryObject<EntityType<GrieferEntity>> GRIEFER = register(
      "griefer",
      Builder.m_20704_(GrieferEntity::new, MobCategory.MONSTER)
         .setShouldReceiveVelocityUpdates(true)
         .setTrackingRange(64)
         .setUpdateInterval(3)
         .setCustomClientFactory(GrieferEntity::new)
         .m_20719_()
         .m_20699_(0.6F, 1.7F)
   );
   public static final RegistryObject<EntityType<AshBallProjectileEntity>> ASH_BALL_PROJECTILE = register(
      "ash_ball_projectile",
      Builder.m_20704_(AshBallProjectileEntity::new, MobCategory.MISC)
         .setCustomClientFactory(AshBallProjectileEntity::new)
         .setShouldReceiveVelocityUpdates(true)
         .setTrackingRange(64)
         .setUpdateInterval(1)
         .m_20699_(0.5F, 0.5F)
   );

   private static <T extends Entity> RegistryObject<EntityType<T>> register(String registryname, Builder<T> entityTypeBuilder) {
      return REGISTRY.register(registryname, () -> entityTypeBuilder.m_20712_(registryname));
   }

   @SubscribeEvent
   public static void init(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> GrieferEntity.init());
   }

   @SubscribeEvent
   public static void registerAttributes(EntityAttributeCreationEvent event) {
      event.put((EntityType)GRIEFER.get(), GrieferEntity.createAttributes().m_22265_());
   }
}
