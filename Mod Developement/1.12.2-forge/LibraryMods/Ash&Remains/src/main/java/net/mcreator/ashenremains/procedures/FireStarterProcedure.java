package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

public class FireStarterProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Direction direction, ItemStack itemstack) {
      if (direction != null) {
         boolean damaged = false;
         ItemStack itemstate = ItemStack.f_41583_;
         if (itemstack.m_220157_(1, RandomSource.m_216327_(), null)) {
            itemstack.m_41774_(1);
            itemstack.m_41721_(0);
         }

         if (direction == Direction.DOWN) {
            if (world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:soul_fire_base_blocks")))) {
               world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), Blocks.f_50084_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:infiniburn")))) {
               world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), Blocks.f_50083_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 2.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
               world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         } else if (direction == Direction.UP) {
            if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:soul_fire_base_blocks")))) {
               world.m_7731_(BlockPos.m_274561_(x, y + 2.0, z), Blocks.f_50084_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:infiniburn")))) {
               world.m_7731_(BlockPos.m_274561_(x, y + 2.0, z), Blocks.f_50083_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y + 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
               world.m_7731_(BlockPos.m_274561_(x, y + 2.0, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         } else if (direction == Direction.NORTH) {
            if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:soul_fire_base_blocks")))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), Blocks.f_50084_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:infiniburn")))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), Blocks.f_50083_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z - 1.0), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         } else if (direction == Direction.SOUTH) {
            if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:soul_fire_base_blocks")))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), Blocks.f_50084_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:infiniburn")))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), Blocks.f_50083_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               || world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
               world.m_7731_(BlockPos.m_274561_(x, y, z + 1.0), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         } else if (direction == Direction.WEST) {
            if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:soul_fire_base_blocks")))) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), Blocks.f_50084_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:infiniburn")))) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), Blocks.f_50083_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
               world.m_7731_(BlockPos.m_274561_(x - 1.0, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         } else if (direction == Direction.EAST) {
            if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:soul_fire_base_blocks")))) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), Blocks.f_50084_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:infiniburn")))) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), Blocks.f_50083_.m_49966_(), 3);
            } else if (world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
               || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
               || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("forge:extremely_flammable")))) {
               world.m_7731_(BlockPos.m_274561_(x + 1.0, y, z), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
            }
         }

         if (world instanceof Level _level) {
            if (!_level.m_5776_()) {
               _level.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _level.m_7785_(
                  x,
                  y,
                  z,
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F,
                  false
               );
            }
         }
      }
   }
}
