package asd.itamio.servercore.command;

import asd.itamio.servercore.service.TeleportRequestService;
import asd.itamio.servercore.util.TeleportUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class TeleportRequestCommandHelper {
   private TeleportRequestCommandHelper() {
   }

   public static String acceptRequest(EntityPlayerMP receiver, TeleportRequestService.TeleportRequest request) {
      if (receiver != null && request != null && receiver.func_184102_h() != null) {
         MinecraftServer server = receiver.func_184102_h();
         EntityPlayerMP requester = server.func_184103_al().func_177451_a(request.getRequesterUuid());
         if (requester == null) {
            requester = TeleportUtil.findOnlinePlayer(server, request.getRequesterName());
         }

         if (requester == null) {
            return "Requester " + request.getRequesterName() + " is no longer online.";
         } else if (request.getType() == TeleportRequestService.RequestType.TPA) {
            TeleportUtil.teleportPlayer(
               requester,
               receiver.field_71093_bK,
               receiver.field_70165_t,
               receiver.field_70163_u,
               receiver.field_70161_v,
               receiver.field_70177_z,
               receiver.field_70125_A
            );
            requester.func_145747_a(new TextComponentString("[ServerCore] " + receiver.func_70005_c_() + " accepted your /tpa request."));
            receiver.func_145747_a(new TextComponentString("[ServerCore] Accepted /tpa request from " + requester.func_70005_c_() + "."));
            return null;
         } else {
            TeleportUtil.teleportPlayer(
               receiver,
               requester.field_71093_bK,
               requester.field_70165_t,
               requester.field_70163_u,
               requester.field_70161_v,
               requester.field_70177_z,
               requester.field_70125_A
            );
            requester.func_145747_a(new TextComponentString("[ServerCore] " + receiver.func_70005_c_() + " accepted your /tpahere request."));
            receiver.func_145747_a(new TextComponentString("[ServerCore] Accepted /tpahere request from " + requester.func_70005_c_() + "."));
            return null;
         }
      } else {
         return "Unable to process request.";
      }
   }
}
