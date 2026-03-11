package asd.itamio.servercore.command;

import asd.itamio.servercore.service.TeleportRequestService;
import asd.itamio.servercore.util.TeleportUtil;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class CommandTpahere extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "tpahere";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/tpahere <player>";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      EntityPlayerMP requester = requirePlayer(sender);
      if (args.length != 1) {
         throw new CommandException(this.func_71518_a(sender), new Object[0]);
      } else {
         EntityPlayerMP target = TeleportUtil.findOnlinePlayer(server, args[0]);
         if (target == null) {
            throw new CommandException("Player not found: " + args[0], new Object[0]);
         } else if (requester.func_110124_au().equals(target.func_110124_au())) {
            throw new CommandException("You cannot send a teleport request to yourself.", new Object[0]);
         } else {
            TeleportRequestService.getInstance().upsertRequest(requester, target, TeleportRequestService.RequestType.TPAHERE);
            requester.func_145747_a(new TextComponentString("[ServerCore] Teleport-here request sent to " + target.func_70005_c_() + "."));
            target.func_145747_a(
               new TextComponentString(
                  "[ServerCore] "
                     + requester.func_70005_c_()
                     + " wants you to teleport to them. Use /tpaccept "
                     + requester.func_70005_c_()
                     + " or /tpadeny "
                     + requester.func_70005_c_()
                     + "."
               )
            );
         }
      }
   }

   public List<String> func_184883_a(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
      return args.length == 1 ? func_71530_a(args, server.func_71213_z()) : Collections.emptyList();
   }
}
