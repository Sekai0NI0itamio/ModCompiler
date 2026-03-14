package com.forsteri.createliquidfuel.util;

public final class MathUtil {
    private MathUtil() {
    }

    public static int gcd(int a, int b) {
        int x = Math.abs(a);
        int y = Math.abs(b);
        while (y != 0) {
            int tmp = x % y;
            x = y;
            y = tmp;
        }
        return x == 0 ? 1 : x;
    }
}
