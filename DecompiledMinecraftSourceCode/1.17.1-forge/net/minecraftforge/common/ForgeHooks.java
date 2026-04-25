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

package net.minecraftforge.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.advancements.Advancement;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.tags.Tag;
import net.minecraft.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.Container;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.stats.Stats;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.RegistryReadOps;
import net.minecraft.resources.RegistryWriteOps;
import net.minecraft.world.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifierManager;
import net.minecraftforge.common.loot.LootTableIdCondition;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;
import net.minecraftforge.common.world.ForgeWorldType;
import net.minecraftforge.common.world.MobSpawnInfoBuilder;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.DifficultyChangeEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.NoteBlockEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fmllegacy.packs.ResourcePackLoader;
import net.minecraftforge.registries.DataSerializerEntry;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.IRegistryDelegate;
import net.minecraftforge.versions.mcp.MCPVersion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.LavaFluid;
import net.minecraft.world.level.material.WaterFluid;

public class ForgeHooks
{
    private static final Logger LOGGER = LogManager.getLogger();
    @SuppressWarnings("unused")
    private static final Marker FORGEHOOKS = MarkerManager.getMarker("FORGEHOOKS");

    public static boolean canContinueUsing(@Nonnull ItemStack from, @Nonnull ItemStack to)
    {
        if (!from.m_41619_() && !to.m_41619_())
        {
            return from.m_41720_().canContinueUsing(from, to);
        }
        return false;
    }

    public static boolean isCorrectToolForDrops(@Nonnull BlockState state, @Nonnull Player player)
    {
        if (!state.m_60834_())
            return ForgeEventFactory.doPlayerHarvestCheck(player, state, true);

        return player.m_36298_(state);
    }

    /**
     * Called when a player uses 'pick block', calls new Entity and Block hooks.
     */
    @SuppressWarnings("resource")
    public static boolean onPickBlock(HitResult target, Player player, Level world)
    {
        ItemStack result = ItemStack.f_41583_;
        boolean isCreative = player.m_150110_().f_35937_;
        BlockEntity te = null;

        if (target.m_6662_() == HitResult.Type.BLOCK)
        {
            BlockPos pos = ((BlockHitResult)target).m_82425_();
            BlockState state = world.m_8055_(pos);

            if (state.m_60795_())
                return false;

            if (isCreative && Screen.m_96637_() && state.m_155947_())
                te = world.m_7702_(pos);

            result = state.getPickBlock(target, world, pos, player);

            if (result.m_41619_())
                LOGGER.warn("Picking on: [{}] {} gave null item", target.m_6662_(), state.m_60734_().getRegistryName());
        }
        else if (target.m_6662_() == HitResult.Type.ENTITY)
        {
            Entity entity = ((EntityHitResult)target).m_82443_();
            result = entity.getPickedResult(target);

            if (result.m_41619_())
                LOGGER.warn("Picking on: [{}] {} gave null item", target.m_6662_(), entity.m_6095_().getRegistryName());
        }

        if (result.m_41619_())
            return false;

        if (te != null)
            Minecraft.m_91087_().m_91122_(result, te);

        if (isCreative)
        {
            player.m_150109_().m_36012_(result);
            Minecraft.m_91087_().f_91072_.m_105241_(player.m_21120_(InteractionHand.MAIN_HAND), 36 + player.m_150109_().f_35977_);
            return true;
        }
        int slot = player.m_150109_().m_36030_(result);
        if (slot != -1)
        {
            if (Inventory.m_36045_(slot))
                player.m_150109_().f_35977_ = slot;
            else
                Minecraft.m_91087_().f_91072_.m_105206_(slot);
            return true;
        }
        return false;
    }

    public static void onDifficultyChange(Difficulty difficulty, Difficulty oldDifficulty)
    {
        MinecraftForge.EVENT_BUS.post(new DifficultyChangeEvent(difficulty, oldDifficulty));
    }

    //Optifine Helper Functions u.u, these are here specifically for Optifine
    //Note: When using Optifine, these methods are invoked using reflection, which
    //incurs a major performance penalty.
    public static void onLivingSetAttackTarget(LivingEntity entity, LivingEntity target)
    {
        MinecraftForge.EVENT_BUS.post(new LivingSetAttackTargetEvent(entity, target));
    }

    public static boolean onLivingUpdate(LivingEntity entity)
    {
        return MinecraftForge.EVENT_BUS.post(new LivingUpdateEvent(entity));
    }

    public static boolean onLivingAttack(LivingEntity entity, DamageSource src, float amount)
    {
        return entity instanceof Player || !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(entity, src, amount));
    }

    public static boolean onPlayerAttack(LivingEntity entity, DamageSource src, float amount)
    {
        return !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(entity, src, amount));
    }

    public static LivingKnockBackEvent onLivingKnockBack(LivingEntity target, float strength, double ratioX, double ratioZ)
    {
        LivingKnockBackEvent event = new LivingKnockBackEvent(target, strength, ratioX, ratioZ);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    public static float onLivingHurt(LivingEntity entity, DamageSource src, float amount)
    {
        LivingHurtEvent event = new LivingHurtEvent(entity, src, amount);
        return (MinecraftForge.EVENT_BUS.post(event) ? 0 : event.getAmount());
    }

    public static float onLivingDamage(LivingEntity entity, DamageSource src, float amount)
    {
        LivingDamageEvent event = new LivingDamageEvent(entity, src, amount);
        return (MinecraftForge.EVENT_BUS.post(event) ? 0 : event.getAmount());
    }

    public static boolean onLivingDeath(LivingEntity entity, DamageSource src)
    {
        return MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(entity, src));
    }

    public static boolean onLivingDrops(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops, int lootingLevel, boolean recentlyHit)
    {
        return MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(entity, source, drops, lootingLevel, recentlyHit));
    }

    @Nullable
    public static float[] onLivingFall(LivingEntity entity, float distance, float damageMultiplier)
    {
        LivingFallEvent event = new LivingFallEvent(entity, distance, damageMultiplier);
        return (MinecraftForge.EVENT_BUS.post(event) ? null : new float[]{event.getDistance(), event.getDamageMultiplier()});
    }

    public static int getLootingLevel(Entity target, @Nullable Entity killer, DamageSource cause)
    {
        int looting = 0;
        if (killer instanceof LivingEntity)
            looting = EnchantmentHelper.m_44930_((LivingEntity)killer);
        if (target instanceof LivingEntity)
            looting = getLootingLevel((LivingEntity)target, cause, looting);
        return looting;
    }

    public static int getLootingLevel(LivingEntity target, DamageSource cause, int level)
    {
        LootingLevelEvent event = new LootingLevelEvent(target, cause, level);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getLootingLevel();
    }

    public static double getEntityVisibilityMultiplier(LivingEntity entity, Entity lookingEntity, double originalMultiplier){
        LivingEvent.LivingVisibilityEvent event = new LivingEvent.LivingVisibilityEvent(entity, lookingEntity, originalMultiplier);
        MinecraftForge.EVENT_BUS.post(event);
        return Math.max(0,event.getVisibilityModifier());
    }

    public static boolean isLivingOnLadder(@Nonnull BlockState state, @Nonnull Level world, @Nonnull BlockPos pos, @Nonnull LivingEntity entity)
    {
        boolean isSpectator = (entity instanceof Player && ((Player)entity).m_5833_());
        if (isSpectator) return false;
        if (!ForgeConfig.SERVER.fullBoundingBoxLadders.get())
        {
            return state.isLadder(world, pos, entity);
        }
        else
        {
            AABB bb = entity.m_142469_();
            int mX = Mth.m_14107_(bb.f_82288_);
            int mY = Mth.m_14107_(bb.f_82289_);
            int mZ = Mth.m_14107_(bb.f_82290_);
            for (int y2 = mY; y2 < bb.f_82292_; y2++)
            {
                for (int x2 = mX; x2 < bb.f_82291_; x2++)
                {
                    for (int z2 = mZ; z2 < bb.f_82293_; z2++)
                    {
                        BlockPos tmp = new BlockPos(x2, y2, z2);
                        state = world.m_8055_(tmp);
                        if (state.isLadder(world, tmp, entity))
                        {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    public static void onLivingJump(LivingEntity entity)
    {
        MinecraftForge.EVENT_BUS.post(new LivingJumpEvent(entity));
    }

    @Nullable
    public static ItemEntity onPlayerTossEvent(@Nonnull Player player, @Nonnull ItemStack item, boolean includeName)
    {
        player.captureDrops(Lists.newArrayList());
        ItemEntity ret = player.m_7197_(item, false, includeName);
        player.captureDrops(null);

        if (ret == null)
            return null;

        ItemTossEvent event = new ItemTossEvent(ret, player);
        if (MinecraftForge.EVENT_BUS.post(event))
            return null;

        if (!player.f_19853_.f_46443_)
            player.m_20193_().m_7967_(event.getEntityItem());
        return event.getEntityItem();
    }

    @Nullable
    public static Component onServerChatEvent(ServerGamePacketListenerImpl net, String raw, Component comp)
    {
        ServerChatEvent event = new ServerChatEvent(net.f_9743_, raw, comp);
        if (MinecraftForge.EVENT_BUS.post(event))
        {
            return null;
        }
        return event.getComponent();
    }


    static final Pattern URL_PATTERN = Pattern.compile(
            //         schema                          ipv4            OR        namespace                 port     path         ends
            //   |-----------------|        |-------------------------|  |-------------------------|    |---------| |--|   |---------------|
            "((?:[a-z0-9]{2,}:\\/\\/)?(?:(?:[0-9]{1,3}\\.){3}[0-9]{1,3}|(?:[-\\w_]{1,}\\.[a-z]{2,}?))(?::[0-9]{1,5})?.*?(?=[!\"\u00A7 \n]|$))",
            Pattern.CASE_INSENSITIVE);

    public static Component newChatWithLinks(String string){ return newChatWithLinks(string, true); }
    public static Component newChatWithLinks(String string, boolean allowMissingHeader)
    {
        // Includes ipv4 and domain pattern
        // Matches an ip (xx.xxx.xx.xxx) or a domain (something.com) with or
        // without a protocol or path.
        MutableComponent ichat = null;
        Matcher matcher = URL_PATTERN.matcher(string);
        int lastEnd = 0;

        // Find all urls
        while (matcher.find())
        {
            int start = matcher.start();
            int end = matcher.end();

            // Append the previous left overs.
            String part = string.substring(lastEnd, start);
            if (part.length() > 0)
            {
                if (ichat == null)
                    ichat = new TextComponent(part);
                else
                    ichat.m_130946_(part);
            }
            lastEnd = end;
            String url = string.substring(start, end);
            MutableComponent link = new TextComponent(url);

            try
            {
                // Add schema so client doesn't crash.
                if ((new URI(url)).getScheme() == null)
                {
                    if (!allowMissingHeader)
                    {
                        if (ichat == null)
                            ichat = new TextComponent(url);
                        else
                            ichat.m_130946_(url);
                        continue;
                    }
                    url = "http://" + url;
                }
            }
            catch (URISyntaxException e)
            {
                // Bad syntax bail out!
                if (ichat == null) ichat = new TextComponent(url);
                else ichat.m_130946_(url);
                continue;
            }

            // Set the click event and append the link.
            ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, url);
            link.m_6270_(link.m_7383_().m_131142_(click).setUnderlined(true).m_131148_(TextColor.m_131270_(ChatFormatting.BLUE)));
            if (ichat == null)
                ichat = new TextComponent("");
            ichat.m_7220_(link);
        }

        // Append the rest of the message.
        String end = string.substring(lastEnd);
        if (ichat == null)
            ichat = new TextComponent(end);
        else if (end.length() > 0)
            ichat.m_7220_(new TextComponent(string.substring(lastEnd)));
        return ichat;
    }

    public static int onBlockBreakEvent(Level world, GameType gameType, ServerPlayer entityPlayer, BlockPos pos)
    {
        // Logic from tryHarvestBlock for pre-canceling the event
        boolean preCancelEvent = false;
        ItemStack itemstack = entityPlayer.m_21205_();
        if (!itemstack.m_41619_() && !itemstack.m_41720_().m_6777_(world.m_8055_(pos), world, pos, entityPlayer))
        {
            preCancelEvent = true;
        }

        if (gameType.m_46407_())
        {
            if (gameType == GameType.SPECTATOR)
                preCancelEvent = true;

            if (!entityPlayer.m_36326_())
            {
                if (itemstack.m_41619_() || !itemstack.m_41633_(world.m_5999_(), new BlockInWorld(world, pos, false)))
                    preCancelEvent = true;
            }
        }

        // Tell client the block is gone immediately then process events
        if (world.m_7702_(pos) == null)
        {
            entityPlayer.f_8906_.m_141995_(new ClientboundBlockUpdatePacket(pos, world.m_6425_(pos).m_76188_()));
        }

        // Post the block break event
        BlockState state = world.m_8055_(pos);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, state, entityPlayer);
        event.setCanceled(preCancelEvent);
        MinecraftForge.EVENT_BUS.post(event);

        // Handle if the event is canceled
        if (event.isCanceled())
        {
            // Let the client know the block still exists
            entityPlayer.f_8906_.m_141995_(new ClientboundBlockUpdatePacket(world, pos));

            // Update any tile entity data for this block
            BlockEntity blockEntity = world.m_7702_(pos);
            if (blockEntity != null)
            {
                Packet<?> pkt = blockEntity.m_7033_();
                if (pkt != null)
                {
                    entityPlayer.f_8906_.m_141995_(pkt);
                }
            }
        }
        return event.isCanceled() ? -1 : event.getExpToDrop();
    }

    public static InteractionResult onPlaceItemIntoWorld(@Nonnull UseOnContext context)
    {
        ItemStack itemstack = context.m_43722_();
        Level world = context.m_43725_();

        Player player = context.m_43723_();
        if (player != null && !player.m_150110_().f_35938_ && !itemstack.m_41723_(world.m_5999_(), new BlockInWorld(world, context.m_8083_(), false)))
            return InteractionResult.PASS;

        // handle all placement events here
        Item item = itemstack.m_41720_();
        int size = itemstack.m_41613_();
        CompoundTag nbt = null;
        if (itemstack.m_41783_() != null)
            nbt = itemstack.m_41783_().m_6426_();

        if (!(itemstack.m_41720_() instanceof BucketItem)) // if not bucket
            world.captureBlockSnapshots = true;

        ItemStack copy = itemstack.m_41777_();
        InteractionResult ret = itemstack.m_41720_().m_6225_(context);
        if (itemstack.m_41619_())
            ForgeEventFactory.onPlayerDestroyItem(player, copy, context.m_43724_());

        world.captureBlockSnapshots = false;

        if (ret.m_19077_())
        {
            // save new item data
            int newSize = itemstack.m_41613_();
            CompoundTag newNBT = null;
            if (itemstack.m_41783_() != null)
            {
                newNBT = itemstack.m_41783_().m_6426_();
            }
            @SuppressWarnings("unchecked")
            List<BlockSnapshot> blockSnapshots = (List<BlockSnapshot>)world.capturedBlockSnapshots.clone();
            world.capturedBlockSnapshots.clear();

            // make sure to set pre-placement item data for event
            itemstack.m_41764_(size);
            itemstack.m_41751_(nbt);

            Direction side = context.m_43719_();

            boolean eventResult = false;
            if (blockSnapshots.size() > 1)
            {
                eventResult = ForgeEventFactory.onMultiBlockPlace(player, blockSnapshots, side);
            }
            else if (blockSnapshots.size() == 1)
            {
                eventResult = ForgeEventFactory.onBlockPlace(player, blockSnapshots.get(0), side);
            }

            if (eventResult)
            {
                ret = InteractionResult.FAIL; // cancel placement
                // revert back all captured blocks
                for (BlockSnapshot blocksnapshot : Lists.reverse(blockSnapshots))
                {
                    world.restoringBlockSnapshots = true;
                    blocksnapshot.restore(true, false);
                    world.restoringBlockSnapshots = false;
                }
            }
            else
            {
                // Change the stack to its new content
                itemstack.m_41764_(newSize);
                itemstack.m_41751_(newNBT);

                for (BlockSnapshot snap : blockSnapshots)
                {
                    int updateFlag = snap.getFlag();
                    BlockState oldBlock = snap.getReplacedBlock();
                    BlockState newBlock = world.m_8055_(snap.getPos());
                    newBlock.m_60696_(world, snap.getPos(), oldBlock, false);

                    world.markAndNotifyBlock(snap.getPos(), world.m_46745_(snap.getPos()), oldBlock, newBlock, updateFlag, 512);
                }
                if (player != null)
                    player.m_36246_(Stats.f_12982_.m_12902_(item));
            }
        }
        world.capturedBlockSnapshots.clear();

        return ret;
    }

    public static boolean onAnvilChange(AnvilMenu container, @Nonnull ItemStack left, @Nonnull ItemStack right, Container outputSlot, String name, int baseCost, Player player)
    {
        AnvilUpdateEvent e = new AnvilUpdateEvent(left, right, name, baseCost, player);
        if (MinecraftForge.EVENT_BUS.post(e)) return false;
        if (e.getOutput().m_41619_()) return true;

        outputSlot.m_6836_(0, e.getOutput());
        container.setMaximumCost(e.getCost());
        container.f_39000_ = e.getMaterialCost();
        return false;
    }

    public static float onAnvilRepair(Player player, @Nonnull ItemStack output, @Nonnull ItemStack left, @Nonnull ItemStack right)
    {
        AnvilRepairEvent e = new AnvilRepairEvent(player, left, right, output);
        MinecraftForge.EVENT_BUS.post(e);
        return e.getBreakChance();
    }

    private static ThreadLocal<Player> craftingPlayer = new ThreadLocal<Player>();
    public static void setCraftingPlayer(Player player)
    {
        craftingPlayer.set(player);
    }
    public static Player getCraftingPlayer()
    {
        return craftingPlayer.get();
    }
    @Nonnull
    public static ItemStack getContainerItem(@Nonnull ItemStack stack)
    {
        if (stack.m_41720_().hasContainerItem(stack))
        {
            stack = stack.m_41720_().getContainerItem(stack);
            if (!stack.m_41619_() && stack.m_41763_() && stack.m_41773_() > stack.m_41776_())
            {
                ForgeEventFactory.onPlayerDestroyItem(craftingPlayer.get(), stack, null);
                return ItemStack.f_41583_;
            }
            return stack;
        }
        return ItemStack.f_41583_;
    }

    public static boolean onPlayerAttackTarget(Player player, Entity target)
    {
        if (MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, target))) return false;
        ItemStack stack = player.m_21205_();
        return stack.m_41619_() || !stack.m_41720_().onLeftClickEntity(stack, player, target);
    }

    public static boolean onTravelToDimension(Entity entity, ResourceKey<Level> dimension)
    {
        EntityTravelToDimensionEvent event = new EntityTravelToDimensionEvent(entity, dimension);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    public static InteractionResult onInteractEntityAt(Player player, Entity entity, HitResult ray, InteractionHand hand)
    {
        Vec3 vec3d = ray.m_82450_().m_82546_(entity.m_20182_());
        return onInteractEntityAt(player, entity, vec3d, hand);
    }

    public static InteractionResult onInteractEntityAt(Player player, Entity entity, Vec3 vec3d, InteractionHand hand)
    {
        PlayerInteractEvent.EntityInteractSpecific evt = new PlayerInteractEvent.EntityInteractSpecific(player, hand, entity, vec3d);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    public static InteractionResult onInteractEntity(Player player, Entity entity, InteractionHand hand)
    {
        PlayerInteractEvent.EntityInteract evt = new PlayerInteractEvent.EntityInteract(player, hand, entity);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    public static InteractionResult onItemRightClick(Player player, InteractionHand hand)
    {
        PlayerInteractEvent.RightClickItem evt = new PlayerInteractEvent.RightClickItem(player, hand);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    public static PlayerInteractEvent.LeftClickBlock onLeftClickBlock(Player player, BlockPos pos, Direction face)
    {
        PlayerInteractEvent.LeftClickBlock evt = new PlayerInteractEvent.LeftClickBlock(player, pos, face);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt;
    }

    public static PlayerInteractEvent.RightClickBlock onRightClickBlock(Player player, InteractionHand hand, BlockPos pos, BlockHitResult hitVec)
    {
        PlayerInteractEvent.RightClickBlock evt = new PlayerInteractEvent.RightClickBlock(player, hand, pos, hitVec);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt;
    }

    public static void onEmptyClick(Player player, InteractionHand hand)
    {
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickEmpty(player, hand));
    }

    public static void onEmptyLeftClick(Player player)
    {
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickEmpty(player));
    }

    public static boolean onChangeGameMode(Player player, GameType currentGameMode, GameType newGameMode)
    {
        if (currentGameMode != newGameMode)
        {
            PlayerEvent.PlayerChangeGameModeEvent evt = new PlayerEvent.PlayerChangeGameModeEvent(player, currentGameMode, newGameMode);
            MinecraftForge.EVENT_BUS.post(evt);
            return !evt.isCanceled();
        }
        return true;
    }

    private static ThreadLocal<Deque<LootTableContext>> lootContext = new ThreadLocal<Deque<LootTableContext>>();
    private static LootTableContext getLootTableContext()
    {
        LootTableContext ctx = lootContext.get().peek();

        if (ctx == null)
            throw new JsonParseException("Invalid call stack, could not grab json context!"); // Should I throw this? Do we care about custom deserializers outside the manager?

        return ctx;
    }

    @Nullable
    public static LootTable loadLootTable(Gson gson, ResourceLocation name, JsonElement data, boolean custom, LootTables lootTableManager)
    {
        Deque<LootTableContext> que = lootContext.get();
        if (que == null)
        {
            que = Queues.newArrayDeque();
            lootContext.set(que);
        }

        LootTable ret = null;
        try
        {
            que.push(new LootTableContext(name, custom));
            ret = gson.fromJson(data, LootTable.class);
            ret.setLootTableId(name);
            que.pop();
        }
        catch (JsonParseException e)
        {
            que.pop();
            throw e;
        }

        if (!custom)
            ret = ForgeEventFactory.loadLootTable(name, ret, lootTableManager);

        if (ret != null)
           ret.freeze();

        return ret;
    }

    public static FluidAttributes createVanillaFluidAttributes(Fluid fluid)
    {
        if (fluid instanceof EmptyFluid)
            return net.minecraftforge.fluids.FluidAttributes.builder(null, null)
                    .translationKey("block.minecraft.air")
                    .color(0).density(0).temperature(0).luminosity(0).viscosity(0).build(fluid);
        if (fluid instanceof WaterFluid)
            return net.minecraftforge.fluids.FluidAttributes.Water.builder(
                    new ResourceLocation("block/water_still"),
                    new ResourceLocation("block/water_flow"))
                    .overlay(new ResourceLocation("block/water_overlay"))
                    .translationKey("block.minecraft.water")
                    .color(0xFF3F76E4)
                    .sound(SoundEvents.f_11781_, SoundEvents.f_11778_)
                    .build(fluid);
        if (fluid instanceof LavaFluid)
            return net.minecraftforge.fluids.FluidAttributes.builder(
                    new ResourceLocation("block/lava_still"),
                    new ResourceLocation("block/lava_flow"))
                    .translationKey("block.minecraft.lava")
                    .luminosity(15).density(3000).viscosity(6000).temperature(1300)
                    .sound(SoundEvents.f_11783_, SoundEvents.f_11780_)
                    .build(fluid);
        throw new RuntimeException("Mod fluids must override createAttributes.");
    }

    public static String getDefaultWorldType()
    {
        ForgeWorldType def = ForgeWorldType.getDefaultWorldType();
        if (def != null)
            return def.getRegistryName().toString();
        return "default";
    }

    public static Tag<Block> getTagFromVanillaTier(Tiers tier)
    {
        return switch(tier)
                {
                    case WOOD -> Tags.Blocks.NEEDS_WOOD_TOOL;
                    case GOLD -> Tags.Blocks.NEEDS_GOLD_TOOL;
                    case STONE -> BlockTags.f_144286_;
                    case IRON -> BlockTags.f_144285_;
                    case DIAMOND -> BlockTags.f_144284_;
                    case NETHERITE -> Tags.Blocks.NEEDS_NETHERITE_TOOL;
                };
    }

    @FunctionalInterface
    public interface BiomeCallbackFunction
    {
        Biome apply(final Biome.ClimateSettings climate, final Biome.BiomeCategory category, final Float depth, final Float scale, final BiomeSpecialEffects effects, final BiomeGenerationSettings gen, final MobSpawnSettings spawns);
    }

    public static Biome enhanceBiome(@Nullable final ResourceLocation name, final Biome.ClimateSettings climate, final Biome.BiomeCategory category, final Float depth, final Float scale, final BiomeSpecialEffects effects, final BiomeGenerationSettings gen, final MobSpawnSettings spawns, final RecordCodecBuilder.Instance<Biome> codec, final BiomeCallbackFunction callback)
    {
        BiomeGenerationSettingsBuilder genBuilder = new BiomeGenerationSettingsBuilder(gen);
        MobSpawnInfoBuilder spawnBuilder = new MobSpawnInfoBuilder(spawns);
        BiomeLoadingEvent event = new BiomeLoadingEvent(name, climate, category, depth, scale, effects, genBuilder, spawnBuilder);
        MinecraftForge.EVENT_BUS.post(event);
        return callback.apply(event.getClimate(), event.getCategory(), event.getDepth(), event.getScale(), event.getEffects(), event.getGeneration().m_47831_(), event.getSpawns().m_48381_()).setRegistryName(name);
    }

    private static class LootTableContext
    {
        public final ResourceLocation name;
        private final boolean vanilla;
        public final boolean custom;
        public int poolCount = 0;
        public int entryCount = 0;
        private HashSet<String> entryNames = Sets.newHashSet();

        private LootTableContext(ResourceLocation name, boolean custom)
        {
            this.name = name;
            this.custom = custom;
            this.vanilla = "minecraft".equals(this.name.m_135827_());
        }

        private void resetPoolCtx()
        {
            this.entryCount = 0;
            this.entryNames.clear();
        }

        public String validateEntryName(@Nullable String name)
        {
            if (name != null && !this.entryNames.contains(name))
            {
                this.entryNames.add(name);
                return name;
            }

            if (!this.vanilla)
                throw new JsonParseException("Loot Table \"" + this.name.toString() + "\" Duplicate entry name \"" + name + "\" for pool #" + (this.poolCount - 1) + " entry #" + (this.entryCount-1));

            int x = 0;
            while (this.entryNames.contains(name + "#" + x))
                x++;

            name = name + "#" + x;
            this.entryNames.add(name);

            return name;
        }
    }

    public static String readPoolName(JsonObject json)
    {
        LootTableContext ctx = ForgeHooks.getLootTableContext();
        ctx.resetPoolCtx();

        if (json.has("name"))
            return GsonHelper.m_13906_(json, "name");

        if (ctx.custom)
            return "custom#" + json.hashCode(); //We don't care about custom ones modders shouldn't be editing them!

        ctx.poolCount++;

        if (!ctx.vanilla)
            throw new JsonParseException("Loot Table \"" + ctx.name.toString() + "\" Missing `name` entry for pool #" + (ctx.poolCount - 1));

        return ctx.poolCount == 1 ? "main" : "pool" + (ctx.poolCount - 1);
    }

    public static String readLootEntryName(JsonObject json, String type)
    {
        LootTableContext ctx = ForgeHooks.getLootTableContext();
        ctx.entryCount++;

        if (json.has("entryName"))
            return ctx.validateEntryName(GsonHelper.m_13906_(json, "entryName"));

        if (ctx.custom)
            return "custom#" + json.hashCode(); //We don't care about custom ones modders shouldn't be editing them!

        String name = null;
        if ("item".equals(type))
            name = GsonHelper.m_13906_(json, "name");
        else if ("loot_table".equals(type))
            name = GsonHelper.m_13906_(json, "name");
        else if ("empty".equals(type))
            name = "empty";

        return ctx.validateEntryName(name);
    }

    public static boolean onCropsGrowPre(Level worldIn, BlockPos pos, BlockState state, boolean def)
    {
        BlockEvent ev = new BlockEvent.CropGrowEvent.Pre(worldIn,pos,state);
        MinecraftForge.EVENT_BUS.post(ev);
        return (ev.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW || (ev.getResult() == net.minecraftforge.eventbus.api.Event.Result.DEFAULT && def));
    }

    public static void onCropsGrowPost(Level worldIn, BlockPos pos, BlockState state)
    {
        MinecraftForge.EVENT_BUS.post(new BlockEvent.CropGrowEvent.Post(worldIn, pos, state, worldIn.m_8055_(pos)));
    }

    @Nullable
    public static CriticalHitEvent getCriticalHit(Player player, Entity target, boolean vanillaCritical, float damageModifier)
    {
        CriticalHitEvent hitResult = new CriticalHitEvent(player, target, damageModifier, vanillaCritical);
        MinecraftForge.EVENT_BUS.post(hitResult);
        if (hitResult.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW || (vanillaCritical && hitResult.getResult() == net.minecraftforge.eventbus.api.Event.Result.DEFAULT))
        {
            return hitResult;
        }
        return null;
    }

    public static void onAdvancement(ServerPlayer player, Advancement advancement)
    {
        MinecraftForge.EVENT_BUS.post(new AdvancementEvent(player, advancement));
    }

    /**
     * Hook to fire {@link ItemAttributeModifierEvent}. Modders should use {@link ItemStack#getAttributeModifiers(EquipmentSlot)} instead.
     */
    public static Multimap<Attribute,AttributeModifier> getAttributeModifiers(ItemStack stack, EquipmentSlot equipmentSlot, Multimap<Attribute,AttributeModifier> attributes)
    {
        ItemAttributeModifierEvent event = new ItemAttributeModifierEvent(stack, equipmentSlot, attributes);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getModifiers();
    }

    /**
     * Used as the default implementation of {@link Item#getCreatorModId}. Call that method instead.
     */
    @Nullable
    public static String getDefaultCreatorModId(@Nonnull ItemStack itemStack)
    {
        Item item = itemStack.m_41720_();
        ResourceLocation registryName = item.getRegistryName();
        String modId = registryName == null ? null : registryName.m_135827_();
        if ("minecraft".equals(modId))
        {
            if (item instanceof EnchantedBookItem)
            {
                ListTag enchantmentsNbt = EnchantedBookItem.m_41163_(itemStack);
                if (enchantmentsNbt.size() == 1)
                {
                    CompoundTag nbttagcompound = enchantmentsNbt.m_128728_(0);
                    ResourceLocation resourceLocation = ResourceLocation.m_135820_(nbttagcompound.m_128461_("id"));
                    if (resourceLocation != null && ForgeRegistries.ENCHANTMENTS.containsKey(resourceLocation))
                    {
                        return resourceLocation.m_135827_();
                    }
                }
            }
            else if (item instanceof PotionItem || item instanceof TippedArrowItem)
            {
                Potion potionType = PotionUtils.m_43579_(itemStack);
                ResourceLocation resourceLocation = ForgeRegistries.POTIONS.getKey(potionType);
                if (resourceLocation != null)
                {
                    return resourceLocation.m_135827_();
                }
            }
            else if (item instanceof SpawnEggItem)
            {
                ResourceLocation resourceLocation = ((SpawnEggItem)item).m_43228_(null).getRegistryName();
                if (resourceLocation != null)
                {
                    return resourceLocation.m_135827_();
                }
            }
        }
        return modId;
    }

    public static boolean onFarmlandTrample(Level world, BlockPos pos, BlockState state, float fallDistance, Entity entity)
    {
        if (entity.canTrample(state, pos, fallDistance))
        {
            BlockEvent.FarmlandTrampleEvent event = new BlockEvent.FarmlandTrampleEvent(world, pos, state, fallDistance, entity);
            MinecraftForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }
        return false;
    }

    public static int onNoteChange(Level world, BlockPos pos, BlockState state, int old, int _new) {
        NoteBlockEvent.Change event = new NoteBlockEvent.Change(world, pos, state, old, _new);
        if (MinecraftForge.EVENT_BUS.post(event))
            return -1;
        return event.getVanillaNoteId();
    }

    public static int canEntitySpawn(Mob entity, LevelAccessor world, double x, double y, double z, BaseSpawner spawner, MobSpawnType spawnReason) {
        Result res = ForgeEventFactory.canEntitySpawn(entity, world, x, y, z, null, spawnReason);
        return res == Result.DEFAULT ? 0 : res == Result.DENY ? -1 : 1;
    }

    public static <T> void deserializeTagAdditions(List<Tag.Entry> list, JsonObject json, List<Tag.BuilderEntry> allList)
    {
        if (json.has("remove"))
        {
            for (JsonElement entry : GsonHelper.m_13933_(json, "remove"))
            {
                String s = GsonHelper.m_13805_(entry, "value");
                Tag.Entry dummy;
                if (!s.startsWith("#"))
                    dummy = new Tag.ElementEntry(new ResourceLocation(s));
                else
                    dummy = new Tag.TagEntry(new ResourceLocation(s.substring(1)));
                allList.removeIf(e -> e.m_13347_().equals(dummy));
            }
        }
    }

    private static final Map<EntityDataSerializer<?>, DataSerializerEntry> serializerEntries = GameData.getSerializerMap();
    //private static final ForgeRegistry<DataSerializerEntry> serializerRegistry = (ForgeRegistry<DataSerializerEntry>) ForgeRegistries.DATA_SERIALIZERS;
    // Do not reimplement this ^ it introduces a chicken-egg scenario by classloading registries during bootstrap

    @Nullable
    public static EntityDataSerializer<?> getSerializer(int id, CrudeIncrementalIntIdentityHashBiMap<EntityDataSerializer<?>> vanilla)
    {
        EntityDataSerializer<?> serializer = vanilla.m_7942_(id);
        if (serializer == null)
        {
            DataSerializerEntry entry = ((ForgeRegistry<DataSerializerEntry>)ForgeRegistries.DATA_SERIALIZERS).getValue(id);
            if (entry != null) serializer = entry.getSerializer();
        }
        return serializer;
    }

    public static int getSerializerId(EntityDataSerializer<?> serializer, CrudeIncrementalIntIdentityHashBiMap<EntityDataSerializer<?>> vanilla)
    {
        int id = vanilla.m_7447_(serializer);
        if (id < 0)
        {
            DataSerializerEntry entry = serializerEntries.get(serializer);
            if (entry != null) id = ((ForgeRegistry<DataSerializerEntry>)ForgeRegistries.DATA_SERIALIZERS).getID(entry);
        }
        return id;
    }

    public static boolean canEntityDestroy(Level world, BlockPos pos, LivingEntity entity)
    {
        BlockState state = world.m_8055_(pos);
        return ForgeEventFactory.getMobGriefingEvent(world, entity) && state.canEntityDestroy(world, pos, entity) && ForgeEventFactory.onEntityDestroyBlock(entity, pos, state);
    }

    private static final Map<IRegistryDelegate<Item>, Integer> VANILLA_BURNS = new HashMap<>();

    /**
     * Gets the burn time of this itemstack.
     */
    public static int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType)
    {
        if (stack.m_41619_())
        {
            return 0;
        }
        else
        {
            Item item = stack.m_41720_();
            int ret = stack.getBurnTime(recipeType);
            return ForgeEventFactory.getItemBurnTime(stack, ret == -1 ? VANILLA_BURNS.getOrDefault(item.delegate, 0) : ret, recipeType);
        }
    }

    @SuppressWarnings("deprecation")
    public static synchronized void updateBurns()
    {
        VANILLA_BURNS.clear();
        FurnaceBlockEntity.m_58423_().entrySet().forEach(e -> VANILLA_BURNS.put(e.getKey().delegate, e.getValue()));
    }

    /**
     * All loot table drops should be passed to this function so that mod added effects
     * (e.g. smelting enchantments) can be processed.
     *
     * @param list The loot generated
     * @param context The loot context that generated that loot
     * @return The modified list
     *
     * @deprecated Use {@link #modifyLoot(ResourceLocation, List, LootContext)} instead.
     *
     * @implNote This method will use the {@linkplain LootTableIdCondition#UNKNOWN_LOOT_TABLE
     *           unknown loot table marker} when redirecting.
     */
    @Deprecated
    public static List<ItemStack> modifyLoot(List<ItemStack> list, LootContext context) {
        return modifyLoot(LootTableIdCondition.UNKNOWN_LOOT_TABLE, list, context);
    }

    /**
     * Handles the modification of loot table drops via the registered Global Loot Modifiers,
     * so that custom effects can be processed.
     *
     * <p>All loot-table generated loot should be passed to this function.</p>
     *
     * @param lootTableId The ID of the loot table currently being queried
     * @param generatedLoot The loot generated by the loot table
     * @param context The loot context that generated the loot, unmodified
     * @return The modified list of drops
     *
     * @apiNote The given context will be modified by this method to also store the ID of the
     *          loot table being queried.
     */
    public static List<ItemStack> modifyLoot(ResourceLocation lootTableId, List<ItemStack> generatedLoot, LootContext context) {
        context.setQueriedLootTableId(lootTableId); // In case the ID was set via copy constructor, this will be ignored: intended
        LootModifierManager man = ForgeInternalHandler.getLootModifierManager();
        for (IGlobalLootModifier mod : man.getAllLootMods()) {
            generatedLoot = mod.apply(generatedLoot, context);
        }
        return generatedLoot;
    }

    public static List<String> getModPacks()
    {
        List<String> modpacks = ResourcePackLoader.getPackNames();
        if(modpacks.isEmpty())
            throw new IllegalStateException("Attempted to retrieve mod packs before they were loaded in!");
        return modpacks;
    }

    public static List<String> getModPacksWithVanilla()
    {
        List<String> modpacks = getModPacks();
        modpacks.add("vanilla");
        return modpacks;
    }

    /**
     * Fixes MC-194811
     * When a structure mod is removed, this map may contain null keys. This will make the world unable to save if this persists.
     * If we remove a structure from the save data in this way, we then mark the chunk for saving
     */
    public static void fixNullStructureReferences(ChunkAccess chunk, Map<StructureFeature<?>, LongSet> structureReferences)
    {
        if (structureReferences.remove(null) != null)
        {
            chunk.m_8092_(true);
        }
        chunk.m_7946_(structureReferences);
    }

    private static final Set<String> VANILLA_DIMS = Sets.newHashSet("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    private static final String DIMENSIONS_KEY = "dimensions";
    private static final String SEED_KEY = "seed";
    //No to static init!
    private static final Supplier<Codec<MappedRegistry<LevelStem>>> CODEC = Suppliers.memoize(() -> MappedRegistry.m_122747_(Registry.f_122820_, Lifecycle.stable(), LevelStem.f_63970_).xmap(LevelStem::m_63987_, Function.identity()));

    /**
     * Restores previously "deleted" dimensions to the world.
     * The {@link LenientUnboundedMapCodec} prevents this from happening, this is to fix any world from before the fix.
     * TODO: Remove in 1.18
     */
    public static <T> Dynamic<T> fixUpDimensionsData(Dynamic<T> data)
    {
        if (!"1.17.1".equals(MCPVersion.getMCVersion()))
            throw new IllegalStateException("Calling deprecated code that was scheduled for removal after 1.17.1 in version: " + MCPVersion.getMCVersion());

        if(!(data.getOps() instanceof RegistryReadOps))
            return data;

        RegistryReadOps<T> ops = (RegistryReadOps<T>) data.getOps();
        Dynamic<T> dymData = data.get(DIMENSIONS_KEY).orElseEmptyMap();
        Dynamic<T> withInjected = dymData.asMapOpt().map(current ->
        {
            List<Pair<String, T>> currentList = current.map(p -> p.mapFirst(dyn -> dyn.asString().result().orElse("")).mapSecond(Dynamic::getValue)).collect(Collectors.toList());
            Set<String> currentDimNames = currentList.stream().map(Pair::getFirst).collect(Collectors.toSet());

            // FixUp deleted vanilla dims.
            if (!currentDimNames.containsAll(VANILLA_DIMS))
            {
                LOGGER.warn("Detected missing vanilla dimensions from the world!");
                RegistryAccess regs = ops.f_179860_;
                if (regs == null) // should not happen, but it could after a MC version update.
                    throw new RuntimeException("Could not access dynamic registries using reflection. " +
                            "The world was detected to have missing vanilla dimensions and the attempted fix did not work.");

                long seed = data.get(SEED_KEY).get().result().map(d -> d.asLong(0L)).orElse(0L);
                Registry<Biome> biomeReg = regs.m_175515_(Registry.f_122885_);
                Registry<DimensionType> typeReg = regs.m_175515_(Registry.f_122818_);
                Registry<NoiseGeneratorSettings> noiseReg = regs.m_175515_(Registry.f_122878_);

                //Loads the default nether and end
                MappedRegistry<LevelStem> dimReg = DimensionType.m_63921_(typeReg, biomeReg, noiseReg, seed);
                //Loads the default overworld
                dimReg = WorldGenSettings.m_64633_(typeReg, dimReg, WorldGenSettings.m_64637_(biomeReg, noiseReg, seed));

                // Encode and decode the registry. This adds any dimensions from datapacks (see SimpleRegistryCodec#decode), but only the vanilla overrides are needed.
                // This assumes that the datapacks for the vanilla dimensions have not changed since they were "deleted"
                // If they did, this will be seen in newly generated chunks.
                // Since this is to fix an older world, from before the fixes by forge, there is no way to know the state of the dimension when it was "deleted".
                dimReg = CODEC.get().encodeStart(RegistryWriteOps.m_135767_(ops, regs), dimReg).flatMap(t -> CODEC.get().parse(ops, t)).result().orElse(dimReg);
                for (String name : VANILLA_DIMS)
                {
                    if (currentDimNames.contains(name))
                        continue;
                    LevelStem dim = dimReg.m_7745_(new ResourceLocation(name));
                    if (dim == null)
                    {
                        LOGGER.error("The world is missing dimension: " + name + ", but the attempt to re-inject it failed.");
                        continue;
                    }
                    LOGGER.info("Fixing world: re-injected dimension: " + name);
                    Optional<T> dimT = LevelStem.f_63970_.encodeStart(RegistryWriteOps.m_135767_(ops, regs), dim).resultOrPartial(s->{});
                    if (dimT.isPresent())
                        currentList.add(Pair.of(name, dimT.get()));
                    else
                        LOGGER.error("Could not re-encode dimension " + name + ", can not be re-injected.");
                }
            }
            else
                return dymData;

            return new Dynamic<>(ops, ops.createMap(currentList.stream().map(p -> p.mapFirst(ops::createString))));
        }).result().orElse(dymData);
        return data.set(DIMENSIONS_KEY, withInjected);
    }

    private static final Map<EntityType<? extends LivingEntity>, AttributeSupplier> FORGE_ATTRIBUTES = new HashMap<>();
    /**  FOR INTERNAL USE ONLY, DO NOT CALL DIRECTLY */
    @Deprecated
    public static Map<EntityType<? extends LivingEntity>, AttributeSupplier> getAttributesView()
    {
        return Collections.unmodifiableMap(FORGE_ATTRIBUTES);
    }

    /**  FOR INTERNAL USE ONLY, DO NOT CALL DIRECTLY */
    @Deprecated
    public static void modifyAttributes()
    {
        ModLoader.get().postEvent(new EntityAttributeCreationEvent(FORGE_ATTRIBUTES));
        Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> finalMap = new HashMap<>();
        ModLoader.get().postEvent(new EntityAttributeModificationEvent(finalMap));

        finalMap.forEach((k, v) ->
        {
            AttributeSupplier supplier = DefaultAttributes.m_22297_(k);
            AttributeSupplier.Builder newBuilder = supplier != null ? new AttributeSupplier.Builder(supplier) : new AttributeSupplier.Builder();
            newBuilder.combine(v);
            FORGE_ATTRIBUTES.put(k, newBuilder.m_22265_());
        });
    }

    public static void onEntityEnterSection(Entity entity, long packedOldPos, long packedNewPos)
    {
        MinecraftForge.EVENT_BUS.post(new EntityEvent.EnteringSection(entity, packedOldPos, packedNewPos));
    }

}
