package asd.itamio.multiplayerlikesingleplayer.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class IniUtil {
   private IniUtil() {
   }

   public static Map<String, Map<String, String>> parse(String text) {
      Map<String, Map<String, String>> sections = new LinkedHashMap<>();
      Map<String, String> current = new LinkedHashMap<>();
      sections.put("", current);
      String[] lines = text.split("\\r?\\n");

      for(String rawLine : lines) {
         String line = rawLine.trim();
         if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith(";")) {
            if (line.startsWith("[") && line.endsWith("]") && line.length() > 2) {
               String sectionName = line.substring(1, line.length() - 1).trim();
               Map<String, String> section = sections.get(sectionName);
               if (section == null) {
                  section = new LinkedHashMap<>();
                  sections.put(sectionName, section);
               }

               current = section;
            } else {
               int equalsIndex = line.indexOf(61);
               if (equalsIndex > 0) {
                  String key = line.substring(0, equalsIndex).trim();
                  String value = line.substring(equalsIndex + 1).trim();
                  current.put(key, value);
               }
            }
         }
      }

      return sections;
   }

   public static String toString(Map<String, Map<String, String>> sections) {
      StringBuilder builder = new StringBuilder();
      boolean firstSection = true;

      for(Entry<String, Map<String, String>> sectionEntry : sections.entrySet()) {
         String sectionName = sectionEntry.getKey();
         if (!firstSection) {
            builder.append('\n');
         }

         firstSection = false;
         if (!sectionName.isEmpty()) {
            builder.append('[').append(sectionName).append(']').append('\n');
         }

         for(Entry<String, String> valueEntry : sectionEntry.getValue().entrySet()) {
            builder.append(valueEntry.getKey()).append('=').append(valueEntry.getValue()).append('\n');
         }
      }

      return builder.toString();
   }
}
