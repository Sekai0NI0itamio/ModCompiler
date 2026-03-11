package asd.itamio.servercore.command;

import asd.itamio.servercore.service.TeleportRequestService;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandTpacceptAll extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "tpacceptall";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/tpacceptall";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      EntityPlayerMP receiver = requirePlayer(sender);
      if (args.length != 0) {
         throw new CommandException(this.func_71518_a(sender), new Object[0]);
      } else {
         List<TeleportRequestService.TeleportRequest> requests = TeleportRequestService.getInstance().popAllIncoming(receiver.func_110124_au());
         if (requests.isEmpty()) {
            receiver.func_145747_a(new TextComponentString("[ServerCore] You have no pending teleport requests."));
         } else {
            int accepted = 0;
            int failed = 0;

            for (TeleportRequestService.TeleportRequest request : requests) {
               String error = TeleportRequestCommandHelper.acceptRequest(receiver, request);
               if (error == null) {
                  accepted++;
               } else {
                  failed++;
                  receiver.func_145747_a(new TextComponentString("[ServerCore] " + error));
               }
            }

            receiver.func_145747_a(new TextComponentString("[ServerCore] Accepted " + accepted + " request(s), skipped " + failed + "."));
         }
      }
   }
}
