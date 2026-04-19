package asd.itamio.servercore.command;

import asd.itamio.servercore.service.RandomTeleportService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class CommandRtp extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "rtp";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/rtp <overworld|nether|end>";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      EntityPlayerMP player = requirePlayer(sender);
      if (args.length != 1) {
         throw new CommandException(this.func_71518_a(sender), new Object[0]);
      } else {
         int dimension = resolveDimension(args[0]);
         RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, dimension);
         if (!result.isSuccess()) {
            throw new CommandException("[ServerCore] " + result.getMessage(), new Object[0]);
         } else if (result.getLocation() != null) {
            BlockPos location = result.getLocation();
            player.func_145747_a(
               new TextComponentString(
                  "[ServerCore] "
                     + result.getMessage()
                     + " ("
                     + location.func_177958_n()
                     + ", "
                     + location.func_177956_o()
                     + ", "
                     + location.func_177952_p()
                     + ")"
               )
            );
         } else {
            player.func_145747_a(new TextComponentString("[ServerCore] " + result.getMessage()));
         }
      }
   }

   private static int resolveDimension(String arg) throws CommandException {
      if ("overworld".equalsIgnoreCase(arg) || "world".equalsIgnoreCase(arg) || "0".equals(arg)) {
         return 0;
      } else if ("nether".equalsIgnoreCase(arg) || "-1".equals(arg)) {
         return -1;
      } else if (!"end".equalsIgnoreCase(arg) && !"the_end".equalsIgnoreCase(arg) && !"1".equals(arg)) {
         throw new CommandException("Unknown dimension: " + arg + ". Use overworld, nether, or end.", new Object[0]);
      } else {
         return 1;
      }
   }

   public List<String> func_184883_a(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
      return args.length == 1 ? func_175762_a(args, Arrays.asList("overworld", "nether", "end")) : Collections.emptyList();
   }
}
