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

   public HomeRecord(String key, String name, String dimension, double x, double y, double z, float yaw, float pitch) {
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
      return this.key;
   }

   public String getName() {
      return this.name;
   }

   public String getDimension() {
      return this.dimension;
   }

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public double getZ() {
      return this.z;
   }

   public float getYaw() {
      return this.yaw;
   }

   public float getPitch() {
      return this.pitch;
   }
}
