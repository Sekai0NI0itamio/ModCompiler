package asd.itamio.servercore.event;

import asd.itamio.servercore.ServerCoreMod;
import asd.itamio.servercore.service.RandomTeleportService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

public class ServerCoreEvents {
   private static final String TAG_ROOT = "servercore";
   private static final String TAG_FIRST_JOIN_RTP_DONE = "firstJoinRtpDone";

   @SubscribeEvent
   public void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.player instanceof EntityPlayerMP) {
         EntityPlayerMP player = (EntityPlayerMP)event.player;
         if (!hasCompletedFirstJoinRtp(player)) {
            RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, 0);
            if (!result.isSuccess()) {
               player.func_145747_a(new TextComponentString("[ServerCore] First-join RTP failed: " + result.getMessage()));
               ServerCoreMod.LOGGER.warn("Failed first-join RTP for {}: {}", player.func_70005_c_(), result.getMessage());
            } else {
               markFirstJoinRtpComplete(player);
               BlockPos location = result.getLocation();
               if (location != null) {
                  player.func_145747_a(
                     new TextComponentString(
                        "[ServerCore] First join spawn set by RTP (10k square): "
                           + location.func_177958_n()
                           + ", "
                           + location.func_177956_o()
                           + ", "
                           + location.func_177952_p()
                     )
                  );
               } else {
                  player.func_145747_a(new TextComponentString("[ServerCore] First join spawn set by RTP (10k square)."));
               }
            }
         }
      }
   }

   private static boolean hasCompletedFirstJoinRtp(EntityPlayerMP player) {
      NBTTagCompound persisted = getOrCreatePersisted(player);
      NBTTagCompound root = persisted.func_74775_l("servercore");
      return root.func_74767_n("firstJoinRtpDone");
   }

   private static void markFirstJoinRtpComplete(EntityPlayerMP player) {
      NBTTagCompound persisted = getOrCreatePersisted(player);
      NBTTagCompound root = persisted.func_74775_l("servercore");
      root.func_74757_a("firstJoinRtpDone", true);
      persisted.func_74782_a("servercore", root);
      player.getEntityData().func_74782_a("PlayerPersisted", persisted);
   }

   private static NBTTagCompound getOrCreatePersisted(EntityPlayerMP player) {
      NBTTagCompound data = player.getEntityData();
      if (!data.func_150297_b("PlayerPersisted", 10)) {
         data.func_74782_a("PlayerPersisted", new NBTTagCompound());
      }

      return data.func_74775_l("PlayerPersisted");
   }
}
