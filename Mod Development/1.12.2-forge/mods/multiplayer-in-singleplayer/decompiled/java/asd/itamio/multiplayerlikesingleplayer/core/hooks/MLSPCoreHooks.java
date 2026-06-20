package asd.itamio.multiplayerlikesingleplayer.core.hooks;

import asd.itamio.multiplayerlikesingleplayer.MultiplayerLikeSingleplayerMod;
import asd.itamio.multiplayerlikesingleplayer.gui.GuiIdentityPicker;
import asd.itamio.multiplayerlikesingleplayer.gui.GuiJoinAsPrompt;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public final class MLSPCoreHooks {
   private static volatile boolean launchBypass;
   private static volatile boolean multiplayerConnectBypass;

   private MLSPCoreHooks() {
   }

   public static boolean beforeLaunchIntegratedServer(Minecraft minecraft, String folderName, String worldName, WorldSettings settings) {
      if (launchBypass) {
         launchBypass = false;
         return true;
      } else {
         try {
            GuiScreen currentScreen = getCurrentScreen(minecraft);
            minecraft.func_147108_a(new GuiIdentityPicker(currentScreen, folderName, worldName, settings));
         } catch (Throwable var5) {
            MultiplayerLikeSingleplayerMod.LOGGER.error("Failed opening identity picker", var5);
         }

         return false;
      }
   }

   public static void resumeIntegratedLaunch(String folderName, String worldName, WorldSettings settings) {
      Minecraft minecraft = Minecraft.func_71410_x();
      launchBypass = true;
      minecraft.func_71371_a(folderName, worldName, settings);
   }

   public static boolean beforeGuiMultiplayerConnect(GuiMultiplayer guiMultiplayer) {
      if (multiplayerConnectBypass) {
         multiplayerConnectBypass = false;
         return true;
      } else {
         try {
            Minecraft.func_71410_x().func_147108_a(new GuiJoinAsPrompt(guiMultiplayer, null));
         } catch (Throwable var2) {
            MultiplayerLikeSingleplayerMod.LOGGER.error("Failed opening join-as prompt", var2);
         }

         return false;
      }
   }

   public static void resumeMultiplayerConnect(GuiMultiplayer guiMultiplayer) {
      try {
         Minecraft minecraft = Minecraft.func_71410_x();
         minecraft.func_147108_a(guiMultiplayer);
         multiplayerConnectBypass = true;
         Method method = ReflectionHelper.findMethod(GuiMultiplayer.class, "connectToSelected", "func_146791_a", new Class[0]);
         method.setAccessible(true);
         method.invoke(guiMultiplayer);
      } catch (Throwable var3) {
         MultiplayerLikeSingleplayerMod.LOGGER.error("Failed resuming multiplayer connect", var3);
      }
   }

   public static void beforeShareToLAN(IntegratedServer integratedServer) {
      try {
         integratedServer.func_71229_d(false);
      } catch (Throwable var5) {
         try {
            Method method = ReflectionHelper.findMethod(integratedServer.getClass(), "setOnlineMode", "func_71229_d", new Class[]{Boolean.TYPE});
            method.setAccessible(true);
            method.invoke(integratedServer, false);
         } catch (Throwable var4) {
            try {
               Field field = ReflectionHelper.findField(integratedServer.getClass(), "onlineMode", "field_71325_x");
               field.setBoolean(integratedServer, false);
            } catch (Throwable var3) {
               MultiplayerLikeSingleplayerMod.LOGGER.warn("Unable to force offline mode before LAN share", var3);
            }
         }
      }
   }

   public static int getFixedLanPort() {
      return 56567;
   }

   private static GuiScreen getCurrentScreen(Minecraft minecraft) {
      try {
         Field field = ReflectionHelper.findField(Minecraft.class, "currentScreen", "field_71462_r");
         Object value = field.get(minecraft);
         if (value instanceof GuiScreen) {
            return (GuiScreen)value;
         }
      } catch (Throwable var3) {
      }

      return null;
   }
}
