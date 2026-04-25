/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common;

import java.lang.reflect.Field;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.google.common.base.Throwables;
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
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.fluid.*;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTableManager;
import net.minecraft.tags.ITag;
import net.minecraft.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.minecart.ContainerMinecartEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.container.RepairContainer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TippedArrowItem;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.IDataSerializer;
import net.minecraft.network.play.server.SChangeBlockPacket;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtils;
import net.minecraft.stats.Stats;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.util.registry.WorldSettingsImport;
import net.minecraft.util.registry.WorldGenSettingsExport;
import net.minecraft.util.text.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.DimensionSettings;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import net.minecraft.world.spawner.AbstractSpawner;
import net.minecraft.tileentity.FurnaceTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeAmbience;
import net.minecraft.world.biome.BiomeGenerationSettings;
import net.minecraft.world.biome.MobSpawnInfo;
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
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.packs.ResourcePackLoader;
import net.minecraftforge.registries.DataSerializerEntry;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.IRegistryDelegate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.util.TriConsumer;

public class ForgeHooks
{
    private static final Logger LOGGER = LogManager.getLogger();
    @SuppressWarnings("unused")
    private static final Marker FORGEHOOKS = MarkerManager.getMarker("FORGEHOOKS");

    public static boolean canContinueUsing(@Nonnull ItemStack from, @Nonnull ItemStack to)
    {
        if (!from.func_190926_b() && !to.func_190926_b())
        {
            return from.func_77973_b().canContinueUsing(from, to);
        }
        return false;
    }

    public static boolean canHarvestBlock(@Nonnull BlockState state, @Nonnull PlayerEntity player, @Nonnull IBlockReader world, @Nonnull BlockPos pos)
    {
        //state = state.getActualState(world, pos);
        if (!state.func_235783_q_())
            return ForgeEventFactory.doPlayerHarvestCheck(player, state, true);

        ItemStack stack = player.func_184614_ca();
        ToolType tool = state.getHarvestTool();
        if (stack.func_190926_b() || tool == null)
            return player.func_234569_d_(state);

        int toolLevel = stack.getHarvestLevel(tool, player, state);
        if (toolLevel < 0)
            return player.func_234569_d_(state);

        return ForgeEventFactory.doPlayerHarvestCheck(player, state, toolLevel >= state.getHarvestLevel());
    }

    public static boolean isToolEffective(IWorldReader world, BlockPos pos, @Nonnull ItemStack stack)
    {
        BlockState state = world.func_180495_p(pos);
        //state = state.getActualState(world, pos);
        for (ToolType type : stack.getToolTypes())
        {
            if (state.isToolEffective(type))
                return true;
        }
        return false;
    }

    private static boolean toolInit = false;
    static void initTools()
    {
        if (toolInit)
            return;
        toolInit = true;

        Set<Block> blocks = getPrivateValue(PickaxeItem.class, null, 0);
        blocks.forEach(block -> blockToolSetter.accept(block, ToolType.PICKAXE, 0));
        blocks = getPrivateValue(ShovelItem.class, null, 0);
        blocks.forEach(block -> blockToolSetter.accept(block, ToolType.SHOVEL, 0));
        //Axes check Materials and Blocks now.
        Set<Material> materials = getPrivateValue(AxeItem.class, null, 0);
        for (Block block : ForgeRegistries.BLOCKS)
            if (materials.contains(block.func_176223_P().func_185904_a()))
                blockToolSetter.accept(block, ToolType.AXE, 0);
        blocks = getPrivateValue(AxeItem.class, null, 1);
        blocks.forEach(block -> blockToolSetter.accept(block, ToolType.AXE, 0));
        blocks = getPrivateValue(HoeItem.class, null, 0);
        blocks.forEach(block -> blockToolSetter.accept(block, ToolType.HOE, 0));

        //This is taken from PickaxeItem, if that changes update here.
        for (Block block : new Block[]{Blocks.field_150343_Z, Blocks.field_235399_ni_, Blocks.field_235397_ng_, Blocks.field_235400_nj_, Blocks.field_235398_nh_})
            blockToolSetter.accept(block, ToolType.PICKAXE, 3);
        for (Block block : new Block[]{Blocks.field_150484_ah, Blocks.field_150482_ag, Blocks.field_150412_bA, Blocks.field_150475_bE, Blocks.field_150340_R, Blocks.field_150352_o, Blocks.field_150450_ax})
            blockToolSetter.accept(block, ToolType.PICKAXE, 2);
        for (Block block : new Block[]{Blocks.field_150339_S, Blocks.field_150366_p, Blocks.field_150368_y, Blocks.field_150369_x})
            blockToolSetter.accept(block, ToolType.PICKAXE, 1);
    }

    /**
     * Called when a player uses 'pick block', calls new Entity and Block hooks.
     */
    @SuppressWarnings("resource")
    public static boolean onPickBlock(RayTraceResult target, PlayerEntity player, World world)
    {
        ItemStack result = ItemStack.field_190927_a;
        boolean isCreative = player.field_71075_bZ.field_75098_d;
        TileEntity te = null;

        if (target.func_216346_c() == RayTraceResult.Type.BLOCK)
        {
            BlockPos pos = ((BlockRayTraceResult)target).func_216350_a();
            BlockState state = world.func_180495_p(pos);

            if (state.isAir(world, pos))
                return false;

            if (isCreative && Screen.func_231172_r_() && state.hasTileEntity())
                te = world.func_175625_s(pos);

            result = state.getPickBlock(target, world, pos, player);

            if (result.func_190926_b())
                LOGGER.warn("Picking on: [{}] {} gave null item", target.func_216346_c(), state.func_177230_c().getRegistryName());
        }
        else if (target.func_216346_c() == RayTraceResult.Type.ENTITY)
        {
            Entity entity = ((EntityRayTraceResult)target).func_216348_a();
            result = entity.getPickedResult(target);

            if (result.func_190926_b())
                LOGGER.warn("Picking on: [{}] {} gave null item", target.func_216346_c(), entity.func_200600_R().getRegistryName());
        }

        if (result.func_190926_b())
            return false;

        if (te != null)
            Minecraft.func_71410_x().func_184119_a(result, te);

        if (isCreative)
        {
            player.field_71071_by.func_184434_a(result);
            Minecraft.func_71410_x().field_71442_b.func_78761_a(player.func_184586_b(Hand.MAIN_HAND), 36 + player.field_71071_by.field_70461_c);
            return true;
        }
        int slot = player.field_71071_by.func_184429_b(result);
        if (slot != -1)
        {
            if (PlayerInventory.func_184435_e(slot))
                player.field_71071_by.field_70461_c = slot;
            else
                Minecraft.func_71410_x().field_71442_b.func_187100_a(slot);
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
        return entity instanceof PlayerEntity || !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(entity, src, amount));
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
            looting = EnchantmentHelper.func_185283_h((LivingEntity)killer);
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

    /**
     * TODO 1.17 remove
     * Unused
     */
    @Deprecated
    public static double getPlayerVisibilityDistance(PlayerEntity player, double xzDistance, double maxXZDistance)
    {
        PlayerEvent.Visibility event = new PlayerEvent.Visibility(player);
        MinecraftForge.EVENT_BUS.post(event);
        double value = event.getVisibilityModifier() * xzDistance;
        return value >= maxXZDistance ? maxXZDistance : value;
    }

    public static double getEntityVisibilityMultiplier(LivingEntity entity, Entity lookingEntity, double originalMultiplier){
        LivingEvent.LivingVisibilityEvent event = new LivingEvent.LivingVisibilityEvent(entity, lookingEntity, originalMultiplier);
        MinecraftForge.EVENT_BUS.post(event);
        return Math.max(0,event.getVisibilityModifier());
    }

    /** @deprecated Use {@link #isLivingOnLadderPos(BlockState, World, BlockPos, LivingEntity)} instead in 1.16. */
    @Deprecated
    public static boolean isLivingOnLadder(@Nonnull BlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull LivingEntity entity)
    {
        return isLivingOnLadderPos(state, world, pos, entity).isPresent();
    }

    public static Optional<BlockPos> isLivingOnLadderPos(@Nonnull BlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull LivingEntity entity)
    {
        boolean isSpectator = (entity instanceof PlayerEntity && entity.func_175149_v());
        if (isSpectator) return Optional.empty();
        if (!ForgeConfig.SERVER.fullBoundingBoxLadders.get())
        {
            return state.isLadder(world, pos, entity) ? Optional.of(pos) : Optional.empty();
        }
        else
        {
            AxisAlignedBB bb = entity.func_174813_aQ();
            int mX = MathHelper.func_76128_c(bb.field_72340_a);
            int mY = MathHelper.func_76128_c(bb.field_72338_b);
            int mZ = MathHelper.func_76128_c(bb.field_72339_c);
            for (int y2 = mY; y2 < bb.field_72337_e; y2++)
            {
                for (int x2 = mX; x2 < bb.field_72336_d; x2++)
                {
                    for (int z2 = mZ; z2 < bb.field_72334_f; z2++)
                    {
                        BlockPos tmp = new BlockPos(x2, y2, z2);
                        state = world.func_180495_p(tmp);
                        if (state.isLadder(world, tmp, entity))
                        {
                            return Optional.of(tmp);
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    public static void onLivingJump(LivingEntity entity)
    {
        MinecraftForge.EVENT_BUS.post(new LivingJumpEvent(entity));
    }

    @Nullable
    public static ItemEntity onPlayerTossEvent(@Nonnull PlayerEntity player, @Nonnull ItemStack item, boolean includeName)
    {
        player.captureDrops(Lists.newArrayList());
        ItemEntity ret = player.func_146097_a(item, false, includeName);
        player.captureDrops(null);

        if (ret == null)
            return null;

        ItemTossEvent event = new ItemTossEvent(ret, player);
        if (MinecraftForge.EVENT_BUS.post(event))
            return null;

        if (!player.field_70170_p.field_72995_K)
            player.func_130014_f_().func_217376_c(event.getEntityItem());
        return event.getEntityItem();
    }

    @Nullable
    public static ITextComponent onServerChatEvent(ServerPlayNetHandler net, String raw, ITextComponent comp)
    {
        ServerChatEvent event = new ServerChatEvent(net.field_147369_b, raw, comp);
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

    public static ITextComponent newChatWithLinks(String string){ return newChatWithLinks(string, true); }
    public static ITextComponent newChatWithLinks(String string, boolean allowMissingHeader)
    {
        // Includes ipv4 and domain pattern
        // Matches an ip (xx.xxx.xx.xxx) or a domain (something.com) with or
        // without a protocol or path.
        IFormattableTextComponent ichat = null;
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
                    ichat = new StringTextComponent(part);
                else
                    ichat.func_240702_b_(part);
            }
            lastEnd = end;
            String url = string.substring(start, end);
            IFormattableTextComponent link = new StringTextComponent(url);

            try
            {
                // Add schema so client doesn't crash.
                if ((new URI(url)).getScheme() == null)
                {
                    if (!allowMissingHeader)
                    {
                        if (ichat == null)
                            ichat = new StringTextComponent(url);
                        else
                            ichat.func_240702_b_(url);
                        continue;
                    }
                    url = "http://" + url;
                }
            }
            catch (URISyntaxException e)
            {
                // Bad syntax bail out!
                if (ichat == null) ichat = new StringTextComponent(url);
                else ichat.func_240702_b_(url);
                continue;
            }

            // Set the click event and append the link.
            ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, url);
            link.func_230530_a_(link.func_150256_b().func_240715_a_(click).setUnderlined(true).func_240718_a_(Color.func_240744_a_(TextFormatting.BLUE)));
            if (ichat == null)
                ichat = new StringTextComponent("");
            ichat.func_230529_a_(link);
        }

        // Append the rest of the message.
        String end = string.substring(lastEnd);
        if (ichat == null)
            ichat = new StringTextComponent(end);
        else if (end.length() > 0)
            ichat.func_230529_a_(new StringTextComponent(string.substring(lastEnd)));
        return ichat;
    }

    public static int onBlockBreakEvent(World world, GameType gameType, ServerPlayerEntity entityPlayer, BlockPos pos)
    {
        // Logic from tryHarvestBlock for pre-canceling the event
        boolean preCancelEvent = false;
        ItemStack itemstack = entityPlayer.func_184614_ca();
        if (!itemstack.func_190926_b() && !itemstack.func_77973_b().func_195938_a(world.func_180495_p(pos), world, pos, entityPlayer))
        {
            preCancelEvent = true;
        }

        if (gameType.func_82752_c())
        {
            if (gameType == GameType.SPECTATOR)
                preCancelEvent = true;

            if (!entityPlayer.func_175142_cm())
            {
                if (itemstack.func_190926_b() || !itemstack.func_206848_a(world.func_205772_D(), new CachedBlockInfo(world, pos, false)))
                    preCancelEvent = true;
            }
        }

        // Tell client the block is gone immediately then process events
        if (world.func_175625_s(pos) == null)
        {
            entityPlayer.field_71135_a.func_147359_a(new SChangeBlockPacket(pos, world.func_204610_c(pos).func_206883_i()));
        }

        // Post the block break event
        BlockState state = world.func_180495_p(pos);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, state, entityPlayer);
        event.setCanceled(preCancelEvent);
        MinecraftForge.EVENT_BUS.post(event);

        // Handle if the event is canceled
        if (event.isCanceled())
        {
            // Let the client know the block still exists
            entityPlayer.field_71135_a.func_147359_a(new SChangeBlockPacket(world, pos));

            // Update any tile entity data for this block
            TileEntity tileentity = world.func_175625_s(pos);
            if (tileentity != null)
            {
                IPacket<?> pkt = tileentity.func_189518_D_();
                if (pkt != null)
                {
                    entityPlayer.field_71135_a.func_147359_a(pkt);
                }
            }
        }
        return event.isCanceled() ? -1 : event.getExpToDrop();
    }

    public static ActionResultType onPlaceItemIntoWorld(@Nonnull ItemUseContext context)
    {
        ItemStack itemstack = context.func_195996_i();
        World world = context.func_195991_k();

        PlayerEntity player = context.func_195999_j();
        if (player != null && !player.field_71075_bZ.field_75099_e && !itemstack.func_206847_b(world.func_205772_D(), new CachedBlockInfo(world, context.func_195995_a(), false)))
            return ActionResultType.PASS;

        // handle all placement events here
        Item item = itemstack.func_77973_b();
        int size = itemstack.func_190916_E();
        CompoundNBT nbt = null;
        if (itemstack.func_77978_p() != null)
            nbt = itemstack.func_77978_p().func_74737_b();

        if (!(itemstack.func_77973_b() instanceof BucketItem)) // if not bucket
            world.captureBlockSnapshots = true;

        ItemStack copy = itemstack.func_77946_l();
        ActionResultType ret = itemstack.func_77973_b().func_195939_a(context);
        if (itemstack.func_190926_b())
            ForgeEventFactory.onPlayerDestroyItem(player, copy, context.func_221531_n());

        world.captureBlockSnapshots = false;

        if (ret.func_226246_a_())
        {
            // save new item data
            int newSize = itemstack.func_190916_E();
            CompoundNBT newNBT = null;
            if (itemstack.func_77978_p() != null)
            {
                newNBT = itemstack.func_77978_p().func_74737_b();
            }
            @SuppressWarnings("unchecked")
            List<BlockSnapshot> blockSnapshots = (List<BlockSnapshot>)world.capturedBlockSnapshots.clone();
            world.capturedBlockSnapshots.clear();

            // make sure to set pre-placement item data for event
            itemstack.func_190920_e(size);
            itemstack.func_77982_d(nbt);

            Direction side = context.func_196000_l();

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
                ret = ActionResultType.FAIL; // cancel placement
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
                itemstack.func_190920_e(newSize);
                itemstack.func_77982_d(newNBT);

                for (BlockSnapshot snap : blockSnapshots)
                {
                    int updateFlag = snap.getFlag();
                    BlockState oldBlock = snap.getReplacedBlock();
                    BlockState newBlock = world.func_180495_p(snap.getPos());
                    newBlock.func_215705_a(world, snap.getPos(), oldBlock, false);

                    world.markAndNotifyBlock(snap.getPos(), world.func_175726_f(snap.getPos()), oldBlock, newBlock, updateFlag, 512);
                }
                if (player != null)
                    player.func_71029_a(Stats.field_75929_E.func_199076_b(item));
            }
        }
        world.capturedBlockSnapshots.clear();

        return ret;
    }

    @Deprecated // TODO: Remove 1.17 - Use player-contextual version below.
    public static boolean onAnvilChange(RepairContainer container, @Nonnull ItemStack left, @Nonnull ItemStack right, IInventory outputSlot, String name, int baseCost)
    {
        return onAnvilChange(container, left, right, outputSlot, name, baseCost, null);
    }

    public static boolean onAnvilChange(RepairContainer container, @Nonnull ItemStack left, @Nonnull ItemStack right, IInventory outputSlot, String name, int baseCost, PlayerEntity player)
    {
        AnvilUpdateEvent e = new AnvilUpdateEvent(left, right, name, baseCost, player);
        if (MinecraftForge.EVENT_BUS.post(e)) return false;
        if (e.getOutput().func_190926_b()) return true;

        outputSlot.func_70299_a(0, e.getOutput());
        container.setMaximumCost(e.getCost());
        container.field_82856_l = e.getMaterialCost();
        return false;
    }

    public static float onAnvilRepair(PlayerEntity player, @Nonnull ItemStack output, @Nonnull ItemStack left, @Nonnull ItemStack right)
    {
        AnvilRepairEvent e = new AnvilRepairEvent(player, left, right, output);
        MinecraftForge.EVENT_BUS.post(e);
        return e.getBreakChance();
    }

    private static ThreadLocal<PlayerEntity> craftingPlayer = new ThreadLocal<PlayerEntity>();
    public static void setCraftingPlayer(PlayerEntity player)
    {
        craftingPlayer.set(player);
    }
    public static PlayerEntity getCraftingPlayer()
    {
        return craftingPlayer.get();
    }
    @Nonnull
    public static ItemStack getContainerItem(@Nonnull ItemStack stack)
    {
        if (stack.func_77973_b().hasContainerItem(stack))
        {
            stack = stack.func_77973_b().getContainerItem(stack);
            if (!stack.func_190926_b() && stack.func_77984_f() && stack.func_77952_i() > stack.func_77958_k())
            {
                ForgeEventFactory.onPlayerDestroyItem(craftingPlayer.get(), stack, null);
                return ItemStack.field_190927_a;
            }
            return stack;
        }
        return ItemStack.field_190927_a;
    }

    public static boolean onPlayerAttackTarget(PlayerEntity player, Entity target)
    {
        if (MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, target))) return false;
        ItemStack stack = player.func_184614_ca();
        return stack.func_190926_b() || !stack.func_77973_b().onLeftClickEntity(stack, player, target);
    }

    public static boolean onTravelToDimension(Entity entity, RegistryKey<World> dimension)
    {
        EntityTravelToDimensionEvent event = new EntityTravelToDimensionEvent(entity, dimension);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled())
        {
            // Revert variable back to true as it would have been set to false
            if (entity instanceof ContainerMinecartEntity)
            {
               ((ContainerMinecartEntity) entity).dropContentsWhenDead(true);
            }
        }
        return !event.isCanceled();
    }

    public static ActionResultType onInteractEntityAt(PlayerEntity player, Entity entity, RayTraceResult ray, Hand hand)
    {
        Vector3d vec3d = ray.func_216347_e().func_178788_d(entity.func_213303_ch());
        return onInteractEntityAt(player, entity, vec3d, hand);
    }

    public static ActionResultType onInteractEntityAt(PlayerEntity player, Entity entity, Vector3d vec3d, Hand hand)
    {
        PlayerInteractEvent.EntityInteractSpecific evt = new PlayerInteractEvent.EntityInteractSpecific(player, hand, entity, vec3d);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    public static ActionResultType onInteractEntity(PlayerEntity player, Entity entity, Hand hand)
    {
        PlayerInteractEvent.EntityInteract evt = new PlayerInteractEvent.EntityInteract(player, hand, entity);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    public static ActionResultType onItemRightClick(PlayerEntity player, Hand hand)
    {
        PlayerInteractEvent.RightClickItem evt = new PlayerInteractEvent.RightClickItem(player, hand);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    public static PlayerInteractEvent.LeftClickBlock onLeftClickBlock(PlayerEntity player, BlockPos pos, Direction face)
    {
        PlayerInteractEvent.LeftClickBlock evt = new PlayerInteractEvent.LeftClickBlock(player, pos, face);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt;
    }

    @Deprecated //Use RayTraceResult version.  TODO: Remove 1.17
    public static PlayerInteractEvent.RightClickBlock onRightClickBlock(PlayerEntity player, Hand hand, BlockPos pos, Direction face)
    {
        PlayerInteractEvent.RightClickBlock evt = new PlayerInteractEvent.RightClickBlock(player, hand, pos, face);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt;
    }

    public static PlayerInteractEvent.RightClickBlock onRightClickBlock(PlayerEntity player, Hand hand, BlockPos pos, BlockRayTraceResult hitVec)
    {
        PlayerInteractEvent.RightClickBlock evt = new PlayerInteractEvent.RightClickBlock(player, hand, pos, hitVec);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt;
    }

    public static void onEmptyClick(PlayerEntity player, Hand hand)
    {
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickEmpty(player, hand));
    }

    public static void onEmptyLeftClick(PlayerEntity player)
    {
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickEmpty(player));
    }

    public static boolean onChangeGameMode(PlayerEntity player, GameType currentGameMode, GameType newGameMode)
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
    public static LootTable loadLootTable(Gson gson, ResourceLocation name, JsonElement data, boolean custom, LootTableManager lootTableManager)
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
                    .sound(SoundEvents.field_187630_M, SoundEvents.field_187624_K)
                    .build(fluid);
        if (fluid instanceof LavaFluid)
            return net.minecraftforge.fluids.FluidAttributes.builder(
                    new ResourceLocation("block/lava_still"),
                    new ResourceLocation("block/lava_flow"))
                    .translationKey("block.minecraft.lava")
                    .luminosity(15).density(3000).viscosity(6000).temperature(1300)
                    .sound(SoundEvents.field_187633_N, SoundEvents.field_187627_L)
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

    @FunctionalInterface
    public interface BiomeCallbackFunction
    {
        Biome apply(final Biome.Climate climate, final Biome.Category category, final Float depth, final Float scale, final BiomeAmbience effects, final BiomeGenerationSettings gen, final MobSpawnInfo spawns);
    }

    public static Biome enhanceBiome(@Nullable final ResourceLocation name, final Biome.Climate climate, final Biome.Category category, final Float depth, final Float scale, final BiomeAmbience effects, final BiomeGenerationSettings gen, final MobSpawnInfo spawns, final RecordCodecBuilder.Instance<Biome> codec, final BiomeCallbackFunction callback)
    {
        BiomeGenerationSettingsBuilder genBuilder = new BiomeGenerationSettingsBuilder(gen);
        MobSpawnInfoBuilder spawnBuilder = new MobSpawnInfoBuilder(spawns);
        BiomeLoadingEvent event = new BiomeLoadingEvent(name, climate, category, depth, scale, effects, genBuilder, spawnBuilder);
        MinecraftForge.EVENT_BUS.post(event);
        return callback.apply(event.getClimate(), event.getCategory(), event.getDepth(), event.getScale(), event.getEffects(), event.getGeneration().func_242508_a(), event.getSpawns().func_242577_b()).setRegistryName(name);
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
            this.vanilla = "minecraft".equals(this.name.func_110624_b());
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
            return JSONUtils.func_151200_h(json, "name");

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
            return ctx.validateEntryName(JSONUtils.func_151200_h(json, "entryName"));

        if (ctx.custom)
            return "custom#" + json.hashCode(); //We don't care about custom ones modders shouldn't be editing them!

        String name = null;
        if ("item".equals(type))
            name = JSONUtils.func_151200_h(json, "name");
        else if ("loot_table".equals(type))
            name = JSONUtils.func_151200_h(json, "name");
        else if ("empty".equals(type))
            name = "empty";

        return ctx.validateEntryName(name);
    }

    public static boolean onCropsGrowPre(World worldIn, BlockPos pos, BlockState state, boolean def)
    {
        BlockEvent ev = new BlockEvent.CropGrowEvent.Pre(worldIn,pos,state);
        MinecraftForge.EVENT_BUS.post(ev);
        return (ev.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW || (ev.getResult() == net.minecraftforge.eventbus.api.Event.Result.DEFAULT && def));
    }

    public static void onCropsGrowPost(World worldIn, BlockPos pos, BlockState state)
    {
        MinecraftForge.EVENT_BUS.post(new BlockEvent.CropGrowEvent.Post(worldIn, pos, state, worldIn.func_180495_p(pos)));
    }

    @Nullable
    public static CriticalHitEvent getCriticalHit(PlayerEntity player, Entity target, boolean vanillaCritical, float damageModifier)
    {
        CriticalHitEvent hitResult = new CriticalHitEvent(player, target, damageModifier, vanillaCritical);
        MinecraftForge.EVENT_BUS.post(hitResult);
        if (hitResult.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW || (vanillaCritical && hitResult.getResult() == net.minecraftforge.eventbus.api.Event.Result.DEFAULT))
        {
            return hitResult;
        }
        return null;
    }

    public static void onAdvancement(ServerPlayerEntity player, Advancement advancement)
    {
        MinecraftForge.EVENT_BUS.post(new AdvancementEvent(player, advancement));
    }

    /**
     * Hook to fire {@link ItemAttributeModifierEvent}. Modders should use {@link ItemStack#getAttributeModifiers(EquipmentSlotType)} instead.
     */
    public static Multimap<Attribute,AttributeModifier> getAttributeModifiers(ItemStack stack, EquipmentSlotType equipmentSlot, Multimap<Attribute,AttributeModifier> attributes)
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
        Item item = itemStack.func_77973_b();
        ResourceLocation registryName = item.getRegistryName();
        String modId = registryName == null ? null : registryName.func_110624_b();
        if ("minecraft".equals(modId))
        {
            if (item instanceof EnchantedBookItem)
            {
                ListNBT enchantmentsNbt = EnchantedBookItem.func_92110_g(itemStack);
                if (enchantmentsNbt.size() == 1)
                {
                    CompoundNBT nbttagcompound = enchantmentsNbt.func_150305_b(0);
                    ResourceLocation resourceLocation = ResourceLocation.func_208304_a(nbttagcompound.func_74779_i("id"));
                    if (resourceLocation != null && ForgeRegistries.ENCHANTMENTS.containsKey(resourceLocation))
                    {
                        return resourceLocation.func_110624_b();
                    }
                }
            }
            else if (item instanceof PotionItem || item instanceof TippedArrowItem)
            {
                Potion potionType = PotionUtils.func_185191_c(itemStack);
                ResourceLocation resourceLocation = ForgeRegistries.POTION_TYPES.getKey(potionType);
                if (resourceLocation != null)
                {
                    return resourceLocation.func_110624_b();
                }
            }
            else if (item instanceof SpawnEggItem)
            {
                ResourceLocation resourceLocation = ((SpawnEggItem)item).func_208076_b(null).getRegistryName();
                if (resourceLocation != null)
                {
                    return resourceLocation.func_110624_b();
                }
            }
        }
        return modId;
    }

    public static boolean onFarmlandTrample(World world, BlockPos pos, BlockState state, float fallDistance, Entity entity)
    {
        if (entity.canTrample(state, pos, fallDistance))
        {
            BlockEvent.FarmlandTrampleEvent event = new BlockEvent.FarmlandTrampleEvent(world, pos, state, fallDistance, entity);
            MinecraftForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }
        return false;
    }

    private static TriConsumer<Block, ToolType, Integer> blockToolSetter;
    //Internal use only Modders, this is specifically hidden from you, as you shouldn't be editing other people's blocks.
    public static void setBlockToolSetter(TriConsumer<Block, ToolType, Integer> setter)
    {
        blockToolSetter = setter;
    }
    @SuppressWarnings("unchecked")
    private static <T, E> T getPrivateValue(Class <? super E > classToAccess, @Nullable E instance, int fieldIndex)
    {
        try
        {
            Field f = classToAccess.getDeclaredFields()[fieldIndex];
            f.setAccessible(true);
            return (T) f.get(instance);
        }
        catch (Exception e)
        {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    public static int onNoteChange(World world, BlockPos pos, BlockState state, int old, int _new) {
        NoteBlockEvent.Change event = new NoteBlockEvent.Change(world, pos, state, old, _new);
        if (MinecraftForge.EVENT_BUS.post(event))
            return -1;
        return event.getVanillaNoteId();
    }

    public static int canEntitySpawn(MobEntity entity, IWorld world, double x, double y, double z, AbstractSpawner spawner, SpawnReason spawnReason) {
        Result res = ForgeEventFactory.canEntitySpawn(entity, world, x, y, z, null, spawnReason);
        return res == Result.DEFAULT ? 0 : res == Result.DENY ? -1 : 1;
    }

    public static <T> void deserializeTagAdditions(List<ITag.ITagEntry> list, JsonObject json, List<ITag.Proxy> allList)
    {
        //TODO 1.17 remove parsing the forge added "optional" array. Still here for compatibility with previously created tags.
        if (json.has("optional"))
        {
            for (JsonElement entry : JSONUtils.func_151214_t(json, "optional"))
            {
                String s = JSONUtils.func_151206_a(entry, "value");
                if (!s.startsWith("#"))
                    list.add(new ITag.OptionalItemEntry(new ResourceLocation(s)));
                else
                    list.add(new ITag.OptionalTagEntry(new ResourceLocation(s.substring(1))));
            }
        }

        if (json.has("remove"))
        {
            for (JsonElement entry : JSONUtils.func_151214_t(json, "remove"))
            {
                String s = JSONUtils.func_151206_a(entry, "value");
                ITag.ITagEntry dummy;
                if (!s.startsWith("#"))
                    dummy = new ITag.ItemEntry(new ResourceLocation(s));
                else
                    dummy = new ITag.TagEntry(new ResourceLocation(s.substring(1)));
                allList.removeIf(e -> e.func_232968_a_().equals(dummy));
            }
        }
    }

    private static final Map<IDataSerializer<?>, DataSerializerEntry> serializerEntries = GameData.getSerializerMap();
    //private static final ForgeRegistry<DataSerializerEntry> serializerRegistry = (ForgeRegistry<DataSerializerEntry>) ForgeRegistries.DATA_SERIALIZERS;
    // Do not reimplement this ^ it introduces a chicken-egg scenario by classloading registries during bootstrap

    @Nullable
    public static IDataSerializer<?> getSerializer(int id, IntIdentityHashBiMap<IDataSerializer<?>> vanilla)
    {
        IDataSerializer<?> serializer = vanilla.func_148745_a(id);
        if (serializer == null)
        {
            DataSerializerEntry entry = ((ForgeRegistry<DataSerializerEntry>)ForgeRegistries.DATA_SERIALIZERS).getValue(id);
            if (entry != null) serializer = entry.getSerializer();
        }
        return serializer;
    }

    public static int getSerializerId(IDataSerializer<?> serializer, IntIdentityHashBiMap<IDataSerializer<?>> vanilla)
    {
        int id = vanilla.func_148757_b(serializer);
        if (id < 0)
        {
            DataSerializerEntry entry = serializerEntries.get(serializer);
            if (entry != null) id = ((ForgeRegistry<DataSerializerEntry>)ForgeRegistries.DATA_SERIALIZERS).getID(entry);
        }
        return id;
    }

    public static boolean canEntityDestroy(World world, BlockPos pos, LivingEntity entity)
    {
        BlockState state = world.func_180495_p(pos);
        return ForgeEventFactory.getMobGriefingEvent(world, entity) && state.canEntityDestroy(world, pos, entity) && ForgeEventFactory.onEntityDestroyBlock(entity, pos, state);
    }

    private static final Map<IRegistryDelegate<Item>, Integer> VANILLA_BURNS = new HashMap<>();

    @Deprecated // use variant with IRecipeType
    public static int getBurnTime(ItemStack stack)
    {
        return getBurnTime(stack, null);
    }

    /**
     * Gets the burn time of this itemstack.
     */
    public static int getBurnTime(ItemStack stack, @Nullable IRecipeType<?> recipeType)
    {
        if (stack.func_190926_b())
        {
            return 0;
        }
        else
        {
            Item item = stack.func_77973_b();
            int ret = stack.getBurnTime(recipeType);
            return ForgeEventFactory.getItemBurnTime(stack, ret == -1 ? VANILLA_BURNS.getOrDefault(item.delegate, 0) : ret, recipeType);
        }
    }

    @SuppressWarnings("deprecation")
    public static synchronized void updateBurns()
    {
        VANILLA_BURNS.clear();
        FurnaceTileEntity.func_214001_f().entrySet().forEach(e -> VANILLA_BURNS.put(e.getKey().delegate, e.getValue()));
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
    public static void fixNullStructureReferences(IChunk chunk, Map<Structure<?>, LongSet> structureReferences)
    {
        if (structureReferences.remove(null) != null)
        {
            chunk.func_177427_f(true);
        }
        chunk.func_201606_b(structureReferences);
    }

    private static final Set<String> VANILLA_DIMS = Sets.newHashSet("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    private static final String DIMENSIONS_KEY = "dimensions";
    private static final String SEED_KEY = "seed";
    //No to static init!
    private static final LazyValue<Codec<SimpleRegistry<Dimension>>> CODEC = new LazyValue<>(() -> SimpleRegistry.func_241744_b_(Registry.field_239700_af_, Lifecycle.stable(), Dimension.field_236052_a_).xmap(Dimension::func_236062_a_, Function.identity()));

    /**
     * Restores previously "deleted" dimensions to the world.
     * The {@link LenientUnboundedMapCodec} prevents this from happening, this is to fix any world from before the fix.
     */
    public static <T> Dynamic<T> fixUpDimensionsData(Dynamic<T> data)
    {
        if(!(data.getOps() instanceof WorldSettingsImport))
            return data;

        WorldSettingsImport<T> ops = (WorldSettingsImport<T>) data.getOps();
        Dynamic<T> dymData = data.get(DIMENSIONS_KEY).orElseEmptyMap();
        Dynamic<T> withInjected = dymData.asMapOpt().map(current ->
        {
            List<Pair<String, T>> currentList = current.map(p -> p.mapFirst(dyn -> dyn.asString().result().orElse("")).mapSecond(Dynamic::getValue)).collect(Collectors.toList());
            Set<String> currentDimNames = currentList.stream().map(Pair::getFirst).collect(Collectors.toSet());

            // FixUp deleted vanilla dims.
            if (!currentDimNames.containsAll(VANILLA_DIMS))
            {
                LOGGER.warn("Detected missing vanilla dimensions from the world!");
                DynamicRegistries regs = ObfuscationReflectionHelper.getPrivateValue(WorldSettingsImport.class, ops, "field_240872_" + "d_");
                if (regs == null) // should not happen, but it could after a MC version update.
                    throw new RuntimeException("Could not access dynamic registries using reflection. " +
                            "The world was detected to have missing vanilla dimensions and the attempted fix did not work.");

                long seed = data.get(SEED_KEY).get().result().map(d -> d.asLong(0L)).orElse(0L);
                Registry<Biome> biomeReg = regs.func_243612_b(Registry.field_239720_u_);
                Registry<DimensionType> typeReg = regs.func_243612_b(Registry.field_239698_ad_);
                Registry<DimensionSettings> noiseReg = regs.func_243612_b(Registry.field_243549_ar);

                //Loads the default nether and end
                SimpleRegistry<Dimension> dimReg = DimensionType.func_242718_a(typeReg, biomeReg, noiseReg, seed);
                //Loads the default overworld
                dimReg = DimensionGeneratorSettings.func_242749_a(typeReg, dimReg, DimensionGeneratorSettings.func_242750_a(biomeReg, noiseReg, seed));

                // Encode and decode the registry. This adds any dimensions from datapacks (see SimpleRegistryCodec#decode), but only the vanilla overrides are needed.
                // This assumes that the datapacks for the vanilla dimensions have not changed since they were "deleted"
                // If they did, this will be seen in newly generated chunks.
                // Since this is to fix an older world, from before the fixes by forge, there is no way to know the state of the dimension when it was "deleted".
                dimReg = CODEC.func_179281_c().encodeStart(WorldGenSettingsExport.func_240896_a_(ops, regs), dimReg).flatMap(t -> CODEC.func_179281_c().parse(ops, t)).result().orElse(dimReg);
                for (String name : VANILLA_DIMS)
                {
                    if (currentDimNames.contains(name))
                        continue;
                    Dimension dim = dimReg.func_82594_a(new ResourceLocation(name));
                    if (dim == null)
                    {
                        LOGGER.error("The world is missing dimension: " + name + ", but the attempt to re-inject it failed.");
                        continue;
                    }
                    LOGGER.info("Fixing world: re-injected dimension: " + name);
                    Optional<T> dimT = Dimension.field_236052_a_.encodeStart(WorldGenSettingsExport.func_240896_a_(ops, regs), dim).resultOrPartial(s->{});
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

    private static final Map<EntityType<? extends LivingEntity>, AttributeModifierMap> FORGE_ATTRIBUTES = new HashMap<>();
    /**  FOR INTERNAL USE ONLY, DO NOT CALL DIRECTLY */
    @Deprecated
    public static Map<EntityType<? extends LivingEntity>, AttributeModifierMap> getAttributesView()
    {
        return Collections.unmodifiableMap(FORGE_ATTRIBUTES);
    }

    /**  FOR INTERNAL USE ONLY, DO NOT CALL DIRECTLY
     * ONLY EXISTS FOR LEGACY REASONS SHOULD BE REMOVED IN 1.17
     */
    @Deprecated /// Internal use only, Remove in 1.17
    public static AttributeModifierMap putAttributesOld(EntityType<? extends LivingEntity> type, AttributeModifierMap map)
    {
        LOGGER.warn("Called deprecated GlobalEntityTypeAttributes#put for {}, use EntityAttributeCreationEvent instead.", type.getRegistryName());
        return FORGE_ATTRIBUTES.put(type, map);
    }

    /**  FOR INTERNAL USE ONLY, DO NOT CALL DIRECTLY */
    @Deprecated
    public static void modifyAttributes()
    {
        ModLoader.get().postEvent(new EntityAttributeCreationEvent(FORGE_ATTRIBUTES));
        Map<EntityType<? extends LivingEntity>, AttributeModifierMap.MutableAttribute> finalMap = new HashMap<>();
        ModLoader.get().postEvent(new EntityAttributeModificationEvent(finalMap));

        finalMap.forEach((k, v) ->
        {
            AttributeModifierMap modifiers = GlobalEntityTypeAttributes.func_233835_a_(k);
            AttributeModifierMap.MutableAttribute newMutable = modifiers != null ? new AttributeModifierMap.MutableAttribute(modifiers) : new AttributeModifierMap.MutableAttribute();
            newMutable.combine(v);
            FORGE_ATTRIBUTES.put(k, newMutable.func_233813_a_());
        });
    }
}
