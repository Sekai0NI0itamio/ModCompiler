/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;

import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents.LiteralContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.fixes.StructuresBecomeConfiguredFix;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.Container;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.stats.Stats;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.crafting.conditions.ConditionCodec;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.BrainBuilder;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.common.util.MavenVersionStringHelper;
import net.minecraftforge.common.util.MutableHashedLinkedMap;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.GatherComponentsEvent;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.ModMismatchEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.RegisterStructureConversionsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.EnderManAngerEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingDrownEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingMakeBrainEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingGetProjectileEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.NoteBlockEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.network.ConnectionType;
import net.minecraftforge.network.ForgePayload;
import net.minecraftforge.network.NetworkContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkInitialization;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.packets.SpawnEntity;
import net.minecraftforge.resource.ResourcePackLoader;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.server.permission.PermissionAPI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.material.Fluid;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup.RegistryLookup;

/**  FOR INTERNAL USE ONLY, DO NOT CALL DIRECTLY */
@ApiStatus.Internal
public final class ForgeHooks {
    private static final Logger LOGGER = LogManager.getLogger();
    @SuppressWarnings("unused")
    private static final Marker FORGEHOOKS = MarkerManager.getMarker("FORGEHOOKS");
    private static final Marker WORLDPERSISTENCE = MarkerManager.getMarker("WP");

    private ForgeHooks() {}

    public static boolean canContinueUsing(@NotNull ItemStack from, @NotNull ItemStack to) {
        if (!from.m_41619_() && !to.m_41619_()) {
            return from.m_41720_().canContinueUsing(from, to);
        }
        return false;
    }

    public static boolean isCorrectToolForDrops(@NotNull BlockState state, @NotNull Player player) {
        if (!state.m_60834_())
            return ForgeEventFactory.doPlayerHarvestCheck(player, state, true);

        return player.m_36298_(state);
    }

    public static Brain<?> onLivingMakeBrain(LivingEntity entity, Brain<?> originalBrain, Dynamic<?> dynamic) {
        BrainBuilder<?> brainBuilder = originalBrain.createBuilder();
        LivingMakeBrainEvent event = new LivingMakeBrainEvent(entity, brainBuilder);
        MinecraftForge.EVENT_BUS.post(event);
        return brainBuilder.makeBrain(dynamic);
    }

    public static boolean onLivingAttack(LivingEntity entity, DamageSource src, float amount) {
        return entity instanceof Player || !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(entity, src, amount));
    }

    public static boolean onPlayerAttack(LivingEntity entity, DamageSource src, float amount) {
        return !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(entity, src, amount));
    }

    public static boolean onLivingUseTotem(LivingEntity entity, DamageSource damageSource, ItemStack totem, InteractionHand hand) {
        return !MinecraftForge.EVENT_BUS.post(new LivingUseTotemEvent(entity, damageSource, totem, hand));
    }

    public static float onLivingHurt(LivingEntity entity, DamageSource src, float amount) {
        LivingHurtEvent event = new LivingHurtEvent(entity, src, amount);
        return (MinecraftForge.EVENT_BUS.post(event) ? 0 : event.getAmount());
    }

    public static float onLivingDamage(LivingEntity entity, DamageSource src, float amount) {
        LivingDamageEvent event = new LivingDamageEvent(entity, src, amount);
        return (MinecraftForge.EVENT_BUS.post(event) ? 0 : event.getAmount());
    }

    public static InteractionResult onInteractEntityAt(Entity entity, Player player, Vec3 vec3d, InteractionHand hand) {
        var ret = ForgeEventFactory.onEntityInteractSpecific(player, entity, hand, vec3d);
        if (ret.isCanceled())
            return ret.getCancellationResult();
        return entity.m_7111_(player, vec3d, hand);
    }

    public static int getLootingLevel(Entity target, @Nullable Entity killer, @Nullable DamageSource cause) {
        int looting = 0;
        if (killer instanceof LivingEntity living)
            looting = EnchantmentHelper.m_44930_(living);
        if (target instanceof LivingEntity living)
            looting = getLootingLevel(living, cause, looting);
        return looting;
    }

    public static int getLootingLevel(LivingEntity target, @Nullable DamageSource cause, int level) {
        LootingLevelEvent event = new LootingLevelEvent(target, cause, level);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getLootingLevel();
    }

    public static double getEntityVisibilityMultiplier(LivingEntity entity, Entity lookingEntity, double originalMultiplier){
        LivingEvent.LivingVisibilityEvent event = new LivingEvent.LivingVisibilityEvent(entity, lookingEntity, originalMultiplier);
        MinecraftForge.EVENT_BUS.post(event);
        return Math.max(0,event.getVisibilityModifier());
    }

    public static Optional<BlockPos> isLivingOnLadder(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull LivingEntity entity) {
        boolean isSpectator = (entity instanceof Player && entity.m_5833_());
        if (isSpectator) return Optional.empty();
        if (!ForgeConfig.SERVER.fullBoundingBoxLadders.get())
            return state.isLadder(level, pos, entity) ? Optional.of(pos) : Optional.empty();
        else {
            AABB bb = entity.m_20191_();
            int mX = Mth.m_14107_(bb.f_82288_);
            int mY = Mth.m_14107_(bb.f_82289_);
            int mZ = Mth.m_14107_(bb.f_82290_);
            for (int y2 = mY; y2 < bb.f_82292_; y2++) {
                for (int x2 = mX; x2 < bb.f_82291_; x2++) {
                    for (int z2 = mZ; z2 < bb.f_82293_; z2++) {
                        BlockPos tmp = new BlockPos(x2, y2, z2);
                        state = level.m_8055_(tmp);
                        if (state.isLadder(level, tmp, entity))
                            return Optional.of(tmp);
                    }
                }
            }
            return Optional.empty();
        }
    }

    public static void onLivingJump(LivingEntity entity) {
        MinecraftForge.EVENT_BUS.post(new LivingJumpEvent(entity));
    }

    @SuppressWarnings("resource")
    @Nullable
    public static ItemEntity onPlayerTossEvent(@NotNull Player player, @NotNull ItemStack item, boolean includeName) {
        player.captureDrops(Lists.newArrayList());
        ItemEntity ret = player.m_7197_(item, false, includeName);
        player.captureDrops(null);

        if (ret == null)
            return null;

        ItemTossEvent event = new ItemTossEvent(ret, player);
        if (MinecraftForge.EVENT_BUS.post(event))
            return null;

        if (!player.m_9236_().f_46443_)
            player.m_20193_().m_7967_(event.getEntity());
        return event.getEntity();
    }

    @Nullable
    public static Component onServerChatSubmittedEvent(ServerPlayer player, Component message) {
        var plain = message.m_214077_() instanceof LiteralContents literalContents ? literalContents.m_305315_() : "";
        ServerChatEvent event = new ServerChatEvent(player, plain, message);
        return MinecraftForge.EVENT_BUS.post(event) ? null : event.getMessage();
    }

    static final Pattern URL_PATTERN = Pattern.compile(
            //         schema                          ipv4            OR        namespace                 port     path         ends
            //   |-----------------|        |-------------------------|  |-------------------------|    |---------| |--|   |---------------|
            "((?:[a-z0-9]{2,}:\\/\\/)?(?:(?:[0-9]{1,3}\\.){3}[0-9]{1,3}|(?:[-\\w_]{1,}\\.[a-z]{2,}?))(?::[0-9]{1,5})?.*?(?=[!\"\u00A7 \n]|$))",
            Pattern.CASE_INSENSITIVE);

    public static Component newChatWithLinks(String string){ return newChatWithLinks(string, true); }
    public static Component newChatWithLinks(String string, boolean allowMissingHeader) {
        // Includes ipv4 and domain pattern
        // Matches an ip (xx.xxx.xx.xxx) or a domain (something.com) with or
        // without a protocol or path.
        MutableComponent ichat = null;
        Matcher matcher = URL_PATTERN.matcher(string);
        int lastEnd = 0;

        // Find all urls
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // Append the previous left overs.
            String part = string.substring(lastEnd, start);
            if (!part.isEmpty()) {
                if (ichat == null)
                    ichat = Component.m_237113_(part);
                else
                    ichat.m_130946_(part);
            }
            lastEnd = end;
            String url = string.substring(start, end);
            MutableComponent link = Component.m_237113_(url);

            try {
                // Add schema so client doesn't crash.
                if ((new URI(url)).getScheme() == null) {
                    if (!allowMissingHeader) {
                        if (ichat == null)
                            ichat = Component.m_237113_(url);
                        else
                            ichat.m_130946_(url);
                        continue;
                    }
                    url = "http://" + url;
                }
            } catch (URISyntaxException e) {
                // Bad syntax bail out!
                if (ichat == null) ichat = Component.m_237113_(url);
                else ichat.m_130946_(url);
                continue;
            }

            // Set the click event and append the link.
            ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, url);
            link.m_6270_(link.m_7383_().m_131142_(click).m_131162_(true).m_131148_(TextColor.m_131270_(ChatFormatting.BLUE)));
            if (ichat == null)
                ichat = Component.m_237113_("");
            ichat.m_7220_(link);
        }

        // Append the rest of the message.
        String end = string.substring(lastEnd);
        if (ichat == null)
            ichat = Component.m_237113_(end);
        else if (!end.isEmpty())
            ichat.m_7220_(Component.m_237113_(string.substring(lastEnd)));
        return ichat;
    }

    public static void dropXpForBlock(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack) {
        int fortuneLevel = stack.getEnchantmentLevel(Enchantments.f_316753_);
        int silkTouchLevel = stack.getEnchantmentLevel(Enchantments.f_44985_);
        int exp = state.getExpDrop(level, level.f_46441_, pos, fortuneLevel, silkTouchLevel);
        if (exp > 0)
            state.m_60734_().m_49805_(level, pos, exp);
    }

    public static int onBlockBreakEvent(Level level, GameType gameType, ServerPlayer entityPlayer, BlockPos pos) {
        // Logic from tryHarvestBlock for pre-canceling the event
        boolean preCancelEvent = false;
        ItemStack itemstack = entityPlayer.m_21205_();
        if (!itemstack.m_41619_() && !itemstack.m_41720_().m_6777_(level.m_8055_(pos), level, pos, entityPlayer)) {
            preCancelEvent = true;
        }

        if (entityPlayer.m_36187_(level, pos, gameType)) {
            preCancelEvent = true;
        }

        // Post the block break event
        BlockState state = level.m_8055_(pos);

        // Tell client the block is gone immediately then process events
        if (level.m_7702_(pos) == null) {
            level.m_7260_(pos, state, state, 3);
        }

        var event = new BlockEvent.BreakEvent(level, pos, state, entityPlayer);
        event.setCanceled(preCancelEvent);
        MinecraftForge.EVENT_BUS.post(event);

        // Handle if the event is canceled
        if (event.isCanceled()) {
            // Let the client know the block still exists
            entityPlayer.f_8906_.m_141995_(new ClientboundBlockUpdatePacket(level, pos));

            // Update any tile entity data for this block
            BlockEntity blockEntity = level.m_7702_(pos);
            if (blockEntity != null) {
                Packet<?> pkt = blockEntity.m_58483_();
                if (pkt != null)
                    entityPlayer.f_8906_.m_141995_(pkt);
            }
        }
        return event.isCanceled() ? -1 : event.getExpToDrop();
    }

    public static InteractionResult onPlaceItemIntoWorld(@NotNull UseOnContext context) {
        ItemStack itemstack = context.m_43722_();
        Level level = context.m_43725_();

        Player player = context.m_43723_();
        if (player != null && !player.m_150110_().f_35938_ && !itemstack.m_321400_(new BlockInWorld(level, context.m_8083_(), false))) {
            return InteractionResult.PASS;
        }


        if (!(itemstack.m_41720_() instanceof BucketItem)) // if not bucket
            level.captureBlockSnapshots = true;

        ItemStack preUse = itemstack.m_41777_();
        InteractionResult ret = itemstack.m_41720_().m_6225_(context);
        if (itemstack.m_41619_())
            ForgeEventFactory.onPlayerDestroyItem(player, preUse, context.m_43724_());

        level.captureBlockSnapshots = false;

        if (ret.m_19077_()) {
            var postUse = player.m_21120_(context.m_43724_());

            var blockSnapshots = new ArrayList<>(level.capturedBlockSnapshots);
            level.capturedBlockSnapshots.clear();

            // make sure to set pre-placement item data for event
            player.m_21008_(context.m_43724_(), preUse);

            Direction side = context.m_43719_();

            boolean eventResult = false;
            if (blockSnapshots.size() > 1)
                eventResult = ForgeEventFactory.onMultiBlockPlace(player, blockSnapshots, side);
            else if (blockSnapshots.size() == 1)
                eventResult = ForgeEventFactory.onBlockPlace(player, blockSnapshots.get(0), side);

            if (eventResult) {
                ret = InteractionResult.FAIL; // cancel placement
                // revert back all captured blocks
                for (BlockSnapshot blocksnapshot : Lists.reverse(blockSnapshots)) {
                    level.restoringBlockSnapshots = true;
                    blocksnapshot.restore(true, false);
                    level.restoringBlockSnapshots = false;
                }
            } else {
                // Change the stack to its new content
                player.m_21008_(context.m_43724_(), postUse);

                for (BlockSnapshot snap : blockSnapshots) {
                    int updateFlag = snap.getFlag();
                    BlockState oldBlock = snap.getReplacedBlock();
                    BlockState newBlock = level.m_8055_(snap.getPos());
                    newBlock.m_60696_(level, snap.getPos(), oldBlock, false);

                    level.markAndNotifyBlock(snap.getPos(), level.m_46745_(snap.getPos()), oldBlock, newBlock, updateFlag, 512);
                }
                if (player != null)
                    player.m_36246_(Stats.f_12982_.m_12902_(preUse.m_41720_()));
            }
        }
        level.capturedBlockSnapshots.clear();

        return ret;
    }

    public static boolean onAnvilChange(AnvilMenu container, @NotNull ItemStack left, @NotNull ItemStack right, Container outputSlot, String name, long baseCost, Player player) {
        AnvilUpdateEvent e = new AnvilUpdateEvent(left, right, name, baseCost, player);
        if (MinecraftForge.EVENT_BUS.post(e)) return false;
        if (e.getOutput().m_41619_()) return true;

        outputSlot.m_6836_(0, e.getOutput());
        container.setMaximumCost((int)e.getCost());
        container.f_39000_ = e.getMaterialCost();
        return false;
    }

    public static boolean onGrindstoneTake(Container inputSlots, ContainerLevelAccess access, Function<Level, Integer> xpFunction) {
        access.m_39292_((l,p) -> {
            int xp = xpFunction.apply(l);
            GrindstoneEvent.OnTakeItem e = new GrindstoneEvent.OnTakeItem(inputSlots.m_8020_(0), inputSlots.m_8020_(1), xp);
            if (MinecraftForge.EVENT_BUS.post(e))
                return;

            if (l instanceof ServerLevel server)
                ExperienceOrb.m_147082_(server, Vec3.m_82512_(p), e.getXp());

            l.m_46796_(1042, p, 0);
            inputSlots.m_6836_(0, e.getNewTopItem());
            inputSlots.m_6836_(1, e.getNewBottomItem());
            inputSlots.m_6596_();
        });
        return true;
    }

    private static final ThreadLocal<Player> CRAFTING_PLAYER = new ThreadLocal<>();
    public static void setCraftingPlayer(Player player) {
        CRAFTING_PLAYER.set(player);
    }

    public static Player getCraftingPlayer() {
        return CRAFTING_PLAYER.get();
    }

    @NotNull
    public static ItemStack getCraftingRemainingItem(@NotNull ItemStack stack) {
        if (stack.m_41720_().hasCraftingRemainingItem(stack)) {
            stack = stack.m_41720_().getCraftingRemainingItem(stack);
            if (!stack.m_41619_() && stack.m_41763_() && stack.m_41773_() > stack.m_41776_()) {
                ForgeEventFactory.onPlayerDestroyItem(CRAFTING_PLAYER.get(), stack, (EquipmentSlot)null);
                return ItemStack.f_41583_;
            }
            return stack;
        }
        return ItemStack.f_41583_;
    }

    public static boolean onPlayerAttackTarget(Player player, Entity target) {
        if (MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, target))) return false;
        ItemStack stack = player.m_21205_();
        return stack.m_41619_() || !stack.m_41720_().onLeftClickEntity(stack, player, target);
    }

    public static InteractionResult onItemRightClick(Player player, InteractionHand hand) {
        var evt = ForgeEventFactory.onRightClickItem(player, hand);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    public static GameType onChangeGameType(Player player, GameType currentGameType, GameType newGameType) {
        if (currentGameType == newGameType)
            return currentGameType;

        var evt = new PlayerEvent.PlayerChangeGameModeEvent(player, currentGameType, newGameType);
        if (MinecraftForge.EVENT_BUS.post(evt))
            return currentGameType;

        return evt.getNewGameMode();
    }

    public static <E extends LootPool> Codec<List<E>> createLootTablePoolCodec(Codec<E> vanilla) {
        var list = vanilla.listOf();
        Decoder<List<E>> decoder = new Decoder<>() {
            @Override
            public <T> DataResult<Pair<List<E>, T>> decode(DynamicOps<T> ops, T input) {
                return list.decode(ops, input).map(p -> {
                    var decoded = p.getFirst();
                    for (int x = 0; x < decoded.size(); x++)
                        decoded.get(x);

                    return p;
                });
            }
        };
        return Codec.of(list, decoder);
    }

    /**
     * Returns a vanilla fluid type for the given fluid.
     *
     * @param fluid the fluid looking for its type
     * @return the type of the fluid if vanilla
     * @throws RuntimeException if the fluid is not a vanilla one
     */
    public static FluidType getVanillaFluidType(Fluid fluid) {
        if (fluid == Fluids.f_76191_)
            return ForgeMod.EMPTY_TYPE.get();
        if (fluid == Fluids.f_76193_ || fluid == Fluids.f_76192_)
            return ForgeMod.WATER_TYPE.get();
        if (fluid == Fluids.f_76195_ || fluid == Fluids.f_76194_)
            return ForgeMod.LAVA_TYPE.get();
        if (ForgeMod.MILK.filter(milk -> milk == fluid).isPresent() || ForgeMod.FLOWING_MILK.filter(milk -> milk == fluid).isPresent())
            return ForgeMod.MILK_TYPE.get();
        throw new RuntimeException("Mod fluids must override getFluidType.");
    }

    /*
    public static TagKey<Block> getTagFromVanillaTier(Tiers tier) {
        return switch(tier) {
            case WOOD -> Tags.Blocks.NEEDS_WOOD_TOOL;
            case GOLD -> Tags.Blocks.NEEDS_GOLD_TOOL;
            case STONE -> BlockTags.NEEDS_STONE_TOOL;
            case IRON -> BlockTags.NEEDS_IRON_TOOL;
            case DIAMOND -> BlockTags.NEEDS_DIAMOND_TOOL;
            case NETHERITE -> Tags.Blocks.NEEDS_NETHERITE_TOOL;
        };
    }
    */

    @FunctionalInterface
    public interface BiomeCallbackFunction {
        Biome apply(final Biome.ClimateSettings climate, final BiomeSpecialEffects effects, final BiomeGenerationSettings gen, final MobSpawnSettings spawns);
    }

    public static boolean onCropsGrowPre(Level level, BlockPos pos, BlockState state, boolean def) {
        var result = MinecraftForge.EVENT_BUS.fire(new BlockEvent.CropGrowEvent.Pre(level,pos,state)).getResult();
        return (result.isAllowed() || (def && result.isDefault()));
    }

    public static void onCropsGrowPost(Level level, BlockPos pos, BlockState state) {
        MinecraftForge.EVENT_BUS.post(new BlockEvent.CropGrowEvent.Post(level, pos, state, level.m_8055_(pos)));
    }

    @Nullable
    public static CriticalHitEvent getCriticalHit(Player player, Entity target, boolean vanillaCritical, float damageModifier) {
        CriticalHitEvent hitResult = new CriticalHitEvent(player, target, damageModifier, vanillaCritical);
        MinecraftForge.EVENT_BUS.post(hitResult);
        if (hitResult.getResult() == Event.Result.ALLOW || (vanillaCritical && hitResult.getResult() == Event.Result.DEFAULT))
            return hitResult;
        return null;
    }

    /**
     * Hook to fire {@link LivingGetProjectileEvent}. Returns the ammo to be used.
     */
    public static ItemStack getProjectile(LivingEntity entity, ItemStack projectileWeaponItem, ItemStack projectile) {
        LivingGetProjectileEvent event = new LivingGetProjectileEvent(entity, projectileWeaponItem, projectile);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getProjectileItemStack();
    }

    /**
     * Used as the default implementation of {@link Item#getCreatorModId}. Call that method instead.
     */
    @Nullable
    public static String getDefaultCreatorModId(@NotNull ItemStack itemStack) {
        Item item = itemStack.m_41720_();
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(item);
        String modId = registryName == null ? null : registryName.m_135827_();
        if ("minecraft".equals(modId)) {
            if (item instanceof EnchantedBookItem) {
                var enchants = EnchantmentHelper.m_324152_(itemStack);
                if (enchants.m_322852_() == 1) {
                    var enchant = enchants.m_324420_().iterator().next();
                    var name = enchant.m_203543_();
                    if (name.isPresent())
                        return name.get().m_135782_().m_135827_();
                }
            } else if (itemStack.m_319951_(DataComponents.f_314188_)) {
                var potion = itemStack.m_323252_(DataComponents.f_314188_).f_317059_().orElse(null);
                if (potion != null && potion.m_203543_().isPresent())
                    return potion.m_203543_().get().m_135782_().m_135827_();
            } else if (item instanceof SpawnEggItem egg) {
                var resourceLocation = ForgeRegistries.ENTITY_TYPES.getKey(egg.m_43228_(itemStack));
                if (resourceLocation != null)
                    return resourceLocation.m_135827_();
            }
        }
        return modId;
    }

    public static boolean onFarmlandTrample(Level level, BlockPos pos, BlockState state, float fallDistance, Entity entity) {
        if (entity.canTrample(state, pos, fallDistance)) {
            BlockEvent.FarmlandTrampleEvent event = new BlockEvent.FarmlandTrampleEvent(level, pos, state, fallDistance, entity);
            MinecraftForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }
        return false;
    }

    public static int onNoteChange(Level level, BlockPos pos, BlockState state, int old, int _new) {
        NoteBlockEvent.Change event = new NoteBlockEvent.Change(level, pos, state, old, _new);
        if (MinecraftForge.EVENT_BUS.post(event))
            return -1;
        return event.getVanillaNoteId();
    }

    public static boolean hasNoElements(Ingredient ingredient) {
        ItemStack[] items = ingredient.m_43908_();
        if (items.length == 0) return true;
        if (items.length == 1) {
            //If we potentially added a barrier due to the ingredient being an empty tag, try and check if it is the stack we added
            ItemStack item = items[0];
            return item.m_41720_() == Items.f_42127_ && item.m_41786_() instanceof MutableComponent hoverName && hoverName.getString().startsWith("Empty Tag: ");
        }
        return false;
    }

    @Nullable
    public static EntityDataSerializer<?> getSerializer(int id, CrudeIncrementalIntIdentityHashBiMap<EntityDataSerializer<?>> vanilla) {
        EntityDataSerializer<?> serializer = vanilla.m_7942_(id);
        if (serializer == null) {
            // ForgeRegistries.DATA_SERIALIZERS is a deferred register now, so if this method is called too early, the registry will be null
            var registry = (ForgeRegistry<EntityDataSerializer<?>>)ForgeRegistries.ENTITY_DATA_SERIALIZERS.get();
            if (registry != null)
                serializer = registry.getValue(id);
        }
        return serializer;
    }

    public static int getSerializerId(EntityDataSerializer<?> serializer, CrudeIncrementalIntIdentityHashBiMap<EntityDataSerializer<?>> vanilla) {
        int id = vanilla.m_7447_(serializer);
        if (id < 0) {
            // ForgeRegistries.DATA_SERIALIZERS is a deferred register now, so if this method is called too early, the registry will be null
            var registry = (ForgeRegistry<EntityDataSerializer<?>>)ForgeRegistries.ENTITY_DATA_SERIALIZERS.get();
            if (registry != null)
                id = registry.getID(serializer);
        }
        return id;
    }

    public static boolean canEntityDestroy(Level level, BlockPos pos, LivingEntity entity) {
        if (!level.m_46749_(pos))
            return false;
        BlockState state = level.m_8055_(pos);
        return ForgeEventFactory.getMobGriefingEvent(level, entity) && state.canEntityDestroy(level, pos, entity) && ForgeEventFactory.onEntityDestroyBlock(entity, pos, state);
    }

    private static final Map<Holder.Reference<Item>, Integer> VANILLA_BURNS = new HashMap<>();

    /**
     * Gets the burn time of this itemstack.
     */
    public static int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        if (stack.m_41619_())
            return 0;
        else {
            Item item = stack.m_41720_();
            int ret = stack.getBurnTime(recipeType);
            return ForgeEventFactory.getItemBurnTime(stack, ret == -1 ? VANILLA_BURNS.getOrDefault(ForgeRegistries.ITEMS.getDelegateOrThrow(item), 0) : ret, recipeType);
        }
    }

    @SuppressWarnings("deprecation")
    public static synchronized void updateBurns() {
        VANILLA_BURNS.clear();
        FurnaceBlockEntity.m_58423_().forEach((k, v) -> VANILLA_BURNS.put(ForgeRegistries.ITEMS.getDelegateOrThrow(k), v));
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
    public static ObjectArrayList<ItemStack> modifyLoot(ResourceLocation lootTableId, ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        context.setQueriedLootTableId(lootTableId); // In case the ID was set via copy constructor, this will be ignored: intended
        for (var mod : ForgeInternalHandler.getLootModifierManager().getAllLootMods())
            generatedLoot = mod.apply(generatedLoot, context);
        return generatedLoot;
    }

    public static List<String> getModPacks() {
        List<String> modpacks = ResourcePackLoader.getPackNames();
        if(modpacks.isEmpty())
            throw new IllegalStateException("Attempted to retrieve mod packs before they were loaded in!");
        return modpacks;
    }

    public static List<String> getModPacksWithVanilla() {
        List<String> modpacks = getModPacks();
        modpacks.add("vanilla");
        return modpacks;
    }

    private static final Map<EntityType<? extends LivingEntity>, AttributeSupplier> FORGE_ATTRIBUTES = new HashMap<>();
    @Deprecated
    public static Map<EntityType<? extends LivingEntity>, AttributeSupplier> getAttributesView() {
        return Collections.unmodifiableMap(FORGE_ATTRIBUTES);
    }

    @Deprecated
    public static void modifyAttributes() {
        ModLoader.get().postEvent(new EntityAttributeCreationEvent(FORGE_ATTRIBUTES));
        Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> finalMap = new HashMap<>();
        ModLoader.get().postEvent(new EntityAttributeModificationEvent(finalMap));

        finalMap.forEach((k, v) -> {
            AttributeSupplier supplier = DefaultAttributes.m_22297_(k);
            AttributeSupplier.Builder newBuilder = supplier != null ? new AttributeSupplier.Builder(supplier) : new AttributeSupplier.Builder();
            newBuilder.combine(v);
            FORGE_ATTRIBUTES.put(k, newBuilder.m_22265_());
        });
    }

    public static void writeAdditionalLevelSaveData(WorldData worldData, CompoundTag levelTag) {
        CompoundTag fmlData = new CompoundTag();
        ListTag modList = new ListTag();
        ModList.get().getMods().forEach(mi -> {
            final CompoundTag mod = new CompoundTag();
            mod.m_128359_("ModId", mi.getModId());
            mod.m_128359_("ModVersion", MavenVersionStringHelper.artifactVersionToString(mi.getVersion()));
            modList.add(mod);
        });
        fmlData.m_128365_("LoadingModList", modList);

        CompoundTag registries = new CompoundTag();
        fmlData.m_128365_("Registries", registries);
        LOGGER.debug(WORLDPERSISTENCE, "Gathering id map for writing to world save {}", worldData.m_5462_());

        for (Map.Entry<ResourceLocation, ForgeRegistry.Snapshot> e : RegistryManager.ACTIVE.takeSnapshot(true).entrySet())
            registries.m_128365_(e.getKey().toString(), e.getValue().write());
        LOGGER.debug(WORLDPERSISTENCE, "ID Map collection complete {}", worldData.m_5462_());
        levelTag.m_128365_("fml", fmlData);
    }

    @ApiStatus.Internal
    public static void readAdditionalLevelSaveData(LevelStorageSource.LevelStorageAccess access, LevelStorageSource.LevelDirectory levelDirectory) {
        CompoundTag tag = null;
        try {
            CompoundTag rootTag = access.getDataTagRaw(false);
            tag = rootTag.m_128469_("fml");
        } catch (IOException e) {
            try {
                CompoundTag rootTag = access.getDataTagRaw(true);
                tag = rootTag.m_128469_("fml");
            } catch (IOException e2) {
                LOGGER.error(WORLDPERSISTENCE, "Failed to read level data.. ", e2);
                return;
            }
        }
        if (tag.m_128441_("LoadingModList")) {
            ListTag modList = tag.m_128437_("LoadingModList", net.minecraft.nbt.Tag.f_178203_);
            Map<String, ArtifactVersion> mismatchedVersions = new HashMap<>(modList.size());
            Map<String, ArtifactVersion> missingVersions = new HashMap<>(modList.size());
            for (int i = 0; i < modList.size(); i++) {
                CompoundTag mod = modList.m_128728_(i);
                String modId = mod.m_128461_("ModId");
                if (Objects.equals("minecraft",  modId))
                    continue;

                String modVersion = mod.m_128461_("ModVersion");
                final var previousVersion = new DefaultArtifactVersion(modVersion);
                ModList.get().getModContainerById(modId).ifPresentOrElse(container -> {
                    final var loadingVersion = container.getModInfo().getVersion();
                    if (!loadingVersion.equals(previousVersion)) {
                        // Enqueue mismatched versions for bulk event
                        mismatchedVersions.put(modId, previousVersion);
                    }
                }, () -> missingVersions.put(modId, previousVersion));
            }

            final var mismatchEvent = new ModMismatchEvent(levelDirectory, mismatchedVersions, missingVersions);
            ModLoader.get().postEvent(mismatchEvent);

            StringBuilder resolved = new StringBuilder("The following mods have version differences that were marked resolved:");
            StringBuilder unresolved = new StringBuilder("The following mods have version differences that were not resolved:");

            // For mods that were marked resolved, log the version resolution and the mod that resolved the mismatch
            mismatchEvent.getResolved().forEachOrdered((res) -> {
                final var modid = res.modid();
                final var diff = res.versionDifference();
                if (res.wasSelfResolved()) {
                    resolved.append(System.lineSeparator())
                    .append(diff.isMissing()
                        ? "%s (version %s -> MISSING, self-resolved)".formatted(modid, diff.oldVersion())
                        : "%s (version %s -> %s, self-resolved)".formatted(modid, diff.oldVersion(), diff.newVersion())
                    );
                } else {
                    final var resolver = res.resolver().getModId();
                    resolved.append(System.lineSeparator())
                    .append(diff.isMissing()
                        ? "%s (version %s -> MISSING, resolved by %s)".formatted(modid, diff.oldVersion(), resolver)
                        : "%s (version %s -> %s, resolved by %s)".formatted(modid, diff.oldVersion(), diff.newVersion(), resolver)
                    );
                }
            });

            // For mods that did not specify handling, show a warning to users that errors may occur
            mismatchEvent.getUnresolved().forEachOrdered((unres) -> {
                final var modid = unres.modid();
                final var diff = unres.versionDifference();
                unresolved.append(System.lineSeparator())
                .append(diff.isMissing()
                    ? "%s (version %s -> MISSING)".formatted(modid, diff.oldVersion())
                    : "%s (version %s -> %s)".formatted(modid, diff.oldVersion(), diff.newVersion())
                );
            });

            if (mismatchEvent.anyResolved()) {
                resolved.append(System.lineSeparator()).append("Things may not work well.");
                LOGGER.debug(WORLDPERSISTENCE, resolved.toString());
            }

            if (mismatchEvent.anyUnresolved()) {
                unresolved.append(System.lineSeparator()).append("Things may not work well.");
                LOGGER.warn(WORLDPERSISTENCE, unresolved.toString());
            }
        }

        Multimap<ResourceLocation, ResourceLocation> failedElements = null;

        if (tag.m_128441_("Registries")) {
            Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot = new HashMap<>();
            CompoundTag regs = tag.m_128469_("Registries");
            for (String key : regs.m_128431_())
                snapshot.put(new ResourceLocation(key), ForgeRegistry.Snapshot.read(regs.m_128469_(key)));
            failedElements = GameData.injectSnapshot(snapshot, true, true);
        }

        if (failedElements != null && !failedElements.isEmpty()) {
            StringBuilder buf = new StringBuilder();
            buf.append("Forge Mod Loader could not load this save.\n\n")
                .append("There are ").append(failedElements.size()).append(" unassigned registry entries in this save.\n")
                .append("You will not be able to load until they are present again.\n\n");

            failedElements.asMap().forEach((name, entries) -> {
                buf.append("Missing ").append(name).append(":\n");
                entries.forEach(rl -> buf.append("    ").append(rl).append("\n"));
            });
            LOGGER.error(WORLDPERSISTENCE, buf.toString());
        }
    }

    public static String encodeLifecycle(Lifecycle lifecycle) {
        if (lifecycle == Lifecycle.stable())
            return "stable";
        if (lifecycle == Lifecycle.experimental())
            return "experimental";
        if (lifecycle instanceof Lifecycle.Deprecated dep)
            return "deprecated=" + dep.since();
        throw new IllegalArgumentException("Unknown lifecycle.");
    }

    public static Lifecycle parseLifecycle(String lifecycle) {
        if (lifecycle.equals("stable"))
            return Lifecycle.stable();
        if (lifecycle.equals("experimental"))
            return Lifecycle.experimental();
        if (lifecycle.startsWith("deprecated="))
            return Lifecycle.deprecated(Integer.parseInt(lifecycle.substring(lifecycle.indexOf('=') + 1)));
        throw new IllegalArgumentException("Unknown lifecycle.");
    }

    public static boolean shouldSuppressEnderManAnger(EnderMan enderMan, Player player, ItemStack mask) {
        return mask.isEnderMask(player, enderMan) || MinecraftForge.EVENT_BUS.post(new EnderManAngerEvent(enderMan, player));
    }

    private static final Lazy<Map<String, StructuresBecomeConfiguredFix.Conversion>> FORGE_CONVERSION_MAP = Lazy.concurrentOf(() -> {
        Map<String, StructuresBecomeConfiguredFix.Conversion> map = new HashMap<>();
        MinecraftForge.EVENT_BUS.post(new RegisterStructureConversionsEvent(map));
        return ImmutableMap.copyOf(map);
    });

    // DO NOT CALL from within RegisterStructureConversionsEvent, otherwise you'll get a deadlock
    /**
     * @hidden For internal use only.
     */
    @Nullable
    public static StructuresBecomeConfiguredFix.Conversion getStructureConversion(String originalBiome) {
        return FORGE_CONVERSION_MAP.get().get(originalBiome);
    }

    /**
     * @hidden For internal use only.
     */
    public static boolean checkStructureNamespace(String biome) {
        @Nullable ResourceLocation biomeLocation = ResourceLocation.m_135820_(biome);
        return biomeLocation != null && !biomeLocation.m_135827_().equals(ResourceLocation.f_179908_);
    }

    /**
     * <p>
     *    This method is used to prefix the path, where elements of the associated registry are stored, with their namespace, if it is not minecraft
     * </p>
     * <p>
     *    This rules conflicts with equal paths out. If for example the mod {@code fancy_cheese} adds a registry named {@code cheeses},
     *    but the mod {@code awesome_cheese} also adds a registry called {@code cheeses},
     *    they are going to have the same path {@code cheeses}, just with different namespaces.
     *    If {@code additional_cheese} wants to add additional cheese to {@code awesome_cheese}, but not {@code fancy_cheese},
     *    it can not differentiate both. Both paths will look like {@code data/additional_cheese/cheeses}.
     * </p>
     * <p>
     *    The fix, which is applied here prefixes the path of the registry with the namespace,
     *    so {@code fancy_cheese}'s registry stores its elements in {@code data/<namespace>/fancy_cheese/cheeses}
     *    and {@code awesome_cheese}'s registry stores its elements in {@code data/namespace/awesome_cheese/cheeses}
     * </p>
     *
     * @param registryKey key of the registry
     * @return path of the registry key. Prefixed with the namespace if it is not "minecraft"
     */
    public static String prefixNamespace(ResourceLocation registryKey) {
        return registryKey.m_135827_().equals("minecraft") ? registryKey.m_135815_() : registryKey.m_135827_() +  "/"  + registryKey.m_135815_();
    }

    public static boolean canUseEntitySelectors(SharedSuggestionProvider provider) {
        if (provider.m_6761_(Commands.f_165684_))
            return true;
        else if (provider instanceof CommandSourceStack source && source.f_81288_ instanceof ServerPlayer player)
            return PermissionAPI.getPermission(player, ForgeMod.USE_SELECTORS_PERMISSION);
        return false;
    }

    @ApiStatus.Internal
    public static <T> HolderLookup.RegistryLookup<T> wrapRegistryLookup(final HolderLookup.RegistryLookup<T> lookup) {
        return new HolderLookup.RegistryLookup.Delegate<>() {
            @Override public RegistryLookup<T> m_254893_() { return lookup; }
            @Override public Stream<HolderSet.Named<T>> m_214063_() { return Stream.empty(); }
            @SuppressWarnings("deprecation")
            @Override public Optional<HolderSet.Named<T>> m_255050_(TagKey<T> key) { return Optional.of(HolderSet.m_255229_(lookup, key)); }
        };
    }

    /**
     * Handles living entities being under water. This fires the {@link LivingBreatheEvent} and if the entity's air supply
     * is less than or equal to zero also the {@link LivingDrownEvent}. Additionally when the entity is under water it will
     * dismount if {@link IForgeEntity#canBeRiddenUnderFluidType(FluidType, Entity)} returns false.
     *
     * @param entity           The living entity which is currently updated
     * @param consumeAirAmount The amount of air to consume when the entity is unable to breathe
     * @param refillAirAmount  The amount of air to refill when the entity is able to breathe
     * @implNote This method needs to closely replicate the logic found right after the call site in {@link LivingEntity#baseTick()} as it overrides it.
     */
    public static void onLivingBreathe(LivingEntity entity, int consumeAirAmount, int refillAirAmount) {
        // Check things that vanilla considers to be air - these will cause the air supply to be increased.
        boolean isAir = entity.getEyeInFluidType().isAir() || entity.m_9236_().m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20188_(), entity.m_20189_())).m_60713_(Blocks.f_50628_);
        // The following effects cause the entity to not drown, but do not cause the air supply to be increased.
        boolean canBreathe = !entity.canDrownInFluidType(entity.getEyeInFluidType()) || MobEffectUtil.m_19588_(entity) || (entity instanceof Player player && player.m_150110_().f_35934_);
        var breatheEvent = ForgeEventFactory.onLivingBreathe(entity, isAir || canBreathe, consumeAirAmount, refillAirAmount, isAir);
        if (breatheEvent.canBreathe()) {
            if (breatheEvent.canRefillAir()) {
                entity.m_20301_(Math.min(entity.m_20146_() + breatheEvent.getRefillAirAmount(), entity.m_6062_()));
            }
        } else
            entity.m_20301_(entity.m_20146_() - breatheEvent.getConsumeAirAmount());

        if (entity.m_20146_() <= -20) {
            var drownEvent = ForgeEventFactory.onLivingDrown(entity, entity.m_20146_() <= -20, 2.0F, 8);
            if (!drownEvent.isCanceled() && drownEvent.isDrowning()) {
                entity.m_20301_(0);
                Vec3 vec3 = entity.m_20184_();

                for (int i = 0; i < drownEvent.getBubbleCount(); ++i) {
                    double d2 = entity.m_217043_().m_188500_() - entity.m_217043_().m_188500_();
                    double d3 = entity.m_217043_().m_188500_() - entity.m_217043_().m_188500_();
                    double d4 = entity.m_217043_().m_188500_() - entity.m_217043_().m_188500_();
                    entity.m_9236_().m_7106_(ParticleTypes.f_123795_, entity.m_20185_() + d2, entity.m_20186_() + d3, entity.m_20189_() + d4, vec3.f_82479_, vec3.f_82480_, vec3.f_82481_);
                }

                if (drownEvent.getDamageAmount() > 0) {
                    entity.m_6469_(entity.m_269291_().m_269063_(), drownEvent.getDamageAmount());
                }
            }
        }

        if (!isAir && !entity.m_9236_().f_46443_ && entity.m_20159_() && entity.m_20202_() != null && !entity.m_20202_().canBeRiddenUnderFluidType(entity.getEyeInFluidType(), entity)) {
            entity.m_8127_();
        }
    }

    public static void onCreativeModeTabBuildContents(CreativeModeTab tab, ResourceKey<CreativeModeTab> tabKey, CreativeModeTab.DisplayItemsGenerator originalGenerator, CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output output) {
        final var entries = new MutableHashedLinkedMap<ItemStack, CreativeModeTab.TabVisibility>(ItemStackLinkedSet.f_260558_,
            (key, left, right) -> {
                //throw new IllegalStateException("Accidentally adding the same item stack twice " + key.getDisplayName().getString() + " to a Creative Mode Tab: " + tab.getDisplayName().getString());
                // Vanilla adds enchanting books twice in both visibilities.
                // This is just code cleanliness for them. For us lets just increase the visibility and merge the entries.
                return CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
            }
        );

        originalGenerator.m_257865_(params, (stack, vis) -> {
            if (stack.m_41613_() != 1)
                throw new IllegalArgumentException("The stack count must be 1");
            entries.put(stack, vis);
        });

        ModLoader.get().postEvent(new BuildCreativeModeTabContentsEvent(tab, tabKey, params, entries));

        for (var entry : entries)
            output.m_246267_(entry.getKey(), entry.getValue());
    }

    @ApiStatus.Internal
    public static <B extends FriendlyByteBuf> StreamCodec<B, ? extends CustomPacketPayload> getCustomPayloadCodec(ResourceLocation id, int max) {
        var channel = NetworkRegistry.findTarget(id);
        if (channel == null)
            return DiscardedPayload.m_323929_(id, max);

        return StreamCodec.<B, ForgePayload>m_324771_(
            (value, buf) -> {
                value.encoder().accept(buf);
            },
            (buf) -> {
                int len = buf.readableBytes();
                if (len < 0 || len > max)
                    throw new IllegalArgumentException("Payload may not be larger then " + max + " bytes");
                return ForgePayload.create(id, buf.wrap(buf.readBytes(len)));
            }
        );
    }

    @ApiStatus.Internal
    public static boolean onCustomPayload(CustomPacketPayload payload, Connection connection) {
        var context = new CustomPayloadEvent.Context(connection);
        return onCustomPayload(new CustomPayloadEvent(payload.m_293297_().f_314054_(), payload, context, 0));
    }

    @ApiStatus.Internal
    public static boolean onCustomPayload(ClientboundCustomQueryPacket packet, Connection connection) {
        var context = new CustomPayloadEvent.Context(connection);
        return onCustomPayload(new CustomPayloadEvent(packet.f_291166_().m_293131_(), packet.f_291166_(), context, packet.f_134745_()));
    }

    @ApiStatus.Internal
    public static boolean onCustomPayload(ServerboundCustomQueryAnswerPacket packet, Connection connection) {
        var context = new CustomPayloadEvent.Context(connection);
        return onCustomPayload(new CustomPayloadEvent(NetworkInitialization.LOGIN_NAME, packet.f_290461_(), context, packet.f_290801_()));
    }

    @ApiStatus.Internal
    public static boolean onCustomPayload(CustomPayloadEvent event) {
        var connection = event.getSource().getConnection();
        var expectedSide = connection.m_178313_() == PacketFlow.CLIENTBOUND ? LogicalSide.CLIENT : LogicalSide.SERVER;
        if (expectedSide != EffectiveSide.get()) {
            connection.m_129507_(Component.m_237113_("Illegal packet received, terminating connection"));
            return false;
        }

        var channel = NetworkRegistry.findTarget(event.getChannel());
        if (channel != null && channel.dispatch(event))
            return true;

        // Should we always fire this, even if the channel consumed the packet?
        if (!event.getSource().getPacketHandled()) {
            MinecraftForge.EVENT_BUS.post(event);
            return event.getSource().getPacketHandled();
        }

        return false;
    }

    @ApiStatus.Internal
    public static void handleClientConfigurationComplete(Connection connection) {
        if (NetworkContext.get(connection).getType() == ConnectionType.VANILLA) {
            LOGGER.info("Connected to a vanilla server. Catching up missing behaviour.");
            ConfigTracker.INSTANCE.loadDefaultServerConfigs();
        } else
            LOGGER.info("Connected to a modded server.");
    }

    @ApiStatus.Internal
    public static Packet<ClientGamePacketListener> getEntitySpawnPacket(Entity entity) {
        if (!(entity instanceof IEntityAdditionalSpawnData add))
            throw new IllegalArgumentException(entity.getClass() + " is not an instance of " + IEntityAdditionalSpawnData.class);

        return NetworkDirection.PLAY_TO_CLIENT.buildPacket(NetworkInitialization.PLAY, new SpawnEntity(entity));
    }

    @ApiStatus.Internal
    public static boolean readAndTestCondition(RegistryOps<JsonElement> ops, JsonObject json) {
        if (!json.has(ICondition.DEFAULT_FIELD))
            return true;

        var condition = ICondition.SAFE_CODEC.parse(ops, json.getAsJsonObject(ICondition.DEFAULT_FIELD))
                .getOrThrow(JsonParseException::new);

        return condition.test(ConditionCodec.getContext(ops), ops);
    }

    @ApiStatus.Internal
    public static void writeCondition(ICondition condition, JsonObject out) {
        if (condition == null)
            return;
        var data = ICondition.CODEC.encode(condition, JsonOps.INSTANCE, JsonOps.INSTANCE.empty()).getOrThrow(JsonSyntaxException::new);
        out.add(ICondition.DEFAULT_FIELD, data);
    }

    @Nullable
    @ApiStatus.Internal
    public static JsonObject readConditionalAdvancement(RegistryOps<JsonElement> context, JsonObject json) {
        var entries = GsonHelper.m_13832_(json, "advancements", null);
        if (entries == null)
            return readAndTestCondition(context, json) ? json : null;

        int idx = 0;
        for (var ele : entries) {
            if (!ele.isJsonObject())
                throw new JsonSyntaxException("Invalid advancement entry at index " + idx + " Must be JsonObject");

            if (readAndTestCondition(context, ele.getAsJsonObject()))
                return ele.getAsJsonObject();

            idx++;
        }

        return null;
    }

    @ApiStatus.Internal
    public static Codec<Ingredient> enhanceIngredientCodec(Codec<Ingredient> vanilla) {
        return Codec.lazyInitialized(() ->
            Codec.<Ingredient, Ingredient>either(
                ForgeRegistries.INGREDIENT_SERIALIZERS.get().getCodec().dispatch(Ingredient::serializer, IIngredientSerializer::codec),
                vanilla
            )
            .flatComapMap(
                i -> i.left().isPresent() ? i.left().get() : i.right().get(),
                i -> DataResult.success(i.isVanilla() ? Either.right(i) : Either.left(i))
            )
        );
    }

    public static StreamCodec<RegistryFriendlyByteBuf, Ingredient> ingredientStreamCodec() {
        return StreamCodec.<RegistryFriendlyByteBuf, Ingredient>m_320617_(
            (buf, value) -> {
                if (value.isVanilla()) {
                    var items = value.m_43908_();
                    ByteBufCodecs.m_324291_(buf, items.length, Integer.MAX_VALUE);
                    for (var item : items) {
                        ItemStack.f_315801_.m_318638_(buf, item);
                    }
                } else {
                    @SuppressWarnings("unchecked")
                    var serializer = (IIngredientSerializer<Ingredient>)value.serializer();
                    var key = ForgeRegistries.INGREDIENT_SERIALIZERS.get().getKey(serializer);
                    if (key == null)
                        throw new IllegalArgumentException("Tried to write unregistered Ingredient to network: " + value);

                    buf.m_130130_(-1); // Our Marker
                    buf.m_130085_(key);
                    serializer.write(buf, value);
                }
            },
            (buf) -> {
                var size = ByteBufCodecs.m_319449_(buf, Integer.MAX_VALUE);
                if (size != -1) {
                    var ret = NonNullList.<ItemStack>m_182647_(size);
                    for (int x = 0; x < size; x++) {
                        ret.add(ItemStack.f_315801_.m_318688_(buf));
                    }
                    return Ingredient.m_43938_(ret.stream().map(Ingredient.ItemValue::new));
                } else {
                    var key = buf.m_130281_();
                    var serializer = ForgeRegistries.INGREDIENT_SERIALIZERS.get().getValue(key);
                    if (serializer == null)
                        throw new DecoderException("Could not read ingredient of type: " + key);
                    return serializer.read(buf);
                }
            }
        );
    }

    @Nullable
    public static DyeColor getDyeColorFromItemStack(ItemStack stack) {
        if (stack.m_41720_() instanceof DyeItem dye)
            return dye.m_41089_();

        for (int x = 0; x < DyeColor.BLACK.m_41060_(); x++) {
            var color = DyeColor.m_41053_(x);
            if (stack.m_204117_(color.getTag())) {
                return color;
            }
        }

        return null;
    }

    public static DataComponentMap gatherItemComponents(Item item, DataComponentMap dataComponents) {
        return DataComponentMap.m_319349_(dataComponents, ForgeEventFactory.gatherItemComponentsEvent(item, dataComponents).getDataComponentMap());
    }
}