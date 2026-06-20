package asd.itamio.multiplayerlikesingleplayer.gui;

import asd.itamio.multiplayerlikesingleplayer.config.UserEntry;
import asd.itamio.multiplayerlikesingleplayer.core.hooks.MLSPCoreHooks;
import asd.itamio.multiplayerlikesingleplayer.service.IdentityManager;
import asd.itamio.multiplayerlikesingleplayer.util.NameValidator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.world.WorldSettings;
import org.lwjgl.input.Keyboard;

public class GuiIdentityPicker extends GuiScreen {
   private final GuiScreen parent;
   private final String worldFolderName;
   private final String worldDisplayName;
   private final WorldSettings worldSettings;
   private GuiTextField addUserField;
   private GuiIdentityList identityList;
   private List<UserEntry> users = new ArrayList<>();
   private String status = "";

   public GuiIdentityPicker(GuiScreen parent, String worldFolderName, String worldDisplayName, WorldSettings worldSettings) {
      this.parent = parent;
      this.worldFolderName = worldFolderName;
      this.worldDisplayName = worldDisplayName;
      this.worldSettings = worldSettings;
   }

   public void func_73866_w_() {
      Keyboard.enableRepeatEvents(true);
      this.loadUsers();
      this.identityList = new GuiIdentityList(this, this.field_146297_k, this.field_146294_l, this.field_146295_m, 56, this.field_146295_m - 98);
      int centerX = this.field_146294_l / 2;
      this.addUserField = new GuiTextField(20, this.field_146289_q, centerX - 140, this.field_146295_m - 72, 280, 20);
      this.addUserField.func_146195_b(true);
      this.rebuildButtons();
      this.applySelection();
   }

   public void func_146281_b() {
      Keyboard.enableRepeatEvents(false);
   }

   private void loadUsers() {
      this.users = IdentityManager.getInstance().getWorldUsers(this.worldFolderName);
   }

   private void rebuildButtons() {
      this.field_146292_n.clear();
      int centerX = this.field_146294_l / 2;
      this.field_146292_n.add(new GuiButton(0, centerX - 152, this.field_146295_m - 30, 96, 20, "Join"));
      this.field_146292_n.add(new GuiButton(1, centerX - 52, this.field_146295_m - 30, 96, 20, "Cancel"));
      this.field_146292_n.add(new GuiButton(2, centerX + 48, this.field_146295_m - 30, 96, 20, "Add + Join"));
      this.field_146292_n.add(new GuiButton(3, centerX - 152, this.field_146295_m - 54, 96, 20, "Permissions"));
      this.field_146292_n.add(new GuiButton(4, centerX - 52, this.field_146295_m - 54, 96, 20, "Refresh"));
   }

   protected void func_146284_a(GuiButton button) {
      if (button.field_146127_k == 0) {
         this.joinSelected();
      } else if (button.field_146127_k == 1) {
         this.field_146297_k.func_147108_a(this.parent);
      } else if (button.field_146127_k == 2) {
         this.addAndJoin();
      } else if (button.field_146127_k == 3) {
         this.field_146297_k.func_147108_a(new GuiPermissionEditor(this, this.worldFolderName));
      } else {
         if (button.field_146127_k == 4) {
            this.loadUsers();
            this.applySelection();
         }
      }
   }

   protected void func_73869_a(char typedChar, int keyCode) throws IOException {
      if (keyCode == 1) {
         this.field_146297_k.func_147108_a(this.parent);
      } else if (keyCode != 28 && keyCode != 156) {
         if (this.addUserField == null || !this.addUserField.func_146201_a(typedChar, keyCode)) {
            super.func_73869_a(typedChar, keyCode);
         }
      } else {
         if (this.addUserField != null && this.addUserField.func_146206_l()) {
            this.addAndJoin();
         } else {
            this.joinSelected();
         }
      }
   }

   public void func_146274_d() throws IOException {
      super.func_146274_d();
      if (this.identityList != null) {
         this.identityList.func_178039_p();
      }
   }

   protected void func_73864_a(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.func_73864_a(mouseX, mouseY, mouseButton);
      if (this.addUserField != null) {
         this.addUserField.func_146192_a(mouseX, mouseY, mouseButton);
      }
   }

   public void func_73876_c() {
      if (this.addUserField != null) {
         this.addUserField.func_146178_a();
      }
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      this.func_146276_q_();
      int centerX = this.field_146294_l / 2;
      this.func_73732_a(this.field_146289_q, "Select Identity Before Loading World", centerX, 14, 16777215);
      this.func_73732_a(this.field_146289_q, "World: " + this.worldDisplayName + " (" + this.worldFolderName + ")", centerX, 28, 10526880);
      this.func_73732_a(this.field_146289_q, "Scrollable identity list", centerX, 42, 8421504);
      if (this.identityList != null) {
         this.identityList.func_148128_a(mouseX, mouseY, partialTicks);
      }

      this.func_73732_a(this.field_146289_q, "Add username and press Enter or Add + Join", centerX, this.field_146295_m - 84, 10526880);
      if (this.addUserField != null) {
         this.addUserField.func_146194_f();
      }

      if (!this.status.isEmpty()) {
         this.func_73732_a(this.field_146289_q, this.status, centerX, this.field_146295_m - 96, 16733525);
      }

      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   public void onIdentityClicked(int index, boolean doubleClick) {
      if (this.identityList != null) {
         this.identityList.setSelectedIndex(index);
      }

      if (doubleClick) {
         this.joinSelected();
      }
   }

   private void applySelection() {
      if (this.identityList != null) {
         this.identityList.setUsers(this.users);
         UserEntry current = IdentityManager.getInstance().getCurrentIdentityOrSession();
         int selected = -1;

         for(int i = 0; i < this.users.size(); ++i) {
            if (this.users.get(i).getUuid().equals(current.getUuid())) {
               selected = i;
               break;
            }
         }

         if (selected < 0 && !this.users.isEmpty()) {
            selected = 0;
         }

         this.identityList.setSelectedIndex(selected);
      }
   }

   private void joinSelected() {
      if (this.identityList == null) {
         this.status = "Identity list unavailable.";
      } else {
         UserEntry selected = this.identityList.getSelectedEntry();
         if (selected == null) {
            this.status = "Select a user first.";
         } else if (!IdentityManager.getInstance().selectIdentity(selected)) {
            this.status = "Failed to switch to selected identity.";
         } else {
            MLSPCoreHooks.resumeIntegratedLaunch(this.worldFolderName, this.worldDisplayName, this.worldSettings);
         }
      }
   }

   private void addAndJoin() {
      if (this.addUserField == null) {
         this.status = "Input field unavailable.";
      } else {
         String name = this.addUserField.func_146179_b().trim();
         if (!NameValidator.isValidUsername(name)) {
            this.status = "Invalid username. Use 3-16 letters/numbers/_";
         } else {
            UserEntry entry = IdentityManager.getInstance().addOrGetWorldUser(this.worldFolderName, name);
            if (entry == null) {
               this.status = "Failed to add user.";
            } else if (!IdentityManager.getInstance().selectIdentity(entry)) {
               this.status = "Failed to switch identity.";
            } else {
               MLSPCoreHooks.resumeIntegratedLaunch(this.worldFolderName, this.worldDisplayName, this.worldSettings);
            }
         }
      }
   }
}
