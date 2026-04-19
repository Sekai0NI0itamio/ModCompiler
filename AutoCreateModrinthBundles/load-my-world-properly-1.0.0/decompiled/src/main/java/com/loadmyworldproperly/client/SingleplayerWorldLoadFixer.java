package com.loadmyworldproperly.client;

import com.loadmyworldproperly.LoadMyWorldProperlyMod;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class SingleplayerWorldLoadFixer {
   private static final int WORLD_READY_TICKS_BEFORE_FIX = 10;
   private static final int STUCK_TICKS_BEFORE_FIX = 40;
   private static final Field SKIP_RENDER_WORLD_FIELD = ReflectionHelper.findField(Minecraft.class, "skipRenderWorld", "field_71454_w");
   private boolean sessionActive = false;
   private int worldReadyTicks = 0;
   private int stuckTicks = 0;
   private boolean fixApplied = false;

   public void onClientTick() {
      Minecraft minecraft = Minecraft.func_71410_x();
      if (minecraft == null) {
         this.reset();
      } else {
         boolean singleplayerSession = minecraft.func_71356_B() || minecraft.func_71401_C() != null;
         if (!singleplayerSession) {
            this.reset();
         } else {
            if (!this.sessionActive) {
               this.sessionActive = true;
               this.worldReadyTicks = 0;
               this.stuckTicks = 0;
               this.fixApplied = false;
            }

            boolean worldReady = minecraft.field_71441_e != null && minecraft.field_71439_g != null;
            boolean loadingScreenVisible = this.isLoadingScreen(minecraft.field_71462_r);
            boolean skipRenderWorld = this.getSkipRenderWorld(minecraft);
            if (!worldReady) {
               this.worldReadyTicks = 0;
               this.stuckTicks = 0;
               this.fixApplied = false;
            } else {
               this.worldReadyTicks++;
               if (!loadingScreenVisible && !skipRenderWorld) {
                  this.stuckTicks = 0;
                  if (this.fixApplied) {
                     this.fixApplied = false;
                  }
               } else {
                  this.stuckTicks++;
               }

               if (!this.fixApplied && this.worldReadyTicks >= 10 && this.stuckTicks >= 40) {
                  this.forceFinishWorldEntry(minecraft);
                  this.fixApplied = true;
               }
            }
         }
      }
   }

   private void reset() {
      this.sessionActive = false;
      this.worldReadyTicks = 0;
      this.stuckTicks = 0;
      this.fixApplied = false;
   }

   private boolean isLoadingScreen(GuiScreen screen) {
      return screen instanceof GuiDownloadTerrain || screen instanceof GuiScreenWorking;
   }

   private boolean getSkipRenderWorld(Minecraft minecraft) {
      try {
         return SKIP_RENDER_WORLD_FIELD.getBoolean(minecraft);
      } catch (IllegalAccessException var3) {
         return false;
      }
   }

   private void setSkipRenderWorld(Minecraft minecraft, boolean value) {
      try {
         SKIP_RENDER_WORLD_FIELD.setBoolean(minecraft, value);
      } catch (IllegalAccessException var4) {
         LoadMyWorldProperlyMod.LOGGER.warn("Load My World PROPERLY could not update skipRenderWorld.", var4);
      }
   }

   private void forceFinishWorldEntry(Minecraft minecraft) {
      this.setSkipRenderWorld(minecraft, false);
      if (minecraft.field_71438_f != null) {
         minecraft.field_71438_f.func_72712_a();
      }

      minecraft.func_147108_a(null);
      if (minecraft.field_71439_g != null) {
         minecraft.func_175607_a(minecraft.field_71439_g);
      }

      minecraft.func_71381_h();
      LoadMyWorldProperlyMod.LOGGER.warn("Load My World PROPERLY forced the client out of the stuck post-loading state for a singleplayer world.");
   }
}
