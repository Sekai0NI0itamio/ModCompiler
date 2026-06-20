package asd.itamio.multiplayerlikesingleplayer.command;

import asd.itamio.multiplayerlikesingleplayer.config.GlobalIdentityStore;
import asd.itamio.multiplayerlikesingleplayer.config.UserEntry;
import asd.itamio.multiplayerlikesingleplayer.config.WorldUserConfig;
import asd.itamio.multiplayerlikesingleplayer.config.WorldUserConfigStore;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandMlspStats extends MLSPCommandBase {
   public String func_71517_b() {
      return "mlsp_stats";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/mlsp_stats";
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      if (server == null) {
         sender.func_145747_a(new TextComponentString("[MLSP] Server unavailable."));
      } else {
         String worldFolder = server.func_71270_I();
         WorldUserConfig config = WorldUserConfigStore.getInstance().loadForWorld(worldFolder);
         int users = 0;
         int ops = 0;

         for(UserEntry user : config.getUsers()) {
            ++users;
            if (user.isOp()) {
               ++ops;
            }
         }

         UserEntry currentIdentity = GlobalIdentityStore.getInstance().getCurrentIdentity();
         String identitySummary = currentIdentity == null ? "none" : currentIdentity.getName() + " (" + currentIdentity.getUuid() + ")";
         sender.func_145747_a(new TextComponentString("[MLSP] World: " + worldFolder));
         sender.func_145747_a(new TextComponentString("[MLSP] Known users: " + users + ", OP users: " + ops));
         sender.func_145747_a(new TextComponentString("[MLSP] Current identity: " + identitySummary));
      }
   }
}
