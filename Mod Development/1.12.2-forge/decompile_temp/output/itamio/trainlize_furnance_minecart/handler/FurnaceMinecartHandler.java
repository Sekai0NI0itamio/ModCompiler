package com.itamio.trainlize_furnance_minecart.handler;

import com.itamio.trainlize_furnance_minecart.TrainlizeFurnanceMinecart;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;

@EventBusSubscriber(
   modid = "trainlize_furnance_minecart"
)
public final class FurnaceMinecartHandler {
   private static final Map<UUID, FurnaceMinecartHandler.CartInfo> CART_DATA = new ConcurrentHashMap<>();
   private static final double EPSILON = 1.0E-8;
   private static final double DELTA = 1.0E-6;
   private static long tickCounter = 0L;
   private static final double PUSH_MAX_DISTANCE = 1.5;
   private static final double PUSH_DOT_THRESHOLD = 0.05;

   private FurnaceMinecartHandler() {
   }

   @SubscribeEvent
   public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
      if (!event.getWorld().field_72995_K) {
         Entity entity = event.getEntity();
         if (entity instanceof EntityMinecartFurnace) {
            EntityMinecartFurnace cart = (EntityMinecartFurnace)entity;
            double vx = cart.field_70159_w;
            double vz = cart.field_70179_y;
            double speedSq = vx * vx + vz * vz;
            double dirX = 0.0;
            double dirZ = 0.0;
            if (speedSq > 1.0E-8) {
               double inv = 1.0 / Math.sqrt(speedSq);
               dirX = vx * inv;
               dirZ = vz * inv;
            }

            CART_DATA.put(cart.func_110124_au(), new FurnaceMinecartHandler.CartInfo(speedSq, dirX, dirZ));
            if (TrainlizeFurnanceMinecart.logger != null) {
               TrainlizeFurnanceMinecart.logger.debug("Registered furnace cart {} initial speed {}", cart.func_110124_au(), Math.sqrt(speedSq));
            }
         }
      }
   }

   @SubscribeEvent
   public static void onWorldTick(WorldTickEvent event) {
      if (event.phase == Phase.END) {
         World world = event.world;
         if (!world.field_72995_K) {
            ++tickCounter;

            for(Entity entity : world.field_72996_f) {
               if (entity instanceof EntityMinecartFurnace) {
                  EntityMinecartFurnace cart = (EntityMinecartFurnace)entity;
                  UUID id = cart.func_110124_au();
                  double vx = cart.field_70159_w;
                  double vz = cart.field_70179_y;
                  double speedSq = vx * vx + vz * vz;
                  FurnaceMinecartHandler.CartInfo info = CART_DATA.get(id);
                  if (info == null) {
                     double dirX = 0.0;
                     double dirZ = 0.0;
                     if (speedSq > 1.0E-8) {
                        double inv = 1.0 / Math.sqrt(speedSq);
                        dirX = vx * inv;
                        dirZ = vz * inv;
                     }

                     CART_DATA.put(id, new FurnaceMinecartHandler.CartInfo(speedSq, dirX, dirZ));
                  } else if (speedSq > info.maxSpeedSq + 1.0E-6) {
                     info.maxSpeedSq = speedSq;
                     if (speedSq > 1.0E-8) {
                        double inv = 1.0 / Math.sqrt(speedSq);
                        info.dirX = vx * inv;
                        info.dirZ = vz * inv;
                     }
                  } else {
                     if (speedSq < info.maxSpeedSq - 1.0E-6) {
                        double targetSpeed = Math.sqrt(info.maxSpeedSq);
                        double currentSpeed = Math.sqrt(speedSq);
                        double useDirX = info.dirX;
                        double useDirZ = info.dirZ;
                        if (currentSpeed > 1.0E-8) {
                           useDirX = vx / currentSpeed;
                           useDirZ = vz / currentSpeed;
                           info.dirX = useDirX;
                           info.dirZ = useDirZ;
                        } else if (useDirX == 0.0 && useDirZ == 0.0) {
                           continue;
                        }

                        cart.field_70159_w = useDirX * targetSpeed;
                        cart.field_70179_y = useDirZ * targetSpeed;
                        cart.field_70133_I = true;
                        pushLine(world, cart, useDirX, useDirZ, targetSpeed);
                     }

                     if (!cart.func_70089_S()) {
                        CART_DATA.remove(id);
                     }
                  }
               }
            }

            if (tickCounter % 1200L == 0L) {
               Set<UUID> present = new HashSet<>();

               for(Entity entity : world.field_72996_f) {
                  if (entity instanceof EntityMinecart) {
                     present.add(entity.func_110124_au());
                  }
               }

               Iterator<UUID> it = CART_DATA.keySet().iterator();

               while(it.hasNext()) {
                  UUID uuid = it.next();
                  if (!present.contains(uuid)) {
                     it.remove();
                  }
               }
            }
         }
      }
   }

   private static void pushLine(World world, EntityMinecart source, double dirX, double dirZ, double targetSpeed) {
      Deque<EntityMinecart> queue = new ArrayDeque();
      Set<UUID> visited = new HashSet<>();
      queue.add(source);
      visited.add(source.func_110124_au());
      double maxDist = 1.5;
      double maxDistSq = maxDist * maxDist;

      while(!queue.isEmpty()) {
         EntityMinecart lead = (EntityMinecart)queue.removeFirst();
         AxisAlignedBB searchBB = lead.func_174813_aQ().func_72321_a(maxDist, 0.5, maxDist);

         for(EntityMinecart other : world.func_72872_a(EntityMinecart.class, searchBB)) {
            UUID otherId = other.func_110124_au();
            if (!visited.contains(otherId) && other.func_70089_S() && !otherId.equals(lead.func_110124_au())) {
               double dx = other.field_70165_t - lead.field_70165_t;
               double dz = other.field_70161_v - lead.field_70161_v;
               double distSq = dx * dx + dz * dz;
               if (!(distSq > maxDistSq)) {
                  double dot = dx * dirX + dz * dirZ;
                  if (!(dot <= 0.05)) {
                     visited.add(otherId);
                     queue.add(other);
                     other.field_70159_w = dirX * targetSpeed;
                     other.field_70179_y = dirZ * targetSpeed;
                     other.field_70133_I = true;
                     double newSpeedSq = targetSpeed * targetSpeed;
                     FurnaceMinecartHandler.CartInfo info = CART_DATA.get(otherId);
                     if (info == null) {
                        CART_DATA.put(otherId, new FurnaceMinecartHandler.CartInfo(newSpeedSq, dirX, dirZ));
                     } else if (newSpeedSq > info.maxSpeedSq + 1.0E-6) {
                        info.maxSpeedSq = newSpeedSq;
                        info.dirX = dirX;
                        info.dirZ = dirZ;
                     }

                     if (TrainlizeFurnanceMinecart.logger != null) {
                        TrainlizeFurnanceMinecart.logger.debug("Pushed minecart {} to speed {} via source {}", otherId, targetSpeed, source.func_110124_au());
                     }
                  }
               }
            }
         }
      }
   }

   private static final class CartInfo {
      double maxSpeedSq;
      double dirX;
      double dirZ;

      CartInfo(double maxSpeedSq, double dirX, double dirZ) {
         this.maxSpeedSq = maxSpeedSq;
         this.dirX = dirX;
         this.dirZ = dirZ;
      }
   }
}
