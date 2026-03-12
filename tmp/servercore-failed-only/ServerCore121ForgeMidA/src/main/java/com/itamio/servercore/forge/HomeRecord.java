package com.itamio.servercore.forge;

public final class HomeRecord {
    private final String key;
    private final String name;
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public HomeRecord(
            String key,
            String name,
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        this.key = key;
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDimension() {
        return dimension;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
