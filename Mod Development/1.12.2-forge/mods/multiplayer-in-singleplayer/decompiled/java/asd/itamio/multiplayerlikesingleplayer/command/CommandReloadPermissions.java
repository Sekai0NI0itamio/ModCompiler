package asd.itamio.multiplayerlikesingleplayer.command;

import asd.itamio.multiplayerlikesingleplayer.service.PermissionSyncResult;
import asd.itamio.multiplayerlikesingleplayer.service.PermissionSyncService;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandReloadPermissions extends MLSPCommandBase {
   public String func_71517_b() {
      return "mlsp_reloadperms";
   }

   public String func_71518_a(ICommandSender sender) {
      return "/mlsp_reloadperms";
   }

   public List<String> func_71514_a() {
      return Collections.singletonList("refresh");
   }

   public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      PermissionSyncResult result = PermissionSyncService.getInstance().syncForCurrentWorld(server);
      sender.func_145747_a(
         new TextComponentString("[MLSP] Permissions reloaded. Added OP: " + result.getOpsAdded() + ", Removed OP: " + result.getOpsRemoved())
      );
   }
}
