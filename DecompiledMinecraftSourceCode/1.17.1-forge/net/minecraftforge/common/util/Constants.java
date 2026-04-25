/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.common.util;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.TagTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A class containing constants for magic numbers used in the minecraft codebase.
 * Everything here should be checked each update, and have a comment relating to where to check it.
 *
 * @deprecated No longer needed. See inner classes for replacements.
 * TODO Remove in 1.18
 */
@Deprecated(since = "1.17", forRemoval = true)
public class Constants
{
    /**
     * NBT Tag type IDS, used when storing the nbt to disc, Should align with {@link TagTypes#getType(int)}
     * and {@link TagType#getPrettyName()}
     *
     * Main use is checking tag type in {@link CompoundTag#contains(String, int)}
     *
     * @deprecated Replaced by the constants in {@link net.minecraft.nbt.Tag}.
     * TODO Remove in 1.18
     */
    @Deprecated(since = "1.17", forRemoval = true)
    public static class NBT
    {
        public static final int TAG_END         = Tag.f_178193_;
        public static final int TAG_BYTE        = Tag.f_178194_;
        public static final int TAG_SHORT       = Tag.f_178195_;
        public static final int TAG_INT         = Tag.f_178196_;
        public static final int TAG_LONG        = Tag.f_178197_;
        public static final int TAG_FLOAT       = Tag.f_178198_;
        public static final int TAG_DOUBLE      = Tag.f_178199_;
        public static final int TAG_BYTE_ARRAY  = Tag.f_178200_;
        public static final int TAG_STRING      = Tag.f_178201_;
        public static final int TAG_LIST        = Tag.f_178202_;
        public static final int TAG_COMPOUND    = Tag.f_178203_;
        public static final int TAG_INT_ARRAY   = Tag.f_178204_;
        public static final int TAG_LONG_ARRAY  = Tag.f_178205_;
        public static final int TAG_ANY_NUMERIC = Tag.f_178206_;
    }

    /**
     * The world event IDS, used when calling {@link Level#globalLevelEvent(int, BlockPos, int)}. <br>
     * Can be found from {@link LevelRenderer#globalLevelEvent(int, BlockPos, int)}<br>
     * Some of the events use the {@code data} parameter. If this is the case, an explanation of what {@code data} does is also provided
     *
     * @deprecated Replaced by the constants in {@link net.minecraft.world.level.block.LevelEvent}.
     * TODO Remove in 1.18
     */
    @Deprecated(since = "1.17", forRemoval = true)
    public static class WorldEvents {
        public static final int DISPENSER_DISPENSE_SOUND        = LevelEvent.f_153607_;
        public static final int DISPENSER_FAIL_SOUND            = LevelEvent.f_153627_;
        /**
         * Like DISPENSER_DISPENSE_SOUND, but for items that are fired (arrows, eggs, snowballs)
         */
        public static final int DISPENSER_LAUNCH_SOUND          = LevelEvent.f_153628_;
        public static final int ENDEREYE_LAUNCH_SOUND           = LevelEvent.f_153629_;
        public static final int FIREWORK_SHOOT_SOUND            = LevelEvent.f_153630_;
        public static final int IRON_DOOR_OPEN_SOUND            = LevelEvent.f_153631_;
        public static final int WOODEN_DOOR_OPEN_SOUND          = LevelEvent.f_153632_;
        public static final int WOODEN_TRAPDOOR_OPEN_SOUND      = LevelEvent.f_153633_;
        public static final int FENCE_GATE_OPEN_SOUND           = LevelEvent.f_153634_;
        public static final int FIRE_EXTINGUISH_SOUND           = LevelEvent.f_153635_;
        /**
         * {@code data} is the item ID of the record you want to play
         */
        public static final int PLAY_RECORD_SOUND               = LevelEvent.f_153636_;
        public static final int IRON_DOOR_CLOSE_SOUND           = LevelEvent.f_153637_;
        public static final int WOODEN_DOOR_CLOSE_SOUND         = LevelEvent.f_153638_;
        public static final int WOODEN_TRAPDOOR_CLOSE_SOUND     = LevelEvent.f_153639_;
        public static final int FENCE_GATE_CLOSE_SOUND          = LevelEvent.f_153640_;
        public static final int GHAST_WARN_SOUND                = LevelEvent.f_153641_;
        public static final int GHAST_SHOOT_SOUND               = LevelEvent.f_153642_;
        public static final int ENDERDRAGON_SHOOT_SOUND         = LevelEvent.f_153643_;
        public static final int BLAZE_SHOOT_SOUND               = LevelEvent.f_153644_;
        public static final int ZOMBIE_ATTACK_DOOR_WOOD_SOUND   = LevelEvent.f_153645_;
        public static final int ZOMBIE_ATTACK_DOOR_IRON_SOUND   = LevelEvent.f_153646_;
        public static final int ZOMBIE_BREAK_DOOR_WOOD_SOUND    = LevelEvent.f_153647_;
        public static final int WITHER_BREAK_BLOCK_SOUND        = LevelEvent.f_153648_;
        public static final int WITHER_BREAK_BLOCK              = LevelEvent.f_153649_;
        public static final int WITHER_SHOOT_SOUND              = LevelEvent.f_153650_;
        public static final int BAT_TAKEOFF_SOUND               = LevelEvent.f_153651_;
        public static final int ZOMBIE_INFECT_SOUND             = LevelEvent.f_153581_;
        public static final int ZOMBIE_VILLAGER_CONVERTED_SOUND = LevelEvent.f_153582_;
        public static final int ANVIL_DESTROYED_SOUND           = LevelEvent.f_153584_;
        public static final int ANVIL_USE_SOUND                 = LevelEvent.f_153585_;
        public static final int ANVIL_LAND_SOUND                = LevelEvent.f_153586_;
        public static final int PORTAL_TRAVEL_SOUND             = LevelEvent.f_153587_;
        public static final int CHORUS_FLOWER_GROW_SOUND        = LevelEvent.f_153588_;
        public static final int CHORUS_FLOWER_DEATH_SOUND       = LevelEvent.f_153589_;
        public static final int BREWING_STAND_BREW_SOUND        = LevelEvent.f_153590_;
        public static final int IRON_TRAPDOOR_CLOSE_SOUND       = LevelEvent.f_153591_;
        public static final int IRON_TRAPDOOR_OPEN_SOUND        = LevelEvent.f_153592_;
        public static final int PHANTOM_BITE_SOUND              = LevelEvent.f_153594_;
        public static final int ZOMBIE_CONVERT_TO_DROWNED_SOUND = LevelEvent.f_153595_;
        public static final int HUSK_CONVERT_TO_ZOMBIE_SOUND    = LevelEvent.f_153596_;
        public static final int GRINDSTONE_USE_SOUND            = LevelEvent.f_153597_;
        public static final int ITEM_BOOK_TURN_PAGE_SOUND       = LevelEvent.f_153598_;
        /**
         * Spawns the composter particles and plays the sound event sound event<br>
         * {@code data} is bigger than 0 when the composter can still be filled up, and is smaller or equal to 0 when the composter is full. (This only effects the sound event)
         */
        public static final int COMPOSTER_FILLED_UP             = LevelEvent.f_153604_;
        public static final int LAVA_EXTINGUISH                 = LevelEvent.f_153605_;
        public static final int REDSTONE_TORCH_BURNOUT          = LevelEvent.f_153606_;
        public static final int END_PORTAL_FRAME_FILL           = LevelEvent.f_153608_;
        /**
         * {@code data} is the {@link Direction#get3DDataValue()} of the direction the smoke is to come out of.
         */
        public static final int DISPENSER_SMOKE                 = LevelEvent.f_153611_;

        /**
         * {@code data} is the {@link Block#getId(BlockState)}  state id} of the block broken
         */
        public static final int BREAK_BLOCK_EFFECTS             = LevelEvent.f_153612_;
        /**
         * {@code data} is the rgb color int that should be used for the potion particles<br>
         * This is the same as {@link Constants.WorldEvents#POTION_IMPACT} but uses the particle type {@link ParticleTypes#EFFECT}
         */
        public static final int POTION_IMPACT_INSTANT           = LevelEvent.f_153613_;
        public static final int ENDER_EYE_SHATTER               = LevelEvent.f_153614_;
        public static final int MOB_SPAWNER_PARTICLES           = LevelEvent.f_153615_;
        /**
         * {@code data} is the amount of particles to spawn. If {@code data} is 0 then there will be 15 particles spawned
         */
        public static final int BONEMEAL_PARTICLES              = LevelEvent.f_153616_;
        public static final int DRAGON_FIREBALL_HIT             = LevelEvent.f_153617_;
        /**
         * {@code data} is the rgb color int that should be used for the potion particles<br>
         * This is the same as {@link Constants.WorldEvents#POTION_IMPACT_INSTANT} but uses the particle type {@link ParticleTypes#INSTANT_EFFECT}
         */
        public static final int POTION_IMPACT                   = LevelEvent.f_153618_;
        public static final int SPAWN_EXPLOSION_PARTICLE        = LevelEvent.f_153619_;
        public static final int GATEWAY_SPAWN_EFFECTS           = LevelEvent.f_153621_;
        public static final int ENDERMAN_GROWL_SOUND            = LevelEvent.f_153622_;
    }


    /**
     * The flags used when calling
     * {@link LevelWriter#setBlock(BlockPos, BlockState, int)}<br>
     * Can be found from {@link Level#setBlock(BlockPos, BlockState, int)} ,
     * {@link Level#markAndNotifyBlock(BlockPos, LevelChunk, BlockState, BlockState, int, int)}, and
     * {@link LevelRenderer#blockChanged(BlockGetter, BlockPos, BlockState, BlockState, int)}<br>
     * Flags can be combined with bitwise OR
     *
     * @deprecated Replaced by the constants in {@link net.minecraft.world.level.block.Block}.
     * TODO Remove in 1.18
     */
    @Deprecated(since = "1.17", forRemoval = true)
    public static class BlockFlags {
        /**
         * Calls {@link Block#neighborChanged(BlockState, Level, BlockPos, Block, BlockPos, boolean)
         * neighborChanged} on surrounding blocks (with isMoving as false). Also updates comparator output state.
         */
        public static final int NOTIFY_NEIGHBORS     = Block.f_152393_;
        /**
         * Calls {@link Level#sendBlockUpdated(BlockPos, BlockState, BlockState, int)}.<br>
         * Server-side, this updates all the path-finding navigators.
         */
        public static final int BLOCK_UPDATE         = Block.f_152394_;
        /**
         * Stops the blocks from being marked for a render update
         */
        public static final int NO_RERENDER          = Block.f_152395_;
        /**
         * Makes the block be re-rendered immediately, on the main thread.
         * If NO_RERENDER is set, then this will be ignored
         */
        public static final int RERENDER_MAIN_THREAD = Block.f_152396_;
        /**
         * Causes neighbor updates to be sent to all surrounding blocks (including
         * diagonals). This in turn will call
         * {@link Block#updateIndirectNeighbourShapes(BlockState, LevelAccessor, BlockPos, int, int)}
         * on both old and new states, and
         * {@link Block#updateOrDestroy(BlockState, BlockState, LevelAccessor, BlockPos, int, int)} on the new state.
         */
        public static final int UPDATE_NEIGHBORS     = Block.f_152397_;

        /**
         * Prevents neighbor changes from spawning item drops, used by
         * {@link Block#updateOrDestroy(BlockState, BlockState, LevelAccessor, BlockPos, int)}.
         */
        public static final int NO_NEIGHBOR_DROPS    = Block.f_152398_;

        /**
         * Tell the block being changed that it was moved, rather than removed/replaced,
         * the boolean value is eventually passed to
         * {@link Block#onRemove(BlockState, Level, BlockPos, BlockState, boolean)}
         * as the last parameter.
         */
        public static final int IS_MOVING            = Block.f_152399_;

        public static final int DEFAULT = Block.f_152402_;
        public static final int DEFAULT_AND_RERENDER = Block.f_152388_;
    }
}
