package asd.itamio.multiplayerlikesingleplayer.gui;

import asd.itamio.multiplayerlikesingleplayer.config.UserEntry;
import asd.itamio.multiplayerlikesingleplayer.service.IdentityManager;
import asd.itamio.multiplayerlikesingleplayer.service.PermissionSyncResult;
import asd.itamio.multiplayerlikesingleplayer.service.PermissionSyncService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class GuiPermissionEditor extends GuiScreen {
   private static final int PAGE_SIZE = 8;
   private final GuiScreen parent;
   private final String worldFolderName;
   private List<UserEntry> users = new ArrayList<>();
   private int page;
   private String status = "";

   public GuiPermissionEditor(GuiScreen parent, String worldFolderName) {
      this.parent = parent;
      this.worldFolderName = worldFolderName;
   }

   public void func_73866_w_() {
      this.reloadUsers();
   }

   private void reloadUsers() {
      List<UserEntry> source = IdentityManager.getInstance().getWorldUsers(this.worldFolderName);
      this.users.clear();

      for(UserEntry user : source) {
         this.users.add(new UserEntry(user.getUuid(), user.getName(), user.isOp()));
      }

      this.rebuildButtons();
   }

   private void rebuildButtons() {
      this.field_146292_n.clear();
      int centerX = this.field_146294_l / 2;
      int top = this.field_146295_m / 2 - 90;
      int start = this.page * 8;
      int end = Math.min(start + 8, this.users.size());

      for(int i = start; i < end; ++i) {
         UserEntry user = this.users.get(i);
         int row = i - start;
         String label = user.getName() + " | OP: " + (user.isOp() ? "ON" : "OFF");
         this.field_146292_n.add(new GuiButton(100 + i, centerX - 140, top + row * 22, 280, 20, label));
      }

      this.field_146292_n.add(new GuiButton(0, centerX - 154, this.field_146295_m - 28, 100, 20, "Done"));
      this.field_146292_n.add(new GuiButton(1, centerX - 50, this.field_146295_m - 28, 100, 20, "Save"));
      this.field_146292_n.add(new GuiButton(2, centerX + 54, this.field_146295_m - 28, 48, 20, "<"));
      this.field_146292_n.add(new GuiButton(3, centerX + 106, this.field_146295_m - 28, 48, 20, ">"));
   }

   protected void func_146284_a(GuiButton button) {
      if (button.field_146127_k == 0) {
         this.field_146297_k.func_147108_a(this.parent);
      } else if (button.field_146127_k == 1) {
         this.saveChanges();
      } else if (button.field_146127_k == 2) {
         if (this.page > 0) {
            --this.page;
            this.rebuildButtons();
         }
      } else if (button.field_146127_k == 3) {
         int maxPage = Math.max(0, (this.users.size() - 1) / 8);
         if (this.page < maxPage) {
            ++this.page;
            this.rebuildButtons();
         }
      } else {
         if (button.field_146127_k >= 100) {
            int index = button.field_146127_k - 100;
            if (index >= 0 && index < this.users.size()) {
               UserEntry user = this.users.get(index);
               user.setOp(!user.isOp());
               this.rebuildButtons();
            }
         }
      }
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      this.func_146276_q_();
      int centerX = this.field_146294_l / 2;
      this.func_73732_a(this.field_146289_q, "MLSP Permission Editor", centerX, 16, 16777215);
      this.func_73732_a(this.field_146289_q, "World: " + this.worldFolderName, centerX, 30, 10526880);
      this.func_73732_a(this.field_146289_q, "Click a user row to toggle OP", centerX, 44, 8421504);
      int maxPage = Math.max(1, (this.users.size() + 8 - 1) / 8);
      this.func_73732_a(this.field_146289_q, "Page " + (this.page + 1) + " / " + maxPage, centerX, 58, 8421504);
      if (!this.status.isEmpty()) {
         this.func_73732_a(this.field_146289_q, this.status, centerX, this.field_146295_m - 42, 5635925);
      }

      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   private void saveChanges() {
      IdentityManager.getInstance().saveWorldUsers(this.worldFolderName, this.users);
      MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
      if (server != null) {
         PermissionSyncResult result = PermissionSyncService.getInstance().syncForCurrentWorld(server);
         this.status = "Saved. OP added: " + result.getOpsAdded() + ", removed: " + result.getOpsRemoved();
      } else {
         this.status = "Saved to file.";
      }
   }
}
