package com.daycounter.util;

import com.daycounter.config.DayCounterConfig;

public final class DayCounterFormatter {
   private static final long TICKS_PER_DAY = 24000L;
   private static final long TICKS_PER_HOUR = 1000L;

   private DayCounterFormatter() {
   }

   public static String format(long totalWorldTime, long worldTime, DayCounterConfig.DisplayMode displayMode) {
      long dayNumber = Math.max(1L, totalWorldTime / 24000L + 1L);
      String dayText = "Day " + dayNumber;
      if (displayMode == DayCounterConfig.DisplayMode.DAYS) {
         return dayText;
      } else {
         long ticksOfDay = normalizeTicks(worldTime % 24000L);
         long shiftedTicks = normalizeTicks(ticksOfDay + 6000L);
         long totalMinutes = shiftedTicks * 1440L / 24000L;
         int hour24 = (int)(totalMinutes / 60L);
         int minute = (int)(totalMinutes % 60L);
         int hour12 = hour24 % 12;
         if (hour12 == 0) {
            hour12 = 12;
         }

         String meridiem = hour24 >= 12 ? "PM" : "AM";
         return displayMode == DayCounterConfig.DisplayMode.DAYS_HOUR
            ? dayText + " | " + hour12 + " " + meridiem
            : dayText + " | " + hour12 + ":" + twoDigits(minute) + " " + meridiem;
      }
   }

   private static long normalizeTicks(long ticks) {
      long normalized = ticks % 24000L;
      return normalized < 0L ? normalized + 24000L : normalized;
   }

   private static String twoDigits(int value) {
      return value < 10 ? "0" + value : Integer.toString(value);
   }
}
