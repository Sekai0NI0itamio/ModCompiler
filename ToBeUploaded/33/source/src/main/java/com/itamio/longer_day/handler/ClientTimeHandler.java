package com.itamio.longer_day.handler;

import com.itamio.longer_day.LongerDay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientTimeHandler {
   private static final int VANILLA_DAY_TICKS = 12000;
   private static final int VANILLA_CYCLE_TICKS = 24000;
   private static final int TICKS_PER_MINUTE = 1200;

   private double smoothDayTime = -1;
   private long lastRawServerTime = -1;

   @SubscribeEvent
   public void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.world == null) {
         smoothDayTime = -1;
         lastRawServerTime = -1;
         return;
      }

      World world = mc.world;
      long rawTime = world.getWorldTime();

      if (smoothDayTime < 0 || lastRawServerTime < 0) {
         smoothDayTime = rawTime;
         lastRawServerTime = rawTime;
         return;
      }

      long serverDelta = rawTime - lastRawServerTime;
      if (serverDelta < -100 || serverDelta > 100) {
         smoothDayTime = rawTime;
         lastRawServerTime = rawTime;
         return;
      }

      lastRawServerTime = rawTime;

      long currentDayTime = ((long) smoothDayTime) % VANILLA_CYCLE_TICKS;
      if (currentDayTime < 0) {
         currentDayTime += VANILLA_CYCLE_TICKS;
      }

      boolean isDay = currentDayTime < VANILLA_DAY_TICKS;

      int configuredRealTicks;
      int vanillaTicks;
      if (isDay) {
         configuredRealTicks = LongerDay.dayLengthMinutes * TICKS_PER_MINUTE;
         vanillaTicks = VANILLA_DAY_TICKS;
      } else {
         configuredRealTicks = LongerDay.nightLengthMinutes * TICKS_PER_MINUTE;
         vanillaTicks = VANILLA_DAY_TICKS;
      }

      double ratio = (double) vanillaTicks / configuredRealTicks;
      smoothDayTime += ratio;

      double drift = smoothDayTime - rawTime;
      if (drift > 3.0 || drift < -3.0) {
         smoothDayTime = rawTime;
      }

      world.setWorldTime((long) Math.floor(smoothDayTime));
   }
}
