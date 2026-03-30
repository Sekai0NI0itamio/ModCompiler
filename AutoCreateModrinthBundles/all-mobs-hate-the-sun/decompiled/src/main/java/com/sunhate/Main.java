package com.sunhate;

import com.sunhate.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(
   modid = "all_most_hate_the_sun",
   version = "1.0",
   name = "All Most Hate The SUN",
   acceptedMinecraftVersions = "[1.12.2]"
)
public class Main {
   public static final String MOD_ID = "all_most_hate_the_sun";
   public static final String VERSION = "1.0";
   public static final String NAME = "All Most Hate The SUN";
   @Instance
   public static Main main;
   @SidedProxy(
      serverSide = "com.sunhate.proxy.CommonProxy",
      clientSide = "com.sunhate.proxy.ClientProxy"
   )
   public static CommonProxy proxy;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
   }

   @EventHandler
   public void postInit(FMLPostInitializationEvent event) {
   }

   @EventHandler
   public void serverStarting(FMLServerStartingEvent event) {
   }
}
