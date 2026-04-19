package net.itamio.togglesprint.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ToggleSprintController {
   private boolean sprintLocked;
   private boolean sprintKeyWasDown;

   public void onClientTick(Minecraft client) {
      if (client == null || client.options == null) {
         this.sprintLocked = false;
         this.sprintKeyWasDown = false;
      } else if (client.player == null) {
         this.sprintLocked = false;
         this.sprintKeyWasDown = false;
      } else if (client.isPaused()) {
         this.sprintKeyWasDown = client.options.keySprint.isDown();
      } else {
         boolean sprintKeyDown = client.options.keySprint.isDown();
         if (sprintKeyDown && !this.sprintKeyWasDown) {
            this.sprintLocked = !this.sprintLocked;
            client.options.keySprint.setDown(false);
            client.player.setSprinting(false);
            client.player.displayClientMessage(Component.literal("Toggle Sprint: " + (this.sprintLocked ? "ON" : "OFF")), true);
         }

         this.sprintKeyWasDown = sprintKeyDown;
         if (this.sprintLocked) {
            client.player.setSprinting(this.shouldKeepSprinting(client));
         }
      }
   }

   private boolean shouldKeepSprinting(Minecraft client) {
      if (client.screen != null) {
         return false;
      } else if (client.player == null) {
         return false;
      } else if (client.player.isSpectator() || client.player.isPassenger()) {
         return false;
      } else {
         return !client.player.isShiftKeyDown() && !client.player.isUsingItem() ? client.options.keyUp.isDown() && !client.options.keyDown.isDown() : false;
      }
   }
}
