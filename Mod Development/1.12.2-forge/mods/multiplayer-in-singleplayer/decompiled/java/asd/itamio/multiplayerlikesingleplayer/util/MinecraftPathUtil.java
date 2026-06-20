package asd.itamio.multiplayerlikesingleplayer.util;

import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public final class MinecraftPathUtil {
   private MinecraftPathUtil() {
   }

   public static File getGameDirectory() {
      try {
         Minecraft mc = Minecraft.func_71410_x();
         Field field = ReflectionHelper.findField(Minecraft.class, new String[]{"gameDir", "mcDataDir", "field_71412_D"});
         Object value = field.get(mc);
         if (value instanceof File) {
            return (File)value;
         }
      } catch (Throwable var3) {
      }

      return new File(".").getAbsoluteFile();
   }
}
