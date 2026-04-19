package net.mcreator.ashenremains.procedures;

import java.util.Comparator;
import net.mcreator.ashenremains.AshenremainsMod;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GrieferBlindnessProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z) {
      double Duration = 0.0;
      Duration = Mth.m_216271_(RandomSource.m_216327_(), 60, 200);
      Entity var10 = world.m_6443_(Player.class, AABB.m_165882_(new Vec3(x, y, z), 6.0, 6.0, 6.0), e -> true).stream().sorted((new Object() {
         Comparator<Entity> compareDistOf(double _x, double _y, double _z) {
            return Comparator.comparingDouble(_entcnd -> _entcnd.m_20275_(_x, _y, _z));
         }
      }).compareDistOf(x, y, z)).findFirst().orElse(null);
      if (var10 instanceof LivingEntity _entity && !_entity.m_9236_().m_5776_()) {
         _entity.m_7292_(new MobEffectInstance(MobEffects.f_19610_, (int)Duration, 0));
      }

      AshenremainsMod.queueServerWork((int)(Duration - 1.0), () -> {
         Entity patt1729$temp = world.m_6443_(Player.class, AABB.m_165882_(new Vec3(x, y, z), 64.0, 64.0, 64.0), e -> true).stream().sorted((new Object() {
            Comparator<Entity> compareDistOf(double _x, double _y, double _z) {
               return Comparator.comparingDouble(_entcnd -> _entcnd.m_20275_(_x, _y, _z));
            }
         }).compareDistOf(x, y, z)).findFirst().orElse(null);
         if (patt1729$temp instanceof LivingEntity _entityx) {
            _entityx.m_21195_(MobEffects.f_19610_);
         }
      });
   }
}
