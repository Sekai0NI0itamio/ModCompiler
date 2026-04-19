package asd.itamio.servercore.command;

import asd.itamio.servercore.service.TeleportRequestService;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandTpadenyAll extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "tpadenyall";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/tpadenyall";
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
            int denied = 0;

            for (TeleportRequestService.TeleportRequest request : requests) {
               denied++;
               EntityPlayerMP requester = server.func_184103_al().func_177451_a(request.getRequesterUuid());
               if (requester != null) {
                  requester.func_145747_a(new TextComponentString("[ServerCore] " + receiver.func_70005_c_() + " denied your teleport request."));
               }
            }

            receiver.func_145747_a(new TextComponentString("[ServerCore] Denied " + denied + " request(s)."));
         }
      }
   }
}
