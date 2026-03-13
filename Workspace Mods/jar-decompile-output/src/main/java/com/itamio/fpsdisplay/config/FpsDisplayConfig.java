package com.itamio.fpsdisplay.config;

import com.itamio.fpsdisplay.FpsDisplay;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class FpsDisplayConfig {
   private static final String CONFIG_DIR = "config";
   private static final String CONFIG_FILE = "fpsdisplay.cfg";
   private boolean enabled = true;

   public FpsDisplayConfig() {
      this.loadConfig();
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   private void loadConfig() {
      try {
         File configDir = new File("config");
         if (!configDir.exists()) {
            configDir.mkdirs();
         }

         File file = new File(configDir, "fpsdisplay.cfg");
         if (!file.exists()) {
            this.saveConfig();
            return;
         }

         Scanner scanner = new Scanner(file);

         while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("on")) {
               this.enabled = true;
            } else if (line.equalsIgnoreCase("off")) {
               this.enabled = false;
            }
         }

         scanner.close();
      } catch (IOException var5) {
         FpsDisplay.LOGGER.error("Failed to load config: " + var5.getMessage());
      }
   }

   public void saveConfig() {
      try {
         File configDir = new File("config");
         if (!configDir.exists()) {
            configDir.mkdirs();
         }

         File file = new File(configDir, "fpsdisplay.cfg");
         FileWriter writer = new FileWriter(file, false);
         writer.write(this.enabled ? "on\n" : "off\n");
         writer.close();
      } catch (IOException var4) {
         FpsDisplay.LOGGER.error("Failed to save config: " + var4.getMessage());
      }
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
      this.saveConfig();
   }
}
