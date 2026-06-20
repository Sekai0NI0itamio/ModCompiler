package asd.itamio.multiplayerlikesingleplayer.gui;

import asd.itamio.multiplayerlikesingleplayer.config.UserEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;

public class GuiIdentityList extends GuiSlot {
   private final GuiIdentityPicker owner;
   private List<UserEntry> users = new ArrayList<>();
   private int selectedIndex = -1;

   public GuiIdentityList(GuiIdentityPicker owner, Minecraft minecraft, int width, int height, int top, int bottom) {
      super(minecraft, width, height, top, bottom, 24);
      this.owner = owner;
   }

   public void setUsers(List<UserEntry> users) {
      this.users = (List<UserEntry>)(users == null ? new ArrayList<>() : users);
   }

   public int getSelectedIndex() {
      return this.selectedIndex;
   }

   public void setSelectedIndex(int selectedIndex) {
      this.selectedIndex = selectedIndex;
   }

   public UserEntry getSelectedEntry() {
      return this.selectedIndex >= 0 && this.selectedIndex < this.users.size() ? this.users.get(this.selectedIndex) : null;
   }

   protected int func_148127_b() {
      return this.users.size();
   }

   protected void func_148144_a(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
      this.selectedIndex = slotIndex;
      this.owner.onIdentityClicked(slotIndex, isDoubleClick);
   }

   protected boolean func_148131_a(int slotIndex) {
      return slotIndex == this.selectedIndex;
   }

   protected void func_148123_a() {
   }

   protected void func_192637_a(int entryId, int x, int y, int slotHeight, int mouseX, int mouseY, float partialTicks) {
      UserEntry user = this.users.get(entryId);
      String primary = user.getName() + (user.isOp() ? " [OP]" : "");
      String secondary = user.getUuid().toString();
      this.field_148161_k.field_71466_p.func_78276_b(primary, x + 4, y + 3, 16777215);
      this.field_148161_k.field_71466_p.func_78276_b(secondary, x + 4, y + 13, 8421504);
   }
}
