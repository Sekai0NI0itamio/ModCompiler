package com.bothelpers.script;

import com.bothelpers.data.BotRegionsData;
import com.bothelpers.data.NamedLocationsData;
import com.bothelpers.entity.EntityBotHelper;
import com.google.gson.Gson;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BotJobScriptRunner {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, ActiveExecution> ACTIVE_EXECUTIONS = new HashMap<>();
    private static final double MOVE_SPEED = 1.0D;
    private static final double ARRIVAL_DISTANCE_SQ = 2.25D;
    private static final double ENTITY_ARRIVAL_DISTANCE_SQ = 4.0D;
    private static final int BLOCK_SEARCH_RADIUS_CHUNKS = 8;
    private static final int ACTION_FACE_TICKS = 4;
    private static final int MAX_TREE_LOGS = 512;
    private static final int DEFAULT_FISH_RADIUS = 12;
    private static final int DEFAULT_CROP_RADIUS = 12;

    private BotJobScriptRunner() {
    }

    public static boolean runWhenCalled(EntityBotHelper bot, EntityPlayer caller) {
        return runEvent(bot, caller, "When Bot Called");
    }

    public static boolean runEvent(EntityBotHelper bot, EntityPlayer caller, String eventName) {
        SavedScript script = load(bot);
        if (script == null || script.blocks == null || script.blocks.isEmpty()) {
            return false;
        }

        Integer startIndex = null;
        for (SavedBlock block : script.blocks) {
            if (block != null && eventName.equalsIgnoreCase(block.text)) {
                startIndex = block.nextIndex;
                break;
            }
        }

        if (startIndex == null) {
            return false;
        }

        ActiveExecution previous = ACTIVE_EXECUTIONS.remove(bot.getUniqueID());
        if (previous != null) {
            previous.finish(bot);
        }

        bot.hasJob = true;
        ACTIVE_EXECUTIONS.put(bot.getUniqueID(), new ActiveExecution(script, caller, startIndex));
        return true;
    }

    public static void tick(EntityBotHelper bot) {
        ActiveExecution execution = ACTIVE_EXECUTIONS.get(bot.getUniqueID());
        if (execution == null) {
            return;
        }

        execution.tick(bot);
        if (execution.finished) {
            ACTIVE_EXECUTIONS.remove(bot.getUniqueID());
        }
    }

    public static void clear(EntityBotHelper bot) {
        ActiveExecution execution = ACTIVE_EXECUTIONS.remove(bot.getUniqueID());
        if (execution != null) {
            execution.finish(bot);
        }
    }

    private static SavedScript load(EntityBotHelper bot) {
        File worldDirectory = bot.world.getSaveHandler() == null ? null : bot.world.getSaveHandler().getWorldDirectory();
        File scriptFile = BotJobScriptPaths.getScriptFile(worldDirectory, bot.getName(), bot.getUniqueID());
        if (scriptFile == null || !scriptFile.isFile()) {
            return null;
        }

        try {
            String json = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
            return GSON.fromJson(json, SavedScript.class);
        } catch (IOException | RuntimeException ex) {
            System.err.println("Bot Helpers: Failed to execute script for " + bot.getName() + " from " + scriptFile.getAbsolutePath());
            ex.printStackTrace();
            return null;
        }
    }

    private static final class ActiveExecution {
        private final SavedScript script;
        private final EntityPlayer caller;
        private final Deque<RepeatFrame> repeatFrames = new ArrayDeque<>();
        private Integer currentIndex;
        private ActiveAction activeAction;
        private boolean finished;

        private ActiveExecution(SavedScript script, EntityPlayer caller, Integer startIndex) {
            this.script = script;
            this.caller = caller;
            this.currentIndex = startIndex;
        }

        private void tick(EntityBotHelper bot) {
            if (finished) {
                return;
            }

            if (activeAction != null) {
                if (activeAction.tick(bot, caller)) {
                    finishAction(bot);
                }
                return;
            }

            int guard = 0;
            while (!finished && activeAction == null && guard++ < 32) {
                if (!resolveCurrentIndex(bot)) {
                    return;
                }

                if (currentIndex == null || currentIndex < 0 || currentIndex >= script.blocks.size()) {
                    finish(bot);
                    return;
                }

                SavedBlock block = script.blocks.get(currentIndex);
                if (block == null) {
                    currentIndex = null;
                    continue;
                }

                if ("Repeat".equalsIgnoreCase(block.text)) {
                    handleRepeat(bot, block);
                    continue;
                }

                activeAction = createAction(bot, caller, block);
                if (activeAction == null) {
                    currentIndex = block.nextIndex;
                    continue;
                }

                if (activeAction.tick(bot, caller)) {
                    finishAction(bot);
                    continue;
                }

                return;
            }
        }

        private boolean resolveCurrentIndex(EntityBotHelper bot) {
            while (currentIndex == null) {
                if (repeatFrames.isEmpty()) {
                    finish(bot);
                    return false;
                }

                RepeatFrame frame = repeatFrames.peek();
                if (frame.shouldLoop(bot.world)) {
                    currentIndex = frame.bodyIndex;
                    return true;
                }

                repeatFrames.pop();
                currentIndex = frame.afterIndex;
            }

            return true;
        }

        private void handleRepeat(EntityBotHelper bot, SavedBlock block) {
            if (block.nextIndex == null) {
                currentIndex = block.branchIndex;
                return;
            }

            String repeatType = defaultString(block.inputType, "Amount");
            if ("untilminecraft".equals(normalizeKey(repeatType))) {
                long untilWorldTime = getNextWorldTimeForCondition(bot.world.getWorldTime(), block.inputValue);
                if (bot.world.getWorldTime() >= untilWorldTime) {
                    currentIndex = block.branchIndex;
                    return;
                }

                repeatFrames.push(RepeatFrame.until(block.nextIndex, block.branchIndex, untilWorldTime));
                currentIndex = block.nextIndex;
                return;
            }

            int amount = parseWholeNumber(block.inputValue);
            if (amount <= 0) {
                currentIndex = block.branchIndex;
                return;
            }

            repeatFrames.push(RepeatFrame.amount(block.nextIndex, block.branchIndex, amount));
            currentIndex = block.nextIndex;
        }

        private void finishAction(EntityBotHelper bot) {
            if (activeAction == null) {
                return;
            }

            Integer nextIndex = activeAction.getNextIndex();
            boolean stopExecution = activeAction.shouldStopExecution();
            activeAction.cleanup(bot);
            activeAction = null;

            if (stopExecution) {
                finish(bot);
                return;
            }

            currentIndex = nextIndex;
        }

        private void finish(EntityBotHelper bot) {
            if (finished) {
                return;
            }

            if (activeAction != null) {
                activeAction.cleanup(bot);
                activeAction = null;
            }

            bot.getNavigator().clearPath();
            finished = true;
        }
    }

    private abstract static class ActiveAction {
        private final Integer nextIndex;
        private boolean stopExecution;

        private ActiveAction(Integer nextIndex) {
            this.nextIndex = nextIndex;
        }

        protected void stopExecution() {
            this.stopExecution = true;
        }

        protected Integer getNextIndex() {
            return nextIndex;
        }

        protected boolean shouldStopExecution() {
            return stopExecution;
        }

        protected void cleanup(EntityBotHelper bot) {
        }

        protected abstract boolean tick(EntityBotHelper bot, EntityPlayer caller);
    }

    private static final class WaitAction extends ActiveAction {
        private int remainingTicks;
        private boolean initialized;

        private WaitAction(SavedBlock block) {
            super(block.nextIndex);
            this.remainingTicks = Math.max(0, resolveWaitTicks(block));
        }

        @Override
        protected boolean tick(EntityBotHelper bot, EntityPlayer caller) {
            if (!initialized) {
                initialized = true;
            }

            if (remainingTicks <= 0) {
                return true;
            }

            remainingTicks--;
            return remainingTicks <= 0;
        }
    }

    private static final class MoveAction extends ActiveAction {
        private final SavedBlock block;
        private final boolean markResting;
        private int repathCooldown;
        private boolean announced;

        private MoveAction(SavedBlock block, boolean markResting) {
            super(block.nextIndex);
            this.block = block;
            this.markResting = markResting;
        }

        @Override
        protected boolean tick(EntityBotHelper bot, EntityPlayer caller) {
            TargetResolution target = resolveMoveTarget(bot, block, caller);
            if (target == null) {
                stopExecution();
                return true;
            }

            if (!announced) {
                tell(caller, target.message);
                announced = true;
            }

            if (target.entity != null) {
                if (bot.getDistanceSq(target.entity) <= ENTITY_ARRIVAL_DISTANCE_SQ) {
                    bot.getNavigator().clearPath();
                    if (markResting) {
                        bot.isRestingTime = true;
                    }
                    return true;
                }

                if (repathCooldown-- <= 0 || bot.getNavigator().noPath()) {
                    boolean started = bot.getNavigator().tryMoveToEntityLiving(target.entity, MOVE_SPEED);
                    if (!started) {
                        bot.getMoveHelper().setMoveTo(target.entity.posX, target.entity.posY, target.entity.posZ, MOVE_SPEED);
                    }
                    repathCooldown = 10;
                }
                return false;
            }

            if (distanceSqTo(target.pos, bot) <= ARRIVAL_DISTANCE_SQ) {
                bot.getNavigator().clearPath();
                if (markResting) {
                    bot.isRestingTime = true;
                }
                return true;
            }

            if (repathCooldown-- <= 0 || bot.getNavigator().noPath()) {
                boolean started = bot.getNavigator().tryMoveToXYZ(target.pos.getX() + 0.5D, target.pos.getY(), target.pos.getZ() + 0.5D, MOVE_SPEED);
                if (!started) {
                    bot.getMoveHelper().setMoveTo(target.pos.getX() + 0.5D, target.pos.getY(), target.pos.getZ() + 0.5D, MOVE_SPEED);
                }
                repathCooldown = 10;
            }
            return false;
        }
    }

    private static final class DigAction extends ActiveAction {
        private final SavedBlock block;
        private DigOperation digOperation;
        private boolean initialized;

        private DigAction(SavedBlock block) {
            super(block.nextIndex);
            this.block = block;
        }

        @Override
        protected boolean tick(EntityBotHelper bot, EntityPlayer caller) {
            if (!initialized) {
                initialized = true;

                BlockPos targetPos = resolveDigTarget(bot, block);
                if (targetPos == null) {
                    tell(caller, "<" + bot.getName() + "> I couldn't resolve a block to dig.");
                    stopExecution();
                    return true;
                }

                IBlockState targetState = bot.world.getBlockState(targetPos);
                if (targetState.getBlock().isAir(targetState, bot.world, targetPos)) {
                    tell(caller, "<" + bot.getName() + "> There is no block to dig at " + formatPos(targetPos) + ".");
                    stopExecution();
                    return true;
                }

                String toolType = defaultString(block.toolType, "Inventory Tool");
                ToolSelection toolSelection = selectTool(bot, targetState, toolType, block.toolValue);
                if (toolSelection == ToolSelection.NONE) {
                    tell(caller, "<" + bot.getName() + "> Couldn't find a matching tool in inventory, skipping dig block.");
                    return true;
                }

                digOperation = startDigOperation(bot, targetPos, targetState, toolSelection);
                if (digOperation == null) {
                    stopExecution();
                    return true;
                }
            }

            DigTickResult result = tickDigOperation(bot, digOperation);
            if (result == DigTickResult.RUNNING) {
                return false;
            }

            if (result == DigTickResult.FAILED) {
                stopExecution();
            }
            return true;
        }

        @Override
        protected void cleanup(EntityBotHelper bot) {
            finishDigOperation(bot, digOperation);
        }
    }

    private static final class PlaceAction extends ActiveAction {
        private final SavedBlock block;
        private boolean initialized;
        private FakePlayer fakePlayer;
        private PlacementSource source;
        private BlockPos targetPos;
        private int faceTicks;

        private PlaceAction(SavedBlock block) {
            super(block.nextIndex);
            this.block = block;
        }

        @Override
        protected boolean tick(EntityBotHelper bot, EntityPlayer caller) {
            if (!initialized) {
                initialized = true;
                source = resolvePlacementSource(bot, block);
                if (source == null || source.state == null) {
                    tell(caller, "<" + bot.getName() + "> I couldn't resolve a block to place.");
                    stopExecution();
                    return true;
                }

                targetPos = resolvePlacementTarget(bot, block);
                if (targetPos == null) {
                    tell(caller, "<" + bot.getName() + "> I couldn't resolve where to place the block.");
                    stopExecution();
                    return true;
                }

                IBlockState existingState = bot.world.getBlockState(targetPos);
                if (!existingState.getMaterial().isReplaceable() && !existingState.getBlock().isAir(existingState, bot.world, targetPos)) {
                    tell(caller, "<" + bot.getName() + "> Can't place a block at " + formatPos(targetPos) + ".");
                    stopExecution();
                    return true;
                }

                fakePlayer = BotPresenceManager.getActionPlayer(bot);
                if (fakePlayer == null) {
                    stopExecution();
                    return true;
                }

                syncFakePlayer(bot, fakePlayer);
                if (!source.stack.isEmpty() && source.stack.getItem() instanceof ItemBlock) {
                    fakePlayer.setHeldItem(EnumHand.MAIN_HAND, source.stack.copy());
                    bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, source.stack.copy());
                }
                faceTicks = ACTION_FACE_TICKS;
            }

            syncFakePlayer(bot, fakePlayer);
            faceTarget(bot, fakePlayer, getBlockCenter(targetPos));
            if (faceTicks > 0) {
                faceTicks--;
                return false;
            }

            boolean placed;
            if (!source.stack.isEmpty() && source.stack.getItem() instanceof ItemBlock) {
                swingMainHand(bot, fakePlayer);
                EnumActionResult result = fakePlayer.getHeldItemMainhand().onItemUse(fakePlayer, bot.world, targetPos, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
                placed = result == EnumActionResult.SUCCESS;
            } else {
                swingMainHand(bot, fakePlayer);
                placed = bot.world.setBlockState(targetPos, source.state, 3);
            }

            if (!placed) {
                stopExecution();
            }
            return true;
        }

        @Override
        protected void cleanup(EntityBotHelper bot) {
            if (fakePlayer == null || source == null) {
                return;
            }

            if (source.inventorySlot >= 0) {
                writeBackMainHand(bot, fakePlayer, source.inventorySlot);
            } else {
                fakePlayer.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }

    private static final class CutTreeAction extends ActiveAction {
        private final SavedBlock block;
        private final List<BlockPos> pendingLogs = new ArrayList<>();
        private DigOperation digOperation;
        private boolean initialized;
        private boolean replantAttempted;
        private boolean whitelist = true;
        private Set<String> configuredLogs = Collections.emptySet();
        private BotRegionsData.Region region;
        private BlockPos stumpPos;
        private IBlockState rootState;

        private CutTreeAction(SavedBlock block) {
            super(block.nextIndex);
            this.block = block;
        }

        @Override
        protected boolean tick(EntityBotHelper bot, EntityPlayer caller) {
            if (!initialized) {
                initialized = true;
                whitelist = !"Blacklist".equalsIgnoreCase(defaultString(block.secondaryType, "Whitelist"));
                configuredLogs = parseConfigList(block.secondaryValue);
                region = resolveCutTreeRegion(bot, block);

                int radius = Math.max(1, parseWholeNumber(defaultString(block.inputValue, "8")));
                BlockPos root = findNearestTreeRoot(bot, radius, configuredLogs, whitelist, region);
                if (root == null) {
                    return true;
                }

                rootState = bot.world.getBlockState(root);
                pendingLogs.addAll(collectTreeLogs(bot, root, radius, configuredLogs, whitelist, region));
                if (pendingLogs.isEmpty()) {
                    return true;
                }
                stumpPos = findLowestLogPos(pendingLogs);
            }

            while (digOperation == null && !pendingLogs.isEmpty()) {
                BlockPos targetPos = pendingLogs.remove(0);
                IBlockState state = bot.world.getBlockState(targetPos);
                if (state.getBlock().isAir(state, bot.world, targetPos)
                    || !isAllowedLogBlock(state, bot.world, targetPos, configuredLogs, whitelist)
                    || !isWithinRegion(targetPos, region)) {
                    continue;
                }

                ToolSelection toolSelection = selectTreeTool(bot, state, defaultString(block.toolType, "Pick Best / Fallback Hand"), block.toolValue);
                if (toolSelection == ToolSelection.NONE) {
                    return true;
                }

                digOperation = startDigOperation(bot, targetPos, state, toolSelection);
                if (digOperation == null) {
                    return true;
                }
            }

            if (digOperation == null) {
                return true;
            }

            DigTickResult result = tickDigOperation(bot, digOperation);
            if (result == DigTickResult.RUNNING) {
                return false;
            }
            if (result == DigTickResult.FAILED) {
                stopExecution();
                finishDigOperation(bot, digOperation);
                digOperation = null;
                return true;
            }

            finishDigOperation(bot, digOperation);
            digOperation = null;
            if (!pendingLogs.isEmpty()) {
                return false;
            }

            if (!replantAttempted) {
                replantAttempted = true;
                attemptCutTreeReplant(bot, block, rootState, stumpPos);
            }
            return true;
        }

        @Override
        protected void cleanup(EntityBotHelper bot) {
            finishDigOperation(bot, digOperation);
        }
    }

    private static final class FishAction extends ActiveAction {
        private final SavedBlock block;
        private BotRegionsData.Region region;
        private BlockPos waterPos;
        private BlockPos standPos;
        private FakePlayer fakePlayer;
        private int rodSlot = -1;
        private int repathCooldown;
        private int faceTicks = ACTION_FACE_TICKS;
        private int waitTicks;
        private boolean initialized;
        private boolean casted;

        private FishAction(SavedBlock block) {
            super(block.nextIndex);
            this.block = block;
        }

        @Override
        protected boolean tick(EntityBotHelper bot, EntityPlayer caller) {
            if (!initialized) {
                initialized = true;
                region = resolveConfiguredRegion(bot, block);
                int radius = Math.max(2, parseWholeNumber(defaultString(block.inputValue, String.valueOf(DEFAULT_FISH_RADIUS))));
                waterPos = findNearestWaterSource(bot, radius, region);
                if (waterPos == null) {
                    stopExecution();
                    return true;
                }

                standPos = findClosestStandablePosition(bot.world, waterPos, 2);
                if (standPos == null) {
                    stopExecution();
                    return true;
                }

                rodSlot = findBestFishingRodSlot(bot);
                if (rodSlot < 0) {
                    stopExecution();
                    return true;
                }

                fakePlayer = BotPresenceManager.getActionPlayer(bot);
                if (fakePlayer == null) {
                    stopExecution();
                    return true;
                }

                syncFakePlayer(bot, fakePlayer);
                ItemStack rodStack = bot.botInventory.getStackInSlot(rodSlot).copy();
                fakePlayer.setHeldItem(EnumHand.MAIN_HAND, rodStack);
                bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, rodStack.copy());
                waitTicks = resolveFishingWaitTicks(bot, rodStack);
            }

            int moveState = tickMoveToward(bot, standPos, repathCooldown, ARRIVAL_DISTANCE_SQ);
            if (moveState != Integer.MIN_VALUE) {
                repathCooldown = moveState;
                return false;
            }

            syncFakePlayer(bot, fakePlayer);
            faceTarget(bot, fakePlayer, getBlockCenter(waterPos));
            if (faceTicks > 0) {
                faceTicks--;
                return false;
            }

            if (!casted) {
                if (!castFishingRod(bot, fakePlayer)) {
                    stopExecution();
                    return true;
                }
                casted = true;
                return false;
            }

            if (waitTicks > 0) {
                waitTicks--;
                return false;
            }

            reelFishingRod(bot, fakePlayer);
            grantFishingCatch(bot, bot.botInventory.getStackInSlot(rodSlot));
            return true;
        }

        @Override
        protected void cleanup(EntityBotHelper bot) {
            if (fakePlayer == null) {
                return;
            }

            if (fakePlayer.fishEntity != null) {
                fakePlayer.fishEntity.setDead();
                fakePlayer.fishEntity = null;
            }

            if (rodSlot >= 0) {
                writeBackMainHand(bot, fakePlayer, rodSlot);
            } else {
                fakePlayer.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }

    private static final class PickCropsAction extends ActiveAction {
        private final SavedBlock block;
        private final List<CropTarget> pendingCrops = new ArrayList<>();
        private BotRegionsData.Region region;
        private CropTarget currentTarget;
        private BlockPos standPos;
        private DigOperation digOperation;
        private int repathCooldown;
        private boolean initialized;

        private PickCropsAction(SavedBlock block) {
            super(block.nextIndex);
            this.block = block;
        }

        @Override
        protected boolean tick(EntityBotHelper bot, EntityPlayer caller) {
            if (!initialized) {
                initialized = true;
                region = resolveConfiguredRegion(bot, block);
                int radius = Math.max(2, parseWholeNumber(defaultString(block.inputValue, String.valueOf(DEFAULT_CROP_RADIUS))));
                pendingCrops.addAll(collectHarvestableCrops(bot, radius, region));
                if (pendingCrops.isEmpty()) {
                    return true;
                }
            }

            if (digOperation != null) {
                DigTickResult result = tickDigOperation(bot, digOperation);
                if (result == DigTickResult.RUNNING) {
                    return false;
                }

                finishDigOperation(bot, digOperation);
                if (result == DigTickResult.SUCCESS && shouldReplantCrops(block)) {
                    attemptCropReplant(bot, currentTarget);
                }

                digOperation = null;
                currentTarget = null;
                standPos = null;
                repathCooldown = 0;
                return pendingCrops.isEmpty();
            }

            while (currentTarget == null && !pendingCrops.isEmpty()) {
                CropTarget nextTarget = pendingCrops.remove(0);
                if (!isWithinRegion(nextTarget.pos, region)) {
                    continue;
                }
                if (getHarvestableCropTarget(bot.world, nextTarget.pos) == null) {
                    continue;
                }

                BlockPos candidateStandPos = findClosestStandablePosition(bot.world, nextTarget.pos, 2);
                if (candidateStandPos == null) {
                    continue;
                }

                currentTarget = nextTarget;
                standPos = candidateStandPos;
            }

            if (currentTarget == null) {
                return true;
            }

            int moveState = tickMoveToward(bot, standPos, repathCooldown, ARRIVAL_DISTANCE_SQ);
            if (moveState != Integer.MIN_VALUE) {
                repathCooldown = moveState;
                return false;
            }

            CropTarget liveTarget = getHarvestableCropTarget(bot.world, currentTarget.pos);
            if (liveTarget == null) {
                currentTarget = null;
                standPos = null;
                repathCooldown = 0;
                return pendingCrops.isEmpty();
            }
            currentTarget = liveTarget;

            digOperation = startDigOperation(bot, currentTarget.pos, currentTarget.state, ToolSelection.HAND);
            if (digOperation == null) {
                currentTarget = null;
                standPos = null;
                repathCooldown = 0;
                return pendingCrops.isEmpty();
            }
            return false;
        }

        @Override
        protected void cleanup(EntityBotHelper bot) {
            finishDigOperation(bot, digOperation);
        }
    }

    private static ActiveAction createAction(EntityBotHelper bot, EntityPlayer caller, SavedBlock block) {
        if ("Go To".equalsIgnoreCase(block.text)) {
            return new MoveAction(block, false);
        }
        if ("Sleep At".equalsIgnoreCase(block.text)) {
            return new MoveAction(block, true);
        }
        if ("Dig Block".equalsIgnoreCase(block.text)) {
            return new DigAction(block);
        }
        if ("Place Block".equalsIgnoreCase(block.text)) {
            return new PlaceAction(block);
        }
        if ("Wait".equalsIgnoreCase(block.text)) {
            return new WaitAction(block);
        }
        if ("Cut Tree".equalsIgnoreCase(block.text)) {
            return new CutTreeAction(block);
        }
        if ("Fish".equalsIgnoreCase(block.text)) {
            return new FishAction(block);
        }
        if ("Pick Crops".equalsIgnoreCase(block.text)) {
            return new PickCropsAction(block);
        }
        return null;
    }

    private static BotRegionsData.Region resolveConfiguredRegion(EntityBotHelper bot, SavedBlock block) {
        if (!"Region Restriction".equalsIgnoreCase(defaultString(block.regionMode, "No Region Restriction"))) {
            return null;
        }

        BotRegionsData data = BotRegionsData.get(bot.world);
        return data == null ? null : data.getRegion(block.regionValue);
    }

    private static int tickMoveToward(EntityBotHelper bot, BlockPos targetPos, int repathCooldown, double arrivalDistanceSq) {
        if (targetPos == null) {
            return Integer.MIN_VALUE;
        }

        if (distanceSqTo(targetPos, bot) <= arrivalDistanceSq) {
            bot.getNavigator().clearPath();
            return Integer.MIN_VALUE;
        }

        if (repathCooldown <= 0 || bot.getNavigator().noPath()) {
            boolean started = bot.getNavigator().tryMoveToXYZ(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, MOVE_SPEED);
            if (!started) {
                bot.getMoveHelper().setMoveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, MOVE_SPEED);
            }
            return 10;
        }

        return repathCooldown - 1;
    }

    private static BlockPos findClosestStandablePosition(World world, BlockPos around, int horizontalRadius) {
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos candidate = around.add(dx, dy, dz);
                    if (!isStandablePosition(world, candidate)) {
                        continue;
                    }

                    double distance = candidate.distanceSq(around);
                    if (bestPos == null || distance < bestDistance) {
                        bestPos = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }

        return bestPos;
    }

    private static boolean isStandablePosition(World world, BlockPos pos) {
        IBlockState feetState = world.getBlockState(pos);
        IBlockState headState = world.getBlockState(pos.up());
        IBlockState groundState = world.getBlockState(pos.down());
        return isPassableFeetSpace(world, pos, feetState)
            && isPassableFeetSpace(world, pos.up(), headState)
            && groundState.getMaterial().blocksMovement()
            && !groundState.getMaterial().isLiquid();
    }

    private static boolean isPassableFeetSpace(World world, BlockPos pos, IBlockState state) {
        Material material = state.getMaterial();
        return state.getBlock().isAir(state, world, pos)
            || material.isReplaceable()
            || (!material.blocksMovement() && !material.isLiquid());
    }

    private static BlockPos findNearestWaterSource(EntityBotHelper bot, int radius, BotRegionsData.Region region) {
        int minX = region != null ? region.getMinPos().getX() : MathHelper.floor(bot.posX) - radius;
        int maxX = region != null ? region.getMaxPos().getX() : MathHelper.floor(bot.posX) + radius;
        int minZ = region != null ? region.getMinPos().getZ() : MathHelper.floor(bot.posZ) - radius;
        int maxZ = region != null ? region.getMaxPos().getZ() : MathHelper.floor(bot.posZ) + radius;
        int minY = region != null ? region.getMinPos().getY() : Math.max(1, MathHelper.floor(bot.posY) - 6);
        int maxY = region != null ? region.getMaxPos().getY() : Math.min(bot.world.getActualHeight() - 1, MathHelper.floor(bot.posY) + 4);

        double bestDistance = Double.MAX_VALUE;
        BlockPos bestPos = null;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isWithinRegion(pos, region)) {
                        continue;
                    }

                    IBlockState state = bot.world.getBlockState(pos);
                    if (!isWaterSourceBlock(state)) {
                        continue;
                    }

                    if (findClosestStandablePosition(bot.world, pos, 2) == null) {
                        continue;
                    }

                    double distance = distanceSqTo(pos, bot);
                    if (bestPos == null || distance < bestDistance) {
                        bestPos = pos;
                        bestDistance = distance;
                    }
                }
            }
        }

        return bestPos;
    }

    private static boolean isWaterSourceBlock(IBlockState state) {
        if (state.getMaterial() != Material.WATER) {
            return false;
        }

        if (state.getProperties().containsKey(BlockLiquid.LEVEL)) {
            Integer level = (Integer) state.getValue(BlockLiquid.LEVEL);
            return level != null && level.intValue() == 0;
        }

        return true;
    }

    private static int findBestFishingRodSlot(EntityBotHelper bot) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int slot = 0; slot < bot.botInventory.getSizeInventory(); slot++) {
            ItemStack stack = bot.botInventory.getStackInSlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemFishingRod)) {
                continue;
            }

            int score = EnchantmentHelper.getFishingLuckBonus(stack) * 7
                + EnchantmentHelper.getFishingSpeedBonus(stack) * 5;
            if (stack.isItemStackDamageable()) {
                score += Math.max(0, stack.getMaxDamage() - stack.getItemDamage()) / 32;
            }

            if (bestSlot < 0 || score > bestScore) {
                bestSlot = slot;
                bestScore = score;
            }
        }

        return bestSlot;
    }

    private static int resolveFishingWaitTicks(EntityBotHelper bot, ItemStack rodStack) {
        int speedBonus = EnchantmentHelper.getFishingSpeedBonus(rodStack);
        return Math.max(50, 120 - (speedBonus * 15) + bot.getRNG().nextInt(40));
    }

    private static boolean castFishingRod(EntityBotHelper bot, FakePlayer fakePlayer) {
        if (fakePlayer == null) {
            return false;
        }

        if (fakePlayer.fishEntity != null) {
            fakePlayer.fishEntity.setDead();
            fakePlayer.fishEntity = null;
        }

        ItemStack held = fakePlayer.getHeldItemMainhand();
        if (held.isEmpty() || !(held.getItem() instanceof ItemFishingRod)) {
            return false;
        }

        swingMainHand(bot, fakePlayer);
        ActionResult<ItemStack> result = ((ItemFishingRod) held.getItem()).onItemRightClick(bot.world, fakePlayer, EnumHand.MAIN_HAND);
        ItemStack updated = result == null ? held : result.getResult();
        fakePlayer.setHeldItem(EnumHand.MAIN_HAND, updated.copy());
        bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, updated.copy());
        return fakePlayer.fishEntity != null;
    }

    private static void reelFishingRod(EntityBotHelper bot, FakePlayer fakePlayer) {
        if (fakePlayer == null) {
            return;
        }

        ItemStack held = fakePlayer.getHeldItemMainhand();
        if (!held.isEmpty() && held.getItem() instanceof ItemFishingRod) {
            swingMainHand(bot, fakePlayer);
            ActionResult<ItemStack> result = ((ItemFishingRod) held.getItem()).onItemRightClick(bot.world, fakePlayer, EnumHand.MAIN_HAND);
            ItemStack updated = result == null ? held : result.getResult();
            fakePlayer.setHeldItem(EnumHand.MAIN_HAND, updated.copy());
            bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, updated.copy());
        }

        if (fakePlayer.fishEntity != null) {
            fakePlayer.fishEntity.setDead();
            fakePlayer.fishEntity = null;
        }
    }

    private static void grantFishingCatch(EntityBotHelper bot, ItemStack rodStack) {
        ItemStack caughtStack = buildFishingCatch(bot, rodStack);
        if (caughtStack.isEmpty()) {
            return;
        }

        ItemStack remaining = bot.storeItemStack(caughtStack);
        if (!remaining.isEmpty()) {
            spawnRemainderAtBot(bot, remaining);
        }
    }

    private static ItemStack buildFishingCatch(EntityBotHelper bot, ItemStack rodStack) {
        int luckBonus = EnchantmentHelper.getFishingLuckBonus(rodStack);
        float treasureChance = Math.min(0.05F + (luckBonus * 0.02F), 0.16F);
        float junkChance = Math.max(0.10F - (luckBonus * 0.01F), 0.03F);
        float roll = bot.getRNG().nextFloat();

        if (roll < treasureChance) {
            switch (bot.getRNG().nextInt(4)) {
                case 0:
                    return new ItemStack(Items.NAME_TAG);
                case 1:
                    return new ItemStack(Items.SADDLE);
                case 2:
                    return new ItemStack(Items.BOW);
                default:
                    return new ItemStack(Items.ENCHANTED_BOOK);
            }
        }

        if (roll < treasureChance + junkChance) {
            switch (bot.getRNG().nextInt(5)) {
                case 0:
                    return new ItemStack(Items.LEATHER_BOOTS);
                case 1:
                    return new ItemStack(Items.BOWL);
                case 2:
                    return new ItemStack(Items.STICK, 2);
                case 3:
                    return new ItemStack(Items.STRING, 2);
                default:
                    return new ItemStack(Items.ROTTEN_FLESH);
            }
        }

        int fishRoll = bot.getRNG().nextInt(10);
        if (fishRoll == 0) {
            return new ItemStack(Items.FISH, 1, 3);
        }
        if (fishRoll <= 2) {
            return new ItemStack(Items.FISH, 1, 1);
        }
        if (fishRoll == 3) {
            return new ItemStack(Items.FISH, 1, 2);
        }
        return new ItemStack(Items.FISH, 1, 0);
    }

    private static void spawnRemainderAtBot(EntityBotHelper bot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || bot.world.isRemote) {
            return;
        }

        EntityItem entityItem = new EntityItem(bot.world, bot.posX, bot.posY + 0.5D, bot.posZ, stack.copy());
        bot.world.spawnEntity(entityItem);
    }

    private static List<CropTarget> collectHarvestableCrops(EntityBotHelper bot, int radius, BotRegionsData.Region region) {
        List<CropTarget> crops = new ArrayList<>();
        int minX = region != null ? region.getMinPos().getX() : MathHelper.floor(bot.posX) - radius;
        int maxX = region != null ? region.getMaxPos().getX() : MathHelper.floor(bot.posX) + radius;
        int minZ = region != null ? region.getMinPos().getZ() : MathHelper.floor(bot.posZ) - radius;
        int maxZ = region != null ? region.getMaxPos().getZ() : MathHelper.floor(bot.posZ) + radius;
        int minY = region != null ? region.getMinPos().getY() : Math.max(0, MathHelper.floor(bot.posY) - radius);
        int maxY = region != null ? region.getMaxPos().getY() : Math.min(bot.world.getActualHeight() - 1, MathHelper.floor(bot.posY) + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isWithinRegion(pos, region)) {
                        continue;
                    }

                    CropTarget target = getHarvestableCropTarget(bot.world, pos);
                    if (target != null) {
                        crops.add(target);
                    }
                }
            }
        }

        crops.sort(new Comparator<CropTarget>() {
            @Override
            public int compare(CropTarget first, CropTarget second) {
                return Double.compare(distanceSqTo(first.pos, bot), distanceSqTo(second.pos, bot));
            }
        });
        return crops;
    }

    private static CropTarget getHarvestableCropTarget(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof BlockCrops && ((BlockCrops) block).isMaxAge(state)) {
            return new CropTarget(pos, state, CropKind.fromCropBlock(block), EnumFacing.UP);
        }

        if (block == Blocks.NETHER_WART && state.getValue(BlockNetherWart.AGE).intValue() >= 3) {
            return new CropTarget(pos, state, CropKind.NETHER_WART, EnumFacing.UP);
        }

        if (block == Blocks.COCOA && state.getValue(BlockCocoa.AGE).intValue() >= 2) {
            return new CropTarget(pos, state, CropKind.COCOA, state.getValue(BlockHorizontal.FACING));
        }

        if (block == Blocks.MELON_BLOCK && hasAdjacentStem(world, pos, Blocks.MELON_STEM)) {
            return new CropTarget(pos, state, CropKind.MELON, EnumFacing.UP);
        }

        if (block == Blocks.PUMPKIN && hasAdjacentStem(world, pos, Blocks.PUMPKIN_STEM)) {
            return new CropTarget(pos, state, CropKind.PUMPKIN, EnumFacing.UP);
        }

        return null;
    }

    private static boolean hasAdjacentStem(World world, BlockPos pos, Block stemBlock) {
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            if (world.getBlockState(pos.offset(facing)).getBlock() == stemBlock) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldReplantCrops(SavedBlock block) {
        return "Replant If Possible".equalsIgnoreCase(defaultString(block.secondaryType, "No Replant"));
    }

    private static void attemptCropReplant(EntityBotHelper bot, CropTarget target) {
        if (target == null) {
            return;
        }

        switch (target.kind) {
            case WHEAT:
                placeCropSeed(bot, target.pos, Items.WHEAT_SEEDS, -1);
                return;
            case CARROT:
                placeCropSeed(bot, target.pos, Items.CARROT, -1);
                return;
            case POTATO:
                placeCropSeed(bot, target.pos, Items.POTATO, -1);
                return;
            case BEETROOT:
                placeCropSeed(bot, target.pos, Items.BEETROOT_SEEDS, -1);
                return;
            case NETHER_WART:
                placeCropSeed(bot, target.pos, Items.NETHER_WART, -1);
                return;
            case COCOA:
                placeCocoa(bot, target);
                return;
            default:
                return;
        }
    }

    private static boolean placeCropSeed(EntityBotHelper bot, BlockPos targetPos, Item item, int metadata) {
        IBlockState currentState = bot.world.getBlockState(targetPos);
        if (!currentState.getMaterial().isReplaceable() && !currentState.getBlock().isAir(currentState, bot.world, targetPos)) {
            return false;
        }

        BlockPos soilPos = targetPos.down();
        IBlockState soilState = bot.world.getBlockState(soilPos);
        if (soilState.getMaterial().isReplaceable() || soilState.getBlock().isAir(soilState, bot.world, soilPos)) {
            return false;
        }

        int slot = findInventoryItemSlot(bot, item, metadata);
        return slot >= 0 && useInventoryItemOnBlock(bot, targetPos, soilPos, EnumFacing.UP, slot);
    }

    private static boolean placeCocoa(EntityBotHelper bot, CropTarget target) {
        IBlockState currentState = bot.world.getBlockState(target.pos);
        if (!currentState.getMaterial().isReplaceable() && !currentState.getBlock().isAir(currentState, bot.world, target.pos)) {
            return false;
        }

        BlockPos supportPos = target.pos.offset(target.facing.getOpposite());
        IBlockState supportState = bot.world.getBlockState(supportPos);
        if (!isLogBlock(supportState, bot.world, supportPos)) {
            return false;
        }

        int slot = findInventoryItemSlot(bot, Items.DYE, EnumDyeColor.BROWN.getDyeDamage());
        return slot >= 0 && useInventoryItemOnBlock(bot, target.pos, supportPos, target.facing, slot);
    }

    private static int findInventoryItemSlot(EntityBotHelper bot, Item item, int metadata) {
        for (int slot = 0; slot < bot.botInventory.getSizeInventory(); slot++) {
            ItemStack stack = bot.botInventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }

            if (metadata >= 0 && stack.getMetadata() != metadata) {
                continue;
            }

            return slot;
        }

        return -1;
    }

    private static boolean useInventoryItemOnBlock(EntityBotHelper bot, BlockPos lookPos, BlockPos usePos, EnumFacing facing, int slot) {
        if (slot < 0) {
            return false;
        }

        ItemStack stack = bot.botInventory.getStackInSlot(slot).copy();
        if (stack.isEmpty()) {
            return false;
        }

        FakePlayer fakePlayer = BotPresenceManager.getActionPlayer(bot);
        if (fakePlayer == null) {
            return false;
        }

        syncFakePlayer(bot, fakePlayer);
        faceTarget(bot, fakePlayer, getBlockCenter(lookPos));
        fakePlayer.setHeldItem(EnumHand.MAIN_HAND, stack);
        bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, stack.copy());
        swingMainHand(bot, fakePlayer);

        float hitX = 0.5F;
        float hitY = facing == EnumFacing.UP ? 1.0F : 0.5F;
        float hitZ = 0.5F;
        if (facing == EnumFacing.NORTH) {
            hitZ = 0.0F;
        } else if (facing == EnumFacing.SOUTH) {
            hitZ = 1.0F;
        } else if (facing == EnumFacing.WEST) {
            hitX = 0.0F;
        } else if (facing == EnumFacing.EAST) {
            hitX = 1.0F;
        }

        EnumActionResult result = fakePlayer.getHeldItemMainhand().onItemUse(fakePlayer, bot.world, usePos, EnumHand.MAIN_HAND, facing, hitX, hitY, hitZ);
        writeBackMainHand(bot, fakePlayer, slot);
        return result == EnumActionResult.SUCCESS;
    }

    private static int resolveWaitTicks(SavedBlock block) {
        double value;
        try {
            value = Double.parseDouble(defaultString(block.inputValue, "0"));
        } catch (NumberFormatException ex) {
            return 0;
        }

        String waitType = normalizeKey(defaultString(block.inputType, "Seconds"));
        if ("minutes".equals(waitType)) {
            return (int) Math.max(0, Math.round(value * 60.0D * 20.0D));
        }
        if ("minecraftdays".equals(waitType)) {
            return (int) Math.max(0, Math.round(value * 24000.0D));
        }
        return (int) Math.max(0, Math.round(value * 20.0D));
    }

    private static TargetResolution resolveMoveTarget(EntityBotHelper bot, SavedBlock block, EntityPlayer caller) {
        String inputType = defaultString(block.inputType, "Name");

        if ("Coordinate".equalsIgnoreCase(inputType)) {
            BlockPos pos = new BlockPos(block.coordX, block.coordY, block.coordZ);
            return TargetResolution.at(pos, "<" + bot.getName() + "> Moving to " + formatPos(pos) + ".");
        }

        if ("Entity".equalsIgnoreCase(inputType)) {
            Entity target = findEntityByName(bot, block.inputValue);
            if (target == null) {
                tell(caller, "<" + bot.getName() + "> I couldn't find entity '" + block.inputValue + "'.");
                return null;
            }
            return TargetResolution.follow(target, "<" + bot.getName() + "> Moving to " + target.getName() + ".");
        }

        BlockPos namedLocation = findNamedLocation(bot, block.inputValue);
        if (namedLocation != null) {
            return TargetResolution.at(namedLocation, "<" + bot.getName() + "> Moving to " + formatPos(namedLocation) + ".");
        }

        Entity target = findEntityByName(bot, block.inputValue);
        if (target != null) {
            return TargetResolution.follow(target, "<" + bot.getName() + "> Moving to " + target.getName() + ".");
        }

        tell(caller, "<" + bot.getName() + "> I couldn't find '" + block.inputValue + "'.");
        return null;
    }

    private static BlockPos resolveDigTarget(EntityBotHelper bot, SavedBlock block) {
        String inputType = defaultString(block.inputType, "Name");
        if ("Coordinate".equalsIgnoreCase(inputType)) {
            return new BlockPos(block.coordX, block.coordY, block.coordZ);
        }
        if ("Name".equalsIgnoreCase(inputType)) {
            return findNamedLocation(bot, block.inputValue);
        }
        if ("Minecraft Block".equalsIgnoreCase(inputType)) {
            return findNearestBlockOfType(bot, block.inputValue);
        }
        return null;
    }

    private static BlockPos resolvePlacementTarget(EntityBotHelper bot, SavedBlock block) {
        String targetType = defaultString(block.secondaryType, "Coordinate");
        if ("Coordinate".equalsIgnoreCase(targetType)) {
            return new BlockPos(block.secondaryCoordX, block.secondaryCoordY, block.secondaryCoordZ);
        }
        if ("Name".equalsIgnoreCase(targetType)) {
            return findNamedLocation(bot, block.secondaryValue);
        }
        return null;
    }

    private static PlacementSource resolvePlacementSource(EntityBotHelper bot, SavedBlock block) {
        String sourceType = defaultString(block.inputType, "Minecraft Block");

        if ("Inventory Block".equalsIgnoreCase(sourceType)) {
            int slot = findInventoryItemSlot(bot, block.inputValue);
            if (slot < 0) {
                return null;
            }

            ItemStack stack = bot.botInventory.getStackInSlot(slot).copy();
            if (!(stack.getItem() instanceof ItemBlock)) {
                return null;
            }

            Block blockType = ((ItemBlock) stack.getItem()).getBlock();
            return new PlacementSource(blockType.getDefaultState(), stack, slot);
        }

        if ("Name".equalsIgnoreCase(sourceType)) {
            BlockPos namedPos = findNamedLocation(bot, block.inputValue);
            if (namedPos == null) {
                return null;
            }

            IBlockState state = bot.world.getBlockState(namedPos);
            Item item = Item.getItemFromBlock(state.getBlock());
            ItemStack stack = item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, 1, state.getBlock().damageDropped(state));
            return new PlacementSource(state, stack, -1);
        }

        Block blockType = getBlockFromValue(block.inputValue);
        if (blockType == null) {
            return null;
        }

        Item item = Item.getItemFromBlock(blockType);
        ItemStack stack = item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, 1, blockType.damageDropped(blockType.getDefaultState()));
        return new PlacementSource(blockType.getDefaultState(), stack, -1);
    }

    private static ToolSelection selectTool(EntityBotHelper bot, IBlockState targetState, String toolType, String toolValue) {
        if ("Hand".equalsIgnoreCase(toolType)) {
            return ToolSelection.HAND;
        }

        if ("Minecraft Tool".equalsIgnoreCase(toolType)) {
            int slot = findInventoryItemSlot(bot, toolValue);
            return slot < 0 ? ToolSelection.NONE : new ToolSelection(slot, bot.botInventory.getStackInSlot(slot).copy());
        }

        return selectBestInventoryTool(bot, targetState);
    }

    private static ToolSelection selectTreeTool(EntityBotHelper bot, IBlockState targetState, String toolType, String toolValue) {
        if ("Inventory Tool".equalsIgnoreCase(toolType)) {
            int slot = findInventoryItemSlot(bot, toolValue);
            return slot < 0 ? ToolSelection.NONE : new ToolSelection(slot, bot.botInventory.getStackInSlot(slot).copy());
        }

        ToolSelection bestTool = selectBestInventoryTool(bot, targetState);
        return bestTool == ToolSelection.NONE ? ToolSelection.HAND : bestTool;
    }

    private static ToolSelection selectBestInventoryTool(EntityBotHelper bot, IBlockState targetState) {
        int bestSlot = -1;
        float bestScore = 1.0F;
        for (int slot = 0; slot < bot.botInventory.getSizeInventory(); slot++) {
            ItemStack stack = bot.botInventory.getStackInSlot(slot);
            if (stack.isEmpty() || !isTool(stack)) {
                continue;
            }

            float score = stack.getDestroySpeed(targetState);
            if (stack.canHarvestBlock(targetState)) {
                score += 5.0F;
            }

            if (bestSlot < 0 || score > bestScore) {
                bestSlot = slot;
                bestScore = score;
            }
        }

        return bestSlot < 0 ? ToolSelection.NONE : new ToolSelection(bestSlot, bot.botInventory.getStackInSlot(bestSlot).copy());
    }

    private static DigOperation startDigOperation(EntityBotHelper bot, BlockPos targetPos, IBlockState targetState, ToolSelection toolSelection) {
        FakePlayer fakePlayer = BotPresenceManager.getActionPlayer(bot);
        if (fakePlayer == null) {
            return null;
        }

        syncFakePlayer(bot, fakePlayer);
        if (toolSelection.slot >= 0) {
            fakePlayer.setHeldItem(EnumHand.MAIN_HAND, toolSelection.stack.copy());
            bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, toolSelection.stack.copy());
        } else {
            fakePlayer.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        float relativeHardness = targetState.getPlayerRelativeBlockHardness(fakePlayer, bot.world, targetPos);
        int totalTicks = relativeHardness <= 0.0F ? 200 : Math.max(1, (int) Math.ceil(1.0F / relativeHardness));
        DigOperation digOperation = new DigOperation(fakePlayer, toolSelection, targetPos, totalTicks);
        digOperation.faceTicks = 0;
        return digOperation;
    }

    private static DigTickResult tickDigOperation(EntityBotHelper bot, DigOperation digOperation) {
        if (digOperation == null || digOperation.fakePlayer == null) {
            return DigTickResult.FAILED;
        }

        IBlockState currentState = bot.world.getBlockState(digOperation.targetPos);
        if (currentState.getBlock().isAir(currentState, bot.world, digOperation.targetPos)) {
            clearBreakProgress(bot, digOperation.targetPos);
            return DigTickResult.SUCCESS;
        }

        syncFakePlayer(bot, digOperation.fakePlayer);
        faceTarget(bot, digOperation.fakePlayer, getBlockCenter(digOperation.targetPos));

        if (digOperation.faceTicks > 0) {
            digOperation.faceTicks--;
            return DigTickResult.RUNNING;
        }

        if (digOperation.remainingTicks > 0) {
            if (digOperation.remainingTicks % 2 == 0 || digOperation.totalTicks <= 3) {
                swingMainHand(bot, digOperation.fakePlayer);
            }
            digOperation.remainingTicks--;
            int progressedTicks = digOperation.totalTicks - digOperation.remainingTicks;
            int stage = digOperation.totalTicks <= 0 ? 9 : Math.min(9, Math.max(0, (int) Math.floor((progressedTicks * 10.0D) / digOperation.totalTicks)));
            bot.world.sendBlockBreakProgress(bot.getEntityId(), digOperation.targetPos, stage);
            if (digOperation.remainingTicks > 0) {
                return DigTickResult.RUNNING;
            }
        }

        bot.world.sendBlockBreakProgress(bot.getEntityId(), digOperation.targetPos, 9);
        swingMainHand(bot, digOperation.fakePlayer);
        boolean harvested = digOperation.fakePlayer.interactionManager.tryHarvestBlock(digOperation.targetPos);
        clearBreakProgress(bot, digOperation.targetPos);
        if (!harvested) {
            IBlockState afterState = bot.world.getBlockState(digOperation.targetPos);
            return afterState.getBlock().isAir(afterState, bot.world, digOperation.targetPos)
                ? DigTickResult.SUCCESS
                : DigTickResult.FAILED;
        }

        bot.collectDropsAt(digOperation.targetPos);
        return DigTickResult.SUCCESS;
    }

    private static void finishDigOperation(EntityBotHelper bot, DigOperation digOperation) {
        if (digOperation == null || digOperation.fakePlayer == null) {
            return;
        }

        clearBreakProgress(bot, digOperation.targetPos);
        if (digOperation.toolSelection.slot >= 0) {
            writeBackMainHand(bot, digOperation.fakePlayer, digOperation.toolSelection.slot);
        } else {
            digOperation.fakePlayer.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    private static void swingMainHand(EntityBotHelper bot, FakePlayer fakePlayer) {
        bot.triggerMainHandSwing();
        if (fakePlayer != null) {
            fakePlayer.swingArm(EnumHand.MAIN_HAND);
        }
    }

    private static BotRegionsData.Region resolveCutTreeRegion(EntityBotHelper bot, SavedBlock block) {
        if (!"Region Restriction".equalsIgnoreCase(defaultString(block.regionMode, "No Region Restriction"))) {
            return null;
        }

        BotRegionsData data = BotRegionsData.get(bot.world);
        return data == null ? null : data.getRegion(block.regionValue);
    }

    private static boolean isWithinRegion(BlockPos pos, BotRegionsData.Region region) {
        return region == null || (pos != null && region.contains(pos));
    }

    private static void clearBreakProgress(EntityBotHelper bot, BlockPos pos) {
        if (bot != null && pos != null) {
            bot.world.sendBlockBreakProgress(bot.getEntityId(), pos, -1);
        }
    }

    private static void faceTarget(EntityBotHelper bot, FakePlayer fakePlayer, Vec3d target) {
        double dx = target.x - bot.posX;
        double dy = target.y - (bot.posY + bot.getEyeHeight());
        double dz = target.z - bot.posZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, horizontalDistance) * (180.0D / Math.PI)));

        applyLook(bot, yaw, pitch);
        if (fakePlayer != null) {
            applyLook(fakePlayer, yaw, pitch);
        }
    }

    private static void applyLook(Entity entity, float yaw, float pitch) {
        entity.prevRotationYaw = entity.rotationYaw;
        entity.rotationYaw = yaw;
        entity.prevRotationPitch = entity.rotationPitch;
        entity.rotationPitch = pitch;
        if (entity instanceof net.minecraft.entity.EntityLivingBase) {
            net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) entity;
            living.rotationYawHead = yaw;
            living.renderYawOffset = yaw;
        }
    }

    private static Vec3d getBlockCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static void syncFakePlayer(EntityBotHelper bot, FakePlayer fakePlayer) {
        fakePlayer.copyLocationAndAnglesFrom(bot);
        fakePlayer.dimension = bot.dimension;
        fakePlayer.interactionManager.setGameType(net.minecraft.world.GameType.SURVIVAL);
    }

    private static void writeBackMainHand(EntityBotHelper bot, FakePlayer fakePlayer, int inventorySlot) {
        ItemStack updated = fakePlayer.getHeldItemMainhand().copy();
        bot.botInventory.setInventorySlotContents(inventorySlot, updated);
        bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, updated.copy());
        fakePlayer.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static int findInventoryItemSlot(EntityBotHelper bot, String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return -1;
        }

        String wanted = itemName.trim();
        for (int slot = 0; slot < bot.botInventory.getSizeInventory(); slot++) {
            ItemStack stack = bot.botInventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation resourceLocation = Item.REGISTRY.getNameForObject(stack.getItem());
            String registryName = resourceLocation == null ? "" : resourceLocation.toString();
            if (registryName.equalsIgnoreCase(wanted) || stack.getDisplayName().equalsIgnoreCase(wanted)) {
                return slot;
            }
        }

        return -1;
    }

    private static boolean isTool(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof ItemTool
            || item instanceof ItemSword
            || item instanceof ItemHoe
            || item instanceof ItemShears;
    }

    private static BlockPos findLowestLogPos(List<BlockPos> logs) {
        BlockPos lowest = null;
        for (BlockPos pos : logs) {
            if (lowest == null
                || pos.getY() < lowest.getY()
                || (pos.getY() == lowest.getY() && (pos.getX() < lowest.getX() || (pos.getX() == lowest.getX() && pos.getZ() < lowest.getZ())))) {
                lowest = pos;
            }
        }
        return lowest;
    }

    private static void attemptCutTreeReplant(EntityBotHelper bot, SavedBlock block, IBlockState rootState, BlockPos stumpPos) {
        String mode = defaultString(block.saplingMode, "No Replant");
        if ("No Replant".equalsIgnoreCase(mode) || rootState == null || stumpPos == null) {
            return;
        }

        int saplingSlot = "Auto Detect".equalsIgnoreCase(mode)
            ? findAutoSaplingSlot(bot, rootState)
            : findConfiguredSaplingSlot(bot, block);
        if (saplingSlot < 0) {
            return;
        }

        placeSaplingFromInventory(bot, stumpPos, saplingSlot);
    }

    private static int findConfiguredSaplingSlot(EntityBotHelper bot, SavedBlock block) {
        Set<String> configuredSaplings = parseConfigList(block.saplingValue);
        boolean whitelist = !"Blacklist".equalsIgnoreCase(defaultString(block.saplingFilterMode, "Whitelist"));

        for (int slot = 0; slot < bot.botInventory.getSizeInventory(); slot++) {
            ItemStack stack = bot.botInventory.getStackInSlot(slot);
            if (!isSaplingStack(stack)) {
                continue;
            }

            if (configuredSaplings.isEmpty()) {
                return slot;
            }

            ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
            String registryName = id == null ? "" : id.toString().toLowerCase();
            String displayName = stack.getDisplayName().toLowerCase();
            boolean listed = configuredSaplings.contains(registryName) || configuredSaplings.contains(displayName);
            if ((whitelist && listed) || (!whitelist && !listed)) {
                return slot;
            }
        }

        return -1;
    }

    private static int findAutoSaplingSlot(EntityBotHelper bot, IBlockState rootState) {
        String logName = "";
        ResourceLocation logId = Block.REGISTRY.getNameForObject(rootState.getBlock());
        if (logId != null) {
            logName = logId.toString().toLowerCase();
        }

        Set<String> treeKeywords = extractTreeKeywords(rootState, logName);
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int slot = 0; slot < bot.botInventory.getSizeInventory(); slot++) {
            ItemStack stack = bot.botInventory.getStackInSlot(slot);
            if (!isSaplingStack(stack)) {
                continue;
            }

            ResourceLocation itemId = Item.REGISTRY.getNameForObject(stack.getItem());
            String saplingName = itemId == null ? stack.getDisplayName().toLowerCase() : itemId.toString().toLowerCase();
            int score = scoreSaplingAgainstTree(logName, treeKeywords, saplingName);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestScore > 0 ? bestSlot : -1;
    }

    private static boolean placeSaplingFromInventory(EntityBotHelper bot, BlockPos targetPos, int slot) {
        if (slot < 0 || targetPos == null) {
            return false;
        }

        ItemStack stack = bot.botInventory.getStackInSlot(slot).copy();
        if (!isSaplingStack(stack)) {
            return false;
        }

        IBlockState currentState = bot.world.getBlockState(targetPos);
        if (!currentState.getMaterial().isReplaceable() && !currentState.getBlock().isAir(currentState, bot.world, targetPos)) {
            return false;
        }

        if (!(stack.getItem() instanceof ItemBlock)) {
            return false;
        }

        BlockPos placeAgainst = targetPos.down();
        IBlockState supportState = bot.world.getBlockState(placeAgainst);
        if (supportState.getMaterial().isReplaceable() || supportState.getBlock().isAir(supportState, bot.world, placeAgainst)) {
            return false;
        }

        FakePlayer fakePlayer = BotPresenceManager.getActionPlayer(bot);
        if (fakePlayer == null) {
            return false;
        }

        syncFakePlayer(bot, fakePlayer);
        faceTarget(bot, fakePlayer, getBlockCenter(targetPos));
        fakePlayer.setHeldItem(EnumHand.MAIN_HAND, stack);
        bot.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, stack.copy());
        swingMainHand(bot, fakePlayer);

        EnumActionResult result = fakePlayer.getHeldItemMainhand().onItemUse(fakePlayer, bot.world, placeAgainst, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        writeBackMainHand(bot, fakePlayer, slot);
        return result == EnumActionResult.SUCCESS;
    }

    private static boolean isSaplingStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }

        Block block = ((ItemBlock) stack.getItem()).getBlock();
        ResourceLocation id = Block.REGISTRY.getNameForObject(block);
        String blockName = id == null ? "" : id.toString().toLowerCase();
        return blockName.contains("sapling") || block.getLocalizedName().toLowerCase().contains("sapling");
    }

    private static Set<String> extractTreeKeywords(IBlockState rootState, String registryName) {
        Set<String> keywords = extractTreeKeywords(registryName);
        if (rootState != null) {
            for (Comparable<?> value : rootState.getProperties().values()) {
                String keyword = String.valueOf(value).toLowerCase();
                if (!keyword.isEmpty() && !"none".equals(keyword)) {
                    keywords.add(keyword);
                }
            }
        }
        return keywords;
    }

    private static Set<String> extractTreeKeywords(String registryName) {
        Set<String> keywords = new HashSet<>();
        if (registryName == null) {
            return keywords;
        }

        String[] parts = registryName.toLowerCase().replace(':', '_').split("[^a-z0-9]+");
        for (String part : parts) {
            if (part.isEmpty()
                || "minecraft".equals(part)
                || "log".equals(part)
                || "logs".equals(part)
                || "wood".equals(part)
                || "bark".equals(part)
                || "stripped".equals(part)) {
                continue;
            }
            keywords.add(part);
        }
        return keywords;
    }

    private static int scoreSaplingAgainstTree(String logName, Set<String> treeKeywords, String saplingName) {
        int score = saplingName.contains("sapling") ? 1 : 0;
        if (!logName.isEmpty() && !saplingName.isEmpty() && namespaceOf(logName).equals(namespaceOf(saplingName))) {
            score += 2;
        }
        for (String keyword : treeKeywords) {
            if (saplingName.contains(keyword)) {
                score += 6;
            }
        }
        return score;
    }

    private static String namespaceOf(String registryName) {
        int separator = registryName.indexOf(':');
        return separator < 0 ? "" : registryName.substring(0, separator);
    }

    private static BlockPos findNearestTreeRoot(EntityBotHelper bot, int radius, Set<String> configuredLogs, boolean whitelist, BotRegionsData.Region region) {
        int minX = MathHelper.floor(bot.posX) - radius;
        int maxX = MathHelper.floor(bot.posX) + radius;
        int minZ = MathHelper.floor(bot.posZ) - radius;
        int maxZ = MathHelper.floor(bot.posZ) + radius;
        int minY = Math.max(0, MathHelper.floor(bot.posY) - radius);
        int maxY = Math.min(bot.world.getActualHeight() - 1, MathHelper.floor(bot.posY) + (radius * 2));

        double bestDistance = Double.MAX_VALUE;
        BlockPos bestPos = null;
        boolean bestHasLeaves = false;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isWithinRegion(pos, region)) {
                        continue;
                    }
                    IBlockState state = bot.world.getBlockState(pos);
                    if (!isAllowedLogBlock(state, bot.world, pos, configuredLogs, whitelist)) {
                        continue;
                    }

                    boolean hasLeaves = hasNearbyLeaves(bot.world, pos, 2);
                    double distance = distanceSqTo(pos, bot);
                    if (bestPos == null
                        || (hasLeaves && !bestHasLeaves)
                        || (hasLeaves == bestHasLeaves && distance < bestDistance)) {
                        bestPos = pos;
                        bestDistance = distance;
                        bestHasLeaves = hasLeaves;
                    }
                }
            }
        }

        return bestPos;
    }

    private static List<BlockPos> collectTreeLogs(EntityBotHelper bot, BlockPos root, int radius, Set<String> configuredLogs, boolean whitelist, BotRegionsData.Region region) {
        List<BlockPos> logs = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(root);

        int horizontalLimit = Math.max(radius, 8) + 6;
        int verticalLimit = Math.max(24, radius * 3);

        while (!queue.isEmpty() && visited.size() < MAX_TREE_LOGS) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }

            if (Math.abs(pos.getX() - root.getX()) > horizontalLimit
                || Math.abs(pos.getZ() - root.getZ()) > horizontalLimit
                || Math.abs(pos.getY() - root.getY()) > verticalLimit) {
                continue;
            }

            if (!isWithinRegion(pos, region)) {
                continue;
            }

            IBlockState state = bot.world.getBlockState(pos);
            if (!isAllowedLogBlock(state, bot.world, pos, configuredLogs, whitelist)) {
                continue;
            }

            logs.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        queue.addLast(pos.add(dx, dy, dz));
                    }
                }
            }
        }

        logs.sort(new Comparator<BlockPos>() {
            @Override
            public int compare(BlockPos first, BlockPos second) {
                int yCompare = Integer.compare(second.getY(), first.getY());
                if (yCompare != 0) {
                    return yCompare;
                }
                return Double.compare(distanceSqTo(first, bot), distanceSqTo(second, bot));
            }
        });
        return logs;
    }

    private static Set<String> parseConfigList(String configValue) {
        Set<String> values = new HashSet<>();
        if (configValue == null || configValue.trim().isEmpty()) {
            return values;
        }

        for (String part : configValue.split("[,;\\n]")) {
            String trimmed = part.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static boolean isAllowedLogBlock(IBlockState state, World world, BlockPos pos, Set<String> configuredLogs, boolean whitelist) {
        if (!isLogBlock(state, world, pos)) {
            return false;
        }

        if (configuredLogs == null || configuredLogs.isEmpty()) {
            return true;
        }

        ResourceLocation id = Block.REGISTRY.getNameForObject(state.getBlock());
        String registryName = id == null ? "" : id.toString().toLowerCase();
        String localizedName = state.getBlock().getLocalizedName().toLowerCase();
        boolean listed = configuredLogs.contains(registryName) || configuredLogs.contains(localizedName);
        return whitelist ? listed : !listed;
    }

    private static boolean isLogBlock(IBlockState state, World world, BlockPos pos) {
        Block block = state.getBlock();
        if (block.isWood(world, pos)) {
            return true;
        }

        ResourceLocation id = Block.REGISTRY.getNameForObject(block);
        String name = id == null ? "" : id.toString().toLowerCase();
        return name.contains("log")
            || (name.contains("wood") && !name.contains("plank") && !name.contains("slab") && !name.contains("stairs") && !name.contains("leaves"));
    }

    private static boolean hasNearbyLeaves(World world, BlockPos pos, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    IBlockState state = world.getBlockState(checkPos);
                    if (isLeafBlock(state, world, checkPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isLeafBlock(IBlockState state, World world, BlockPos pos) {
        Block block = state.getBlock();
        if (block.isLeaves(state, world, pos)) {
            return true;
        }

        ResourceLocation id = Block.REGISTRY.getNameForObject(block);
        String name = id == null ? "" : id.toString().toLowerCase();
        return name.contains("leaves") || name.contains("leaf");
    }

    private static BlockPos findNearestBlockOfType(EntityBotHelper bot, String blockName) {
        Block wantedBlock = getBlockFromValue(blockName);
        if (wantedBlock == null) {
            return null;
        }

        int botChunkX = MathHelper.floor(bot.posX) >> 4;
        int botChunkZ = MathHelper.floor(bot.posZ) >> 4;
        double bestDistance = Double.MAX_VALUE;
        BlockPos bestPos = null;

        for (int chunkX = botChunkX - BLOCK_SEARCH_RADIUS_CHUNKS; chunkX <= botChunkX + BLOCK_SEARCH_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = botChunkZ - BLOCK_SEARCH_RADIUS_CHUNKS; chunkZ <= botChunkZ + BLOCK_SEARCH_RADIUS_CHUNKS; chunkZ++) {
                net.minecraft.world.chunk.Chunk chunk = bot.world.getChunk(chunkX, chunkZ);
                int minX = chunkX << 4;
                int minZ = chunkZ << 4;

                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = minX + localX;
                        int worldZ = minZ + localZ;
                        int topY = chunk.getHeight(new BlockPos(worldX, 0, worldZ));

                        for (int y = 0; y <= topY; y++) {
                            BlockPos pos = new BlockPos(worldX, y, worldZ);
                            IBlockState state = bot.world.getBlockState(pos);
                            if (state.getBlock() == wantedBlock) {
                                double distance = bot.getDistanceSq(pos);
                                if (distance < bestDistance) {
                                    bestDistance = distance;
                                    bestPos = pos;
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private static Block getBlockFromValue(String blockName) {
        if (blockName == null || blockName.trim().isEmpty()) {
            return null;
        }

        String trimmed = blockName.trim();
        try {
            ResourceLocation resourceLocation = new ResourceLocation(trimmed);
            if (Block.REGISTRY.containsKey(resourceLocation)) {
                return Block.REGISTRY.getObject(resourceLocation);
            }
        } catch (IllegalArgumentException ignored) {
        }

        for (Block block : Block.REGISTRY) {
            ResourceLocation registryName = Block.REGISTRY.getNameForObject(block);
            if (registryName != null
                && (registryName.toString().equalsIgnoreCase(trimmed) || block.getLocalizedName().equalsIgnoreCase(trimmed))) {
                return block;
            }
        }

        return null;
    }

    private static Entity findEntityByName(EntityBotHelper bot, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String wanted = name.trim();
        Entity bestMatch = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : bot.world.loadedEntityList) {
            if (entity == bot) {
                continue;
            }

            String displayName = entity.getName() == null ? "" : entity.getName();
            ResourceLocation entityId = EntityList.getKey(entity);
            String registryName = entityId == null ? "" : entityId.toString();
            if (!displayName.equalsIgnoreCase(wanted) && !registryName.equalsIgnoreCase(wanted)) {
                continue;
            }

            double distance = bot.getDistanceSq(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = entity;
            }
        }
        return bestMatch;
    }

    private static BlockPos findNamedLocation(EntityBotHelper bot, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        NamedLocationsData data = NamedLocationsData.get(bot.world);
        if (data == null) {
            return null;
        }

        for (NamedLocationsData.NamedLocation location : data.locations) {
            if (location.name != null && location.name.equalsIgnoreCase(name.trim())) {
                return location.pos;
            }
        }

        return null;
    }

    private static long getNextWorldTimeForCondition(long worldTime, String condition) {
        long dayTime = worldTime % 24000L;
        long targetDayTime = getConditionDayTime(condition);
        long delta = targetDayTime - dayTime;
        if (delta < 0L) {
            delta += 24000L;
        }
        return worldTime + delta;
    }

    private static long getConditionDayTime(String condition) {
        String normalized = normalizeCondition(condition);
        if ("midday".equals(normalized)) {
            return 6000L;
        }
        if ("nightstart".equals(normalized)) {
            return 12000L;
        }
        if ("midnight".equals(normalized)) {
            return 18000L;
        }
        return 0L;
    }

    private static String normalizeCondition(String condition) {
        if (condition == null) {
            return "sunrise";
        }

        String normalized = normalizeKey(condition);
        if ("night".equals(normalized)) {
            return "nightstart";
        }
        return normalized;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replace(" ", "").replace("_", "");
    }

    private static int parseWholeNumber(String value) {
        try {
            return Integer.parseInt(defaultString(value, "0"));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double distanceSqTo(BlockPos pos, EntityBotHelper bot) {
        double dx = bot.posX - (pos.getX() + 0.5D);
        double dy = bot.posY - pos.getY();
        double dz = bot.posZ - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static void tell(EntityPlayer caller, String message) {
        // Script execution now runs quietly; the only bot chat kept is the spawn greeting.
    }

    private static final class TargetResolution {
        private final BlockPos pos;
        private final Entity entity;
        private final String message;

        private TargetResolution(BlockPos pos, Entity entity, String message) {
            this.pos = pos;
            this.entity = entity;
            this.message = message;
        }

        private static TargetResolution at(BlockPos pos, String message) {
            return new TargetResolution(pos, null, message);
        }

        private static TargetResolution follow(Entity entity, String message) {
            return new TargetResolution(null, entity, message);
        }
    }

    private static final class RepeatFrame {
        private final Integer bodyIndex;
        private final Integer afterIndex;
        private int remainingAmount;
        private final long untilWorldTime;
        private final boolean untilMode;

        private RepeatFrame(Integer bodyIndex, Integer afterIndex, int remainingAmount, long untilWorldTime, boolean untilMode) {
            this.bodyIndex = bodyIndex;
            this.afterIndex = afterIndex;
            this.remainingAmount = remainingAmount;
            this.untilWorldTime = untilWorldTime;
            this.untilMode = untilMode;
        }

        private static RepeatFrame amount(Integer bodyIndex, Integer afterIndex, int amount) {
            return new RepeatFrame(bodyIndex, afterIndex, amount, 0L, false);
        }

        private static RepeatFrame until(Integer bodyIndex, Integer afterIndex, long untilWorldTime) {
            return new RepeatFrame(bodyIndex, afterIndex, 0, untilWorldTime, true);
        }

        private boolean shouldLoop(World world) {
            if (untilMode) {
                return world.getWorldTime() < untilWorldTime;
            }

            remainingAmount--;
            return remainingAmount > 0;
        }
    }

    private enum DigTickResult {
        RUNNING,
        SUCCESS,
        FAILED
    }

    private enum CropKind {
        WHEAT,
        CARROT,
        POTATO,
        BEETROOT,
        NETHER_WART,
        COCOA,
        MELON,
        PUMPKIN,
        GENERIC;

        private static CropKind fromCropBlock(Block block) {
            if (block == Blocks.WHEAT) {
                return WHEAT;
            }
            if (block == Blocks.CARROTS) {
                return CARROT;
            }
            if (block == Blocks.POTATOES) {
                return POTATO;
            }
            if (block == Blocks.BEETROOTS) {
                return BEETROOT;
            }
            return GENERIC;
        }
    }

    private static final class CropTarget {
        private final BlockPos pos;
        private final IBlockState state;
        private final CropKind kind;
        private final EnumFacing facing;

        private CropTarget(BlockPos pos, IBlockState state, CropKind kind, EnumFacing facing) {
            this.pos = pos;
            this.state = state;
            this.kind = kind;
            this.facing = facing;
        }
    }

    private static final class SavedScript {
        String botName;
        String botUuid;
        int dimension;
        List<SavedBlock> blocks = new ArrayList<>();
    }

    private static final class SavedBlock {
        String text;
        int x;
        int y;
        boolean hasInput;
        String inputType;
        String inputValue;
        int coordX;
        int coordY;
        int coordZ;
        String secondaryType;
        String secondaryValue;
        int secondaryCoordX;
        int secondaryCoordY;
        int secondaryCoordZ;
        String toolType;
        String toolValue;
        String regionMode;
        String regionValue;
        String saplingMode;
        String saplingFilterMode;
        String saplingValue;
        Integer nextIndex;
        Integer branchIndex;
    }

    private static final class ToolSelection {
        private static final ToolSelection HAND = new ToolSelection(-2, ItemStack.EMPTY);
        private static final ToolSelection NONE = new ToolSelection(-1, ItemStack.EMPTY);

        private final int slot;
        private final ItemStack stack;

        private ToolSelection(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }

    private static final class DigOperation {
        private final FakePlayer fakePlayer;
        private final ToolSelection toolSelection;
        private final BlockPos targetPos;
        private final int totalTicks;
        private int remainingTicks;
        private int faceTicks = ACTION_FACE_TICKS;

        private DigOperation(FakePlayer fakePlayer, ToolSelection toolSelection, BlockPos targetPos, int totalTicks) {
            this.fakePlayer = fakePlayer;
            this.toolSelection = toolSelection;
            this.targetPos = targetPos;
            this.totalTicks = totalTicks;
            this.remainingTicks = totalTicks;
        }
    }

    private static final class PlacementSource {
        private final IBlockState state;
        private final ItemStack stack;
        private final int inventorySlot;

        private PlacementSource(IBlockState state, ItemStack stack, int inventorySlot) {
            this.state = state;
            this.stack = stack;
            this.inventorySlot = inventorySlot;
        }
    }
}
