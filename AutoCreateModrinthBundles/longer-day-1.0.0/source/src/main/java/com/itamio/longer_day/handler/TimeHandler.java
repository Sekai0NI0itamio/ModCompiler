package com.itamio.longer_day.handler;

import com.itamio.longer_day.LongerDay;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;

public class TimeHandler {
   private static final int VANILLA_DAY_TICKS = 12000;
   private static final int VANILLA_NIGHT_TICKS = 12000;
   private static final int VANILLA_CYCLE_TICKS = 24000;
   private static final int TICKS_PER_MINUTE = 1200;

   private final Map<Integer, TimeState> worldStates = new HashMap<>();

   private static final class TimeState {
      double desiredWorldTime;
      long lastSetTime;
      boolean initialized = false;
   }

   @SubscribeEvent
   public void onWorldTick(TickEvent.WorldTickEvent event) {
      if (event.world.isRemote) {
         return;
      }
      if (event.phase != TickEvent.Phase.START) {
         return;
      }

      World world = event.world;
      int dimId = world.provider.getDimension();
      TimeState state = worldStates.computeIfAbsent(dimId, k -> new TimeState());

      long currentWorldTime = world.getWorldTime();

      if (!state.initialized) {
         state.desiredWorldTime = currentWorldTime;
         state.lastSetTime = currentWorldTime;
         state.initialized = true;
         return;
      }

      if (Math.abs(currentWorldTime - state.lastSetTime - 1) > 2) {
         state.desiredWorldTime = currentWorldTime;
      }

      long currentDayTime = ((long) state.desiredWorldTime) % VANILLA_CYCLE_TICKS;
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
         vanillaTicks = VANILLA_NIGHT_TICKS;
      }

      double ratio = (double) vanillaTicks / configuredRealTicks;
      state.desiredWorldTime += ratio;

      long newWorldTime = (long) Math.floor(state.desiredWorldTime);
      world.setWorldTime(newWorldTime);
      state.lastSetTime = newWorldTime;
   }
}
