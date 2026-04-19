package asd.itamio.servercore.command;

import asd.itamio.servercore.service.TeleportRequestService;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class CommandTpadeny extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "tpadeny";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/tpadeny <player>";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      EntityPlayerMP receiver = requirePlayer(sender);
      if (args.length != 1) {
         throw new CommandException(this.func_71518_a(sender), new Object[0]);
      } else {
         TeleportRequestService.TeleportRequest request = TeleportRequestService.getInstance().popIncoming(receiver.func_110124_au(), args[0]);
         if (request == null) {
            receiver.func_145747_a(new TextComponentString("[ServerCore] No pending request from " + args[0] + "."));
         } else {
            receiver.func_145747_a(new TextComponentString("[ServerCore] Denied request from " + request.getRequesterName() + "."));
            EntityPlayerMP requester = server.func_184103_al().func_177451_a(request.getRequesterUuid());
            if (requester != null) {
               requester.func_145747_a(new TextComponentString("[ServerCore] " + receiver.func_70005_c_() + " denied your teleport request."));
            }
         }
      }
   }

   public List<String> func_184883_a(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
      return args.length == 1 ? func_71530_a(args, server.func_71213_z()) : Collections.emptyList();
   }
}
