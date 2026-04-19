package com.daycounter.util;

import com.daycounter.config.DayCounterConfig.DisplayMode;

public final class DayCounterFormatter {

    private static final long TICKS_PER_DAY = 24000L;
    private static final long TICKS_PER_HOUR = 1000L;

    private DayCounterFormatter() {
    }

    public static String format(long totalWorldTime, long worldTime, DisplayMode displayMode) {
        long dayNumber = Math.max(1L, (totalWorldTime / TICKS_PER_DAY) + 1L);
        String dayText = "Day " + dayNumber;

        if (displayMode == DisplayMode.DAYS) {
            return dayText;
        }

        long ticksOfDay = normalizeTicks(worldTime % TICKS_PER_DAY);
        long shiftedTicks = normalizeTicks(ticksOfDay + (TICKS_PER_HOUR * 6L));
        long totalMinutes = (shiftedTicks * 1440L) / TICKS_PER_DAY;

        int hour24 = (int) (totalMinutes / 60L);
        int minute = (int) (totalMinutes % 60L);
        int hour12 = hour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String meridiem = hour24 >= 12 ? "PM" : "AM";

        if (displayMode == DisplayMode.DAYS_HOUR) {
            return dayText + " | " + hour12 + " " + meridiem;
        }

        return dayText + " | " + hour12 + ":" + twoDigits(minute) + " " + meridiem;
    }

    private static long normalizeTicks(long ticks) {
        long normalized = ticks % TICKS_PER_DAY;
        return normalized < 0L ? normalized + TICKS_PER_DAY : normalized;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
