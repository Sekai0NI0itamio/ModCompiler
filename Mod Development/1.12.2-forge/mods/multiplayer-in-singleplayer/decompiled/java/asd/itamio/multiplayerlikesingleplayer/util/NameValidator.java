package asd.itamio.multiplayerlikesingleplayer.util;

import java.util.regex.Pattern;

public final class NameValidator {
   private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

   private NameValidator() {
   }

   public static boolean isValidUsername(String value) {
      return value != null && USERNAME_PATTERN.matcher(value).matches();
   }
}
