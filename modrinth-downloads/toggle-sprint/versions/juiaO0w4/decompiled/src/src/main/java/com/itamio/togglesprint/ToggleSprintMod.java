package com.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import org.lwjgl.input.Keyboard;

@Mod(
   modid = "togglesprint",
   name = "Toggle Sprint",
   version = "1.0",
   clientSideOnly = true,
   acceptedMinecraftVersions = "[1.12.2]"
)
public class ToggleSprintMod {
   public static final String MODID = "togglesprint";
   public static final String VERSION = "1.0";
   private boolean sprintToggled = false;

   @EventHandler
   public void init(FMLInitializationEvent event) {
      MinecraftForge.EVENT_BUS.register(this);
   }

   @SubscribeEvent
   public void onKeyInput(KeyInputEvent event) {
      int key = Keyboard.getEventKey();
      boolean pressed = Keyboard.getEventKeyState();
      if (key == 29 && pressed) {
         this.sprintToggled = !this.sprintToggled;
      }
   }

   @SubscribeEvent
   public void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.START) {
         Minecraft mc = Minecraft.func_71410_x();
         if (mc.field_71439_g != null && mc.field_71439_g.field_71158_b != null) {
            if (this.sprintToggled && mc.field_71439_g.field_71158_b.field_192832_b > 0.0F && !mc.field_71439_g.func_70093_af()) {
               mc.field_71439_g.func_70031_b(true);
            }
         }
      }
   }
}
