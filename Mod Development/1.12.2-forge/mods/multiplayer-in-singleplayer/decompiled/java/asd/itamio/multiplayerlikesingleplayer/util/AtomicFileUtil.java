package asd.itamio.multiplayerlikesingleplayer.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class AtomicFileUtil {
   private AtomicFileUtil() {
   }

   public static void writeAtomic(File destination, String content) throws IOException {
      File parent = destination.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
      } else {
         File temp = new File(destination.getParentFile(), destination.getName() + ".tmp");
         FileOutputStream stream = new FileOutputStream(temp);

         try {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
            stream.flush();
         } finally {
            stream.close();
         }

         try {
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var8) {
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      }
   }
}
