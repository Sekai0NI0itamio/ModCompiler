package com.tntduper;

import com.tntduper.proxy.CommonProxy;
import net.minecraft.block.BlockDispenser;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(
   modid = "tnt_duper",
   version = "1.0",
   name = "TNT Duper",
   acceptedMinecraftVersions = "[1.12.2]"
)
public class Main {
   public static final String MOD_ID = "tnt_duper";
   public static final String VERSION = "1.0";
   public static final String NAME = "TNT Duper";
   @Instance
   public static Main main;
   @SidedProxy(
      serverSide = "com.tntduper.proxy.CommonProxy",
      clientSide = "com.tntduper.proxy.ClientProxy"
   )
   public static CommonProxy proxy;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
      BlockDispenser.field_149943_a.func_82595_a(Item.func_150898_a(Blocks.field_150335_W), new BehaviorDispenseTNTDuper());
   }

   @EventHandler
   public void postInit(FMLPostInitializationEvent event) {
   }

   @EventHandler
   public void serverStarting(FMLServerStartingEvent event) {
   }
}
