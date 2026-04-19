package net.itamio.togglesprint.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2561;
import net.minecraft.class_310;

@Environment(EnvType.CLIENT)
public final class ToggleSprintController {
   private boolean sprintLocked;
   private boolean sprintKeyWasDown;

   public void onClientTick(class_310 client) {
      if (client == null || client.field_1690 == null) {
         this.sprintLocked = false;
         this.sprintKeyWasDown = false;
      } else if (client.field_1724 == null) {
         this.sprintLocked = false;
         this.sprintKeyWasDown = false;
      } else if (client.method_1493()) {
         this.sprintKeyWasDown = client.field_1690.field_1867.method_1434();
      } else {
         boolean sprintKeyDown = client.field_1690.field_1867.method_1434();
         if (sprintKeyDown && !this.sprintKeyWasDown) {
            this.sprintLocked = !this.sprintLocked;
            client.field_1690.field_1867.method_23481(false);
            client.field_1724.method_5728(false);
            client.field_1724.method_7353(class_2561.method_43470("Toggle Sprint: " + (this.sprintLocked ? "ON" : "OFF")), true);
         }

         this.sprintKeyWasDown = sprintKeyDown;
         if (this.sprintLocked) {
            client.field_1724.method_5728(this.shouldKeepSprinting(client));
         }
      }
   }

   private boolean shouldKeepSprinting(class_310 client) {
      if (client.field_1755 != null) {
         return false;
      } else if (client.field_1724 == null) {
         return false;
      } else if (client.field_1724.method_7325() || client.field_1724.method_5765()) {
         return false;
      } else {
         return !client.field_1724.method_5715() && !client.field_1724.method_6115()
            ? client.field_1690.field_1894.method_1434() && !client.field_1690.field_1881.method_1434()
            : false;
      }
   }
}
