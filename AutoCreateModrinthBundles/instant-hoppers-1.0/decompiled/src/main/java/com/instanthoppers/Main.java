package com.instanthoppers;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

@Mod(
   modid = "instant_hoppers",
   version = "1.0",
   name = "Instant Hoppers",
   acceptedMinecraftVersions = "[1.12.2]"
)
@EventBusSubscriber(
   modid = "instant_hoppers"
)
public class Main {
   public static final String MOD_ID = "instant_hoppers";
   public static final String VERSION = "1.0";
   public static final String NAME = "Instant Hoppers";
   private static Field transferCooldownField = null;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      MinecraftForge.EVENT_BUS.register(Main.class);

      try {
         transferCooldownField = ReflectionHelper.findField(TileEntityHopper.class, "transferCooldown", "field_145901_j");
         transferCooldownField.setAccessible(true);
      } catch (Exception var3) {
         var3.printStackTrace();
      }
   }

   @SubscribeEvent
   public static void onWorldTick(WorldTickEvent event) {
      if (event.phase == Phase.END && !event.world.field_72995_K && transferCooldownField != null) {
         World world = event.world;
         List<TileEntity> list = world.field_147482_g;

         for (int i = 0; i < list.size(); i++) {
            TileEntity te = list.get(i);
            if (te != null && te.getClass() == TileEntityHopper.class) {
               TileEntityHopper hopper = (TileEntityHopper)te;
               if (!hopper.func_145837_r()) {
                  try {
                     int cd = transferCooldownField.getInt(hopper);
                     if (cd == 8) {
                        bulkTransfer(hopper, world);
                     }
                  } catch (Exception var7) {
                  }
               }
            }
         }
      }
   }

   private static void bulkTransfer(TileEntityHopper hopper, World world) {
      boolean changed = false;
      IBlockState state = world.func_180495_p(hopper.func_174877_v());
      if (state.func_177230_c() instanceof BlockHopper) {
         EnumFacing facing = (EnumFacing)state.func_177229_b(BlockHopper.field_176430_a);
         IItemHandler hopperExtract = (IItemHandler)hopper.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
         IItemHandler hopperInsert = (IItemHandler)hopper.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
         BlockPos targetPos = hopper.func_174877_v().func_177972_a(facing);
         TileEntity targetTE = world.func_175625_s(targetPos);
         IItemHandler target = null;
         if (targetTE != null && targetTE.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.func_176734_d())) {
            target = (IItemHandler)targetTE.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.func_176734_d());
         }

         if (target != null && hopperExtract != null) {
            boolean pushedThisLoop = true;

            while (pushedThisLoop) {
               pushedThisLoop = false;

               for (int i = 0; i < hopperExtract.getSlots(); i++) {
                  ItemStack stackInHopper = hopperExtract.extractItem(i, 64, true);
                  if (!stackInHopper.func_190926_b()) {
                     ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stackInHopper, false);
                     int amountMoved = stackInHopper.func_190916_E() - remainder.func_190916_E();
                     if (amountMoved > 0) {
                        hopperExtract.extractItem(i, amountMoved, false);
                        pushedThisLoop = true;
                        changed = true;
                     }
                  }
               }
            }
         }

         BlockPos sourcePos = hopper.func_174877_v().func_177984_a();
         TileEntity sourceTE = world.func_175625_s(sourcePos);
         IItemHandler source = null;
         if (sourceTE != null && sourceTE.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN)) {
            source = (IItemHandler)sourceTE.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
         }

         if (source != null && hopperInsert != null) {
            boolean pulledThisLoop = true;

            while (pulledThisLoop) {
               pulledThisLoop = false;

               for (int ix = 0; ix < source.getSlots(); ix++) {
                  ItemStack stackInSource = source.extractItem(ix, 64, true);
                  if (!stackInSource.func_190926_b()) {
                     ItemStack remainder = ItemHandlerHelper.insertItemStacked(hopperInsert, stackInSource, false);
                     int amountMoved = stackInSource.func_190916_E() - remainder.func_190916_E();
                     if (amountMoved > 0) {
                        source.extractItem(ix, amountMoved, false);
                        pulledThisLoop = true;
                        changed = true;
                     }
                  }
               }
            }
         }

         if (hopperInsert != null) {
            for (EntityItem item : world.func_72872_a(EntityItem.class, new AxisAlignedBB(hopper.func_174877_v().func_177984_a()))) {
               if (!item.field_70128_L && !item.func_92059_d().func_190926_b()) {
                  ItemStack remainder = ItemHandlerHelper.insertItemStacked(hopperInsert, item.func_92059_d().func_77946_l(), false);
                  item.func_92058_a(remainder);
                  if (remainder.func_190926_b()) {
                     item.func_70106_y();
                  }

                  changed = true;
               }
            }
         }

         if (changed) {
            hopper.func_70296_d();
            if (targetTE != null) {
               targetTE.func_70296_d();
            }

            if (sourceTE != null) {
               sourceTE.func_70296_d();
            }
         }
      }
   }
}
