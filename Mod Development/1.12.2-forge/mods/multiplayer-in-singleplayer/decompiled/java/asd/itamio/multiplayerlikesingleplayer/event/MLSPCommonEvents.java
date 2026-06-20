package asd.itamio.multiplayerlikesingleplayer.event;

import asd.itamio.multiplayerlikesingleplayer.service.PermissionSyncService;
import asd.itamio.multiplayerlikesingleplayer.service.PlayerIdentityDataService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

public class MLSPCommonEvents {
   @SubscribeEvent
   public void onWorldLoad(Load event) {
      World world = event.getWorld();
      if (!world.field_72995_K && world.field_73011_w.getDimension() == 0) {
         MinecraftServer server = world.func_73046_m();
         if (server != null) {
            PermissionSyncService.getInstance().syncForCurrentWorld(server);
         }
      }
   }

   @SubscribeEvent
   public void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.player instanceof EntityPlayerMP) {
         PlayerIdentityDataService.getInstance().handleHostLogin((EntityPlayerMP)event.player);
      }
   }

   @SubscribeEvent
   public void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.player instanceof EntityPlayerMP) {
         PlayerIdentityDataService.getInstance().handleHostLogout((EntityPlayerMP)event.player);
      }
   }
}
