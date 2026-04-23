package asd.itamio.daycounter.util;

import asd.itamio.daycounter.config.DayCounterConfig;

public final class DayCounterFormatter {
    private static final long TICKS_PER_DAY = 24000L;

    private DayCounterFormatter() {}

    public static String format(long totalWorldTime, long worldTime, DayCounterConfig.DisplayMode displayMode) {
        long dayNumber = Math.max(1L, totalWorldTime / 24000L + 1L);
        String dayText = "Day " + dayNumber;
        if (displayMode == DayCounterConfig.DisplayMode.DAYS) {
            return dayText;
        }
        long ticksOfDay = normalizeTicks(worldTime % 24000L);
        long shiftedTicks = normalizeTicks(ticksOfDay + 6000L);
        long totalMinutes = shiftedTicks * 1440L / 24000L;
        int hour24 = (int)(totalMinutes / 60L);
        int minute = (int)(totalMinutes % 60L);
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        String meridiem = hour24 >= 12 ? "PM" : "AM";
        if (displayMode == DayCounterConfig.DisplayMode.DAYS_HOUR) {
            return dayText + " | " + hour12 + " " + meridiem;
        }
        return dayText + " | " + hour12 + ":" + twoDigits(minute) + " " + meridiem;
    }

    private static long normalizeTicks(long ticks) {
        long n = ticks % 24000L;
        return n < 0L ? n + 24000L : n;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
