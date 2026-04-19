package net.mcreator.ashenremains.item.extension;

import net.mcreator.ashenremains.init.AshenremainsModItems;
import net.mcreator.ashenremains.procedures.BucketOfAshDispenseSuccessfullyIfProcedure;
import net.mcreator.ashenremains.procedures.DispenseAshBucketProcedure;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@EventBusSubscriber(
   bus = Bus.MOD
)
public class AshBucketItemExtensionItemExtension {
   @SubscribeEvent
   public static void init(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> DispenserBlock.m_52672_((ItemLike)AshenremainsModItems.BUCKET_OF_ASH.get(), new OptionalDispenseItemBehavior() {
         public ItemStack m_7498_(BlockSource blockSource, ItemStack stack) {
            ItemStack itemstack = stack.m_41777_();
            Level world = blockSource.m_7727_();
            Direction direction = (Direction)blockSource.m_6414_().m_61143_(DispenserBlock.f_52659_);
            int x = blockSource.m_7961_().m_123341_();
            int y = blockSource.m_7961_().m_123342_();
            int z = blockSource.m_7961_().m_123343_();
            this.m_123573_(BucketOfAshDispenseSuccessfullyIfProcedure.execute(world, x, y, z));
            boolean success = this.m_123570_();
            DispenseAshBucketProcedure.execute(world, x, y, z);
            if (success) {
               itemstack.m_41774_(1);
            }

            return itemstack;
         }
      }));
   }
}
