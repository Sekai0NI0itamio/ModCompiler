package asd.itamio.servercore.teleport;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public class FixedPositionTeleporter extends Teleporter {
   private final double x;
   private final double y;
   private final double z;
   private final float yaw;
   private final float pitch;

   public FixedPositionTeleporter(WorldServer worldIn, double x, double y, double z, float yaw, float pitch) {
      super(worldIn);
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.pitch = pitch;
   }

   public void func_180266_a(Entity entity, float rotationYaw) {
      entity.func_70012_b(this.x, this.y, this.z, this.yaw, this.pitch);
      entity.field_70159_w = 0.0;
      entity.field_70181_x = 0.0;
      entity.field_70179_y = 0.0;
   }

   public boolean func_180620_b(Entity entity, float rotationYaw) {
      this.func_180266_a(entity, rotationYaw);
      return true;
   }

   public boolean func_85188_a(Entity entityIn) {
      return true;
   }

   public void func_85189_a(long worldTime) {
   }
}
