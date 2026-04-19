package com.itamio.craftableslimeballs;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
   modid = "craftableslimeballs",
   name = "Craftable Slime Balls",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12.2]"
)
public class CraftableSlimeBalls {
   public static final String MODID = "craftableslimeballs";
   @Instance("craftableslimeballs")
   public static CraftableSlimeBalls INSTANCE;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
   }

   @EventHandler
   public void postInit(FMLPostInitializationEvent event) {
   }
}
