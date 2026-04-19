package asd.itamio.servercore.command;

import asd.itamio.servercore.data.HomeRecord;
import asd.itamio.servercore.service.HomeService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class CommandDelHome extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "delhome";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/delhome <name>";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      EntityPlayerMP player = requirePlayer(sender);
      if (args.length != 1) {
         throw new CommandException(this.func_71518_a(sender), new Object[0]);
      } else if (HomeService.getInstance().deleteHome(server, player.func_110124_au(), args[0])) {
         player.func_145747_a(new TextComponentString("[ServerCore] Deleted home '" + args[0] + "'."));
      } else {
         throw new CommandException("Home not found: " + args[0], new Object[0]);
      }
   }

   public List<String> func_184883_a(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
      EntityPlayerMP player;
      try {
         player = requirePlayer(sender);
      } catch (CommandException var9) {
         return Collections.emptyList();
      }

      if (args.length != 1) {
         return Collections.emptyList();
      } else {
         List<String> suggestions = new ArrayList<>();

         for (HomeRecord home : HomeService.getInstance().listHomes(server, player.func_110124_au())) {
            suggestions.add(home.getName());
         }

         return func_175762_a(args, suggestions);
      }
   }
}
