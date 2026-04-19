package asd.itamio.servercore.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

public abstract class ServerCoreCommandBase extends CommandBase {
   public int func_82362_a() {
      return 0;
   }

   public boolean func_184882_a(MinecraftServer server, ICommandSender sender) {
      return true;
   }

   protected static EntityPlayerMP requirePlayer(ICommandSender sender) throws CommandException {
      return func_71521_c(sender);
   }
}
