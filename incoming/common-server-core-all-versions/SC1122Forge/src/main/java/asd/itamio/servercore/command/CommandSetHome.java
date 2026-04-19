package asd.itamio.servercore.command;

import asd.itamio.servercore.data.HomeRecord;
import asd.itamio.servercore.service.HomeService;
import asd.itamio.servercore.util.TeleportUtil;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;

public class CommandSetHome extends ServerCoreCommandBase {
   public String func_71517_b() {
      return "sethome";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/sethome <name>";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      EntityPlayerMP player = requirePlayer(sender);
      if (args.length != 1) {
         throw new CommandException(this.func_71518_a(sender), new Object[0]);
      } else {
         HomeRecord record = HomeService.getInstance().setHome(server, player, args[0]);
         if (record == null) {
            throw new CommandException("Invalid home name. Use 1-32 chars: letters, numbers, _ or -.", new Object[0]);
         } else {
            int x = MathHelper.func_76128_c(record.getX());
            int y = MathHelper.func_76128_c(record.getY());
            int z = MathHelper.func_76128_c(record.getZ());
            player.func_145747_a(
               new TextComponentString(
                  "[ServerCore] Home '"
                     + record.getName()
                     + "' set at "
                     + x
                     + ", "
                     + y
                     + ", "
                     + z
                     + " in "
                     + TeleportUtil.dimensionName(record.getDimension())
                     + "."
               )
            );
         }
      }
   }
}
