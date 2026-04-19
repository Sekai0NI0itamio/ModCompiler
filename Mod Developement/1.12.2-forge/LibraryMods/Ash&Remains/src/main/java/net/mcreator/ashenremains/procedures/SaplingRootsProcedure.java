package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class SaplingRootsProcedure {
   @SubscribeEvent
   public static void onSaplingGrow(SaplingGrowTreeEvent event) {
      execute(event, event.getLevel(), event.getPos().m_123341_(), event.getPos().m_123342_(), event.getPos().m_123343_());
   }

   public static void execute(LevelAccessor world, double x, double y, double z) {
      execute(null, world, x, y, z);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z) {
      if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50440_
         || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50493_) {
         world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), Blocks.f_152549_.m_49966_(), 3);
         if (world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z)).m_60734_() == Blocks.f_50493_ && Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y - 2.0, z), Blocks.f_152549_.m_49966_(), 3);
            if (world.m_8055_(BlockPos.m_274561_(x, y - 3.0, z)).m_60734_() == Blocks.f_50493_ && Math.random() < 0.2) {
               world.m_7731_(BlockPos.m_274561_(x, y - 3.0, z), Blocks.f_152549_.m_49966_(), 3);
            }
         }

         if ((
               world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_60734_() == Blocks.f_50440_
                  || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_60734_() == Blocks.f_50493_
            )
            && Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y - 1.0, z), Blocks.f_152549_.m_49966_(), 3);
            if ((
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0)).m_60734_() == Blocks.f_50493_
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0)).m_60734_() == Blocks.f_50440_
               )
               && Math.random() < 0.3) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0), Blocks.f_152549_.m_49966_(), 3);
            }

            if ((
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z - 1.0)).m_60734_() == Blocks.f_50493_
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z - 1.0)).m_60734_() == Blocks.f_50440_
               )
               && Math.random() < 0.3) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y - 1.0, z - 1.0), Blocks.f_152549_.m_49966_(), 3);
            }
         }

         if ((
               world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_60734_() == Blocks.f_50440_
                  || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_60734_() == Blocks.f_50493_
            )
            && Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y - 1.0, z), Blocks.f_152549_.m_49966_(), 3);
            if ((
                  world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0)).m_60734_() == Blocks.f_50493_
                     || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0)).m_60734_() == Blocks.f_50440_
               )
               && Math.random() < 0.3) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y - 1.0, z + 1.0), Blocks.f_152549_.m_49966_(), 3);
            }

            if ((
                  world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z - 1.0)).m_60734_() == Blocks.f_50493_
                     || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z - 1.0)).m_60734_() == Blocks.f_50440_
               )
               && Math.random() < 0.3) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y - 1.0, z - 1.0), Blocks.f_152549_.m_49966_(), 3);
            }
         }

         if ((
               world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_60734_() == Blocks.f_50440_
                  || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_60734_() == Blocks.f_50493_
            )
            && Math.random() < 0.5) {
            world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z - 1.0), Blocks.f_152549_.m_49966_(), 3);
         }

         if ((
               world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_60734_() == Blocks.f_50493_
                  || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_60734_() == Blocks.f_50440_
            )
            && Math.random() < 0.3) {
            world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z + 1.0), Blocks.f_152549_.m_49966_(), 3);
         }
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50746_) {
         if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), Blocks.f_50011_.m_49966_(), 3);
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y + 1.0, z), Blocks.f_50011_.m_49966_(), 3);
            }
         }

         if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), Blocks.f_50011_.m_49966_(), 3);
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y + 1.0, z), Blocks.f_50011_.m_49966_(), 3);
            }
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), Blocks.f_50011_.m_49966_(), 3);
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z - 1.0), Blocks.f_50011_.m_49966_(), 3);
            }
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), Blocks.f_50011_.m_49966_(), 3);
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z + 1.0), Blocks.f_50011_.m_49966_(), 3);
            }
         }
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50748_) {
         if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), Blocks.f_50013_.m_49966_(), 3);
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y + 1.0, z), Blocks.f_50013_.m_49966_(), 3);
            }
         }

         if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), Blocks.f_50013_.m_49966_(), 3);
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y + 1.0, z), Blocks.f_50013_.m_49966_(), 3);
            }
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), Blocks.f_50013_.m_49966_(), 3);
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z - 1.0), Blocks.f_50013_.m_49966_(), 3);
            }
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), Blocks.f_50013_.m_49966_(), 3);
            if (Math.random() < 0.05) {
               world.m_7731_(BlockPos.m_274561_(x, y + 1.0, z + 1.0), Blocks.f_50013_.m_49966_(), 3);
            }
         }
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50750_) {
         if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), Blocks.f_50015_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), Blocks.f_50015_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), Blocks.f_50015_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), Blocks.f_50015_.m_49966_(), 3);
         }
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50747_) {
         if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), Blocks.f_50012_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), Blocks.f_50012_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), Blocks.f_50012_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), Blocks.f_50012_.m_49966_(), 3);
         }
      }

      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50749_) {
         if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), Blocks.f_50014_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y + 1.0, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y, z)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), Blocks.f_50014_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z - 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), Blocks.f_50014_.m_49966_(), 3);
         }

         if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y, z + 1.0)).m_60734_() == Blocks.f_50016_
            && world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_60815_()
            && Math.random() < 0.1) {
            world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), Blocks.f_50014_.m_49966_(), 3);
         }
      }
   }
}
