package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelAccessor;

public class AshSilkTouchProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         if (!(new Object() {
               public boolean checkGamemode(Entity _ent) {
                  if (_ent instanceof ServerPlayer _serverPlayer) {
                     return _serverPlayer.f_8941_.m_9290_() == GameType.CREATIVE;
                  } else {
                     return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                        ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                           && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.CREATIVE
                        : false;
                  }
               }
            })
            .checkGamemode(entity)) {
            if (Math.random() < 0.3) {
               if (world instanceof ServerLevel _level) {
                  ItemEntity entityToSpawn = new ItemEntity(_level, x, y, z, new ItemStack((ItemLike)AshenremainsModItems.ASH_BALL.get()));
                  entityToSpawn.m_32010_(10);
                  _level.m_7967_(entityToSpawn);
               }

               if (world instanceof ServerLevel _level) {
                  ItemEntity entityToSpawn = new ItemEntity(_level, x, y, z, new ItemStack((ItemLike)AshenremainsModItems.ASH_BALL.get()));
                  entityToSpawn.m_32010_(10);
                  _level.m_7967_(entityToSpawn);
               }
            } else if (Math.random() < 0.6 && world instanceof ServerLevel _level) {
               ItemEntity entityToSpawn = new ItemEntity(_level, x, y, z, new ItemStack((ItemLike)AshenremainsModItems.ASH_BALL.get()));
               entityToSpawn.m_32010_(10);
               _level.m_7967_(entityToSpawn);
            }
         }
      }
   }
}
