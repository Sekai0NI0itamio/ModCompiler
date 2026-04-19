package com.loadmyworldproperly;

import com.loadmyworldproperly.client.SingleplayerWorldLoadFixer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "loadmyworldproperly",
   name = "Load My World PROPERLY",
   version = "1.0.0",
   clientSideOnly = true,
   acceptedMinecraftVersions = "[1.12.2]"
)
@EventBusSubscriber(
   modid = "loadmyworldproperly"
)
public final class LoadMyWorldProperlyMod {
   public static final String MOD_ID = "loadmyworldproperly";
   public static final String NAME = "Load My World PROPERLY";
   public static final String VERSION = "1.0.0";
   public static final Logger LOGGER = LogManager.getLogger("Load My World PROPERLY");
   private static final SingleplayerWorldLoadFixer FIXER = new SingleplayerWorldLoadFixer();

   private LoadMyWorldProperlyMod() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         FIXER.onClientTick();
      }
   }
}
