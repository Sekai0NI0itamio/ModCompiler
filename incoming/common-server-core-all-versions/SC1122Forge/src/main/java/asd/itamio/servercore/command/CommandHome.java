package asd.itamio.servercore.command;

import asd.itamio.servercore.data.HomeRecord;
import asd.itamio.servercore.service.HomeService;
import asd.itamio.servercore.util.TeleportUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;

public class CommandHome extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "home";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/home <name|list>";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      EntityPlayerMP player = requirePlayer(sender);
      if (args.length != 1) {
         throw new CommandException(this.func_71518_a(sender), new Object[0]);
      } else if ("list".equalsIgnoreCase(args[0])) {
         List<HomeRecord> homes = HomeService.getInstance().listHomes(server, player.func_110124_au());
         if (homes.isEmpty()) {
            player.func_145747_a(new TextComponentString("[ServerCore] You have no homes set."));
         } else {
            StringBuilder names = new StringBuilder();

            for (int i = 0; i < homes.size(); i++) {
               if (i > 0) {
                  names.append(", ");
               }

               names.append(homes.get(i).getName());
            }

            player.func_145747_a(new TextComponentString("[ServerCore] Homes (" + homes.size() + "): " + names));
         }
      } else {
         HomeRecord home = HomeService.getInstance().getHome(server, player.func_110124_au(), args[0]);
         if (home == null) {
            throw new CommandException("Home not found: " + args[0], new Object[0]);
         } else {
            TeleportUtil.teleportPlayer(player, home.getDimension(), home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch());
            int x = MathHelper.func_76128_c(home.getX());
            int y = MathHelper.func_76128_c(home.getY());
            int z = MathHelper.func_76128_c(home.getZ());
            player.func_145747_a(
               new TextComponentString(
                  "[ServerCore] Teleported to home '"
                     + home.getName()
                     + "' at "
                     + x
                     + ", "
                     + y
                     + ", "
                     + z
                     + " in "
                     + TeleportUtil.dimensionName(home.getDimension())
                     + "."
               )
            );
         }
      }
   }

   public List<String> func_184883_a(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
      EntityPlayerMP player;
      try {
         player = requirePlayer(sender);
      } catch (CommandException var10) {
         return Collections.emptyList();
      }

      if (args.length != 1) {
         return Collections.emptyList();
      } else {
         List<String> suggestions = new ArrayList<>();
         suggestions.add("list");

         for (HomeRecord home : HomeService.getInstance().listHomes(server, player.func_110124_au())) {
            suggestions.add(home.getName());
         }

         return func_175762_a(args, suggestions);
      }
   }
}
