package asd.itamio.multiplayerlikesingleplayer.service;

import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public final class SessionSwitcher {
   private SessionSwitcher() {
   }

   public static boolean switchOfflineIdentity(String name, UUID uuid) {
      try {
         Session session = new Session(name, uuid.toString().replace("-", ""), "0", "legacy");
         Field field = ReflectionHelper.findField(Minecraft.class, "session", "field_71449_j");
         field.set(Minecraft.func_71410_x(), session);
         return true;
      } catch (Throwable var4) {
         return false;
      }
   }
}
