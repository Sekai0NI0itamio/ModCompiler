package asd.itamio.multiplayerlikesingleplayer.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public abstract class MLSPCommandBase extends CommandBase {
   public int func_82362_a() {
      return 0;
   }

   public boolean func_184882_a(MinecraftServer server, ICommandSender sender) {
      return true;
   }
}
