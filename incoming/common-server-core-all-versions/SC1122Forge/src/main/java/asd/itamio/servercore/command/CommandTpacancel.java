package asd.itamio.servercore.command;

import asd.itamio.servercore.service.TeleportRequestService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class CommandTpacancel extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "tpacancel";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/tpacancel <player|all>";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      EntityPlayerMP requester = requirePlayer(sender);
      if (args.length != 1) {
         throw new CommandException(this.func_71518_a(sender), new Object[0]);
      } else {
         String target = args[0];
         int removed = TeleportRequestService.getInstance().cancelOutgoing(requester.func_110124_au(), target);
         if (removed <= 0) {
            requester.func_145747_a(new TextComponentString("[ServerCore] No matching outgoing requests found."));
         } else {
            requester.func_145747_a(new TextComponentString("[ServerCore] Cancelled " + removed + " outgoing request(s)."));
         }
      }
   }

   public List<String> func_184883_a(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
      if (args.length == 1) {
         List<String> options = new ArrayList<>();
         options.add("all");
         Collections.addAll(options, server.func_71213_z());
         return func_175762_a(args, options);
      } else {
         return Collections.emptyList();
      }
   }
}
