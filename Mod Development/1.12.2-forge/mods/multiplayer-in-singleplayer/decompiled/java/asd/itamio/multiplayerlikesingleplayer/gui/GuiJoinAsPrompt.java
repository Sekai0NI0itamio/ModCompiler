package asd.itamio.multiplayerlikesingleplayer.gui;

import asd.itamio.multiplayerlikesingleplayer.core.hooks.MLSPCoreHooks;
import asd.itamio.multiplayerlikesingleplayer.service.IdentityManager;
import asd.itamio.multiplayerlikesingleplayer.util.NameValidator;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.lwjgl.input.Keyboard;

public class GuiJoinAsPrompt extends GuiScreen {
   private final GuiScreen parent;
   private final ServerData directServer;
   private GuiTextField nameField;
   private String status = "";

   public GuiJoinAsPrompt(GuiScreen parent, ServerData directServer) {
      this.parent = parent;
      this.directServer = directServer;
   }

   public void func_73866_w_() {
      Keyboard.enableRepeatEvents(true);
      int centerX = this.field_146294_l / 2;
      int centerY = this.field_146295_m / 2;
      this.field_146292_n.clear();
      this.field_146292_n.add(new GuiButton(0, centerX - 102, centerY + 30, 100, 20, "Join"));
      this.field_146292_n.add(new GuiButton(1, centerX + 2, centerY + 30, 100, 20, "Cancel"));
      this.nameField = new GuiTextField(2, this.field_146289_q, centerX - 120, centerY - 5, 240, 20);
      this.nameField.func_146195_b(true);
      String current = IdentityManager.getInstance().getCurrentIdentityOrSession().getName();
      this.nameField.func_146180_a(current == null ? "" : current);
   }

   public void func_146281_b() {
      Keyboard.enableRepeatEvents(false);
   }

   protected void func_146284_a(GuiButton button) {
      if (button.field_146127_k == 0) {
         this.submit();
      } else if (button.field_146127_k == 1) {
         this.field_146297_k.func_147108_a(this.parent);
      }
   }

   protected void func_73869_a(char typedChar, int keyCode) throws IOException {
      if (keyCode == 28 || keyCode == 156) {
         this.submit();
      } else if (keyCode == 1) {
         this.field_146297_k.func_147108_a(this.parent);
      } else {
         this.nameField.func_146201_a(typedChar, keyCode);
      }
   }

   protected void func_73864_a(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.func_73864_a(mouseX, mouseY, mouseButton);
      this.nameField.func_146192_a(mouseX, mouseY, mouseButton);
   }

   public void func_73876_c() {
      this.nameField.func_146178_a();
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      this.func_146276_q_();
      int centerX = this.field_146294_l / 2;
      int centerY = this.field_146295_m / 2;
      this.func_73732_a(this.field_146289_q, "Join As Offline Identity", centerX, centerY - 45, 16777215);
      this.func_73732_a(this.field_146289_q, "Username (3-16, letters/numbers/_)", centerX, centerY - 20, 10526880);
      this.nameField.func_146194_f();
      if (!this.status.isEmpty()) {
         this.func_73732_a(this.field_146289_q, this.status, centerX, centerY + 58, 16733525);
      }

      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   private void submit() {
      String name = this.nameField.func_146179_b().trim();
      if (!NameValidator.isValidUsername(name)) {
         this.status = "Invalid username format.";
      } else if (IdentityManager.getInstance().selectOrCreateGlobalIdentity(name) == null) {
         this.status = "Failed to switch identity.";
      } else if (this.directServer != null) {
         FMLClientHandler.instance().connectToServer(this.parent, this.directServer);
      } else if (this.parent instanceof GuiMultiplayer) {
         MLSPCoreHooks.resumeMultiplayerConnect((GuiMultiplayer)this.parent);
      } else {
         this.field_146297_k.func_147108_a(this.parent);
      }
   }
}
