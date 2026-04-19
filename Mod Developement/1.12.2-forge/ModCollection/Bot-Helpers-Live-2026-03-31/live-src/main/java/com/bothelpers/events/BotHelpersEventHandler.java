package com.bothelpers.events;

import com.bothelpers.data.BotRegionsData;
import com.bothelpers.data.NamedLocationsData;
import com.bothelpers.entity.EntityBotHelper;
import com.bothelpers.script.BotJobScriptRunner;
import com.bothelpers.script.BotNaturalSpawner;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "bothelpers")
public class BotHelpersEventHandler {
    private static final BotNaturalSpawner BOT_NATURAL_SPAWNER = new BotNaturalSpawner();
    private static final Map<UUID, RegionSelection> REGION_SELECTIONS = new HashMap<>();

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.indexOf('@') < 0) {
            return;
        }

        for (Entity entity : event.getPlayer().world.loadedEntityList) {
            if (!(entity instanceof EntityBotHelper)) {
                continue;
            }

            EntityBotHelper bot = (EntityBotHelper) entity;
            if (isBotMentioned(message, bot.getName())) {
                BotJobScriptRunner.runWhenCalled(bot, event.getPlayer());
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || !(event.world instanceof net.minecraft.world.WorldServer)) {
            return;
        }

        net.minecraft.world.WorldServer world = (net.minecraft.world.WorldServer) event.world;
        if (!world.getGameRules().getBoolean("doMobSpawning") || world.getTotalWorldTime() % 20L != 0L) {
            return;
        }

        List<EntityBotHelper> bots = new ArrayList<>();
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityBotHelper && !entity.isDead) {
                bots.add((EntityBotHelper) entity);
            }
        }

        if (!bots.isEmpty()) {
            boolean spawnHostile = world.getDifficulty() != net.minecraft.world.EnumDifficulty.PEACEFUL;
            BOT_NATURAL_SPAWNER.findChunksForSpawning(world, bots, spawnHostile, true, world.getWorldInfo().getWorldTotalTime() % 400L == 0L);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }

        if (handleNamedStickUse(event)) {
            return;
        }

        if (handleSignInteraction(event)) {
            return;
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND || !isNamedStick(event.getItemStack())) {
            return;
        }

        RayTraceResult rayTrace = event.getEntityPlayer().rayTrace(5.0D, 1.0F);
        if (rayTrace == null || rayTrace.typeOfHit != RayTraceResult.Type.BLOCK) {
            RegionSelection selection = REGION_SELECTIONS.get(event.getEntityPlayer().getUniqueID());
            if (selection != null && (selection.firstPos != null || selection.secondPos != null)) {
                selection.clear();
                sendAqua(event.getEntityPlayer(), "Region selection cleared.");
            }
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreakSign(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }

        IBlockState state = event.getState();
        if (!isSignState(state)) {
            return;
        }

        BotRegionsData regionsData = BotRegionsData.get(event.getWorld());
        if (regionsData != null) {
            BotRegionsData.Region region = regionsData.getRegionBySign(event.getPos());
            if (region != null) {
                regionsData.removeRegionBySign(event.getPos());
                sendAqua(event.getPlayer(), "Deleted region '" + region.name + "'.");
                return;
            }
        }

        TileEntity te = event.getWorld().getTileEntity(event.getPos());
        if (!(te instanceof TileEntitySign)) {
            return;
        }

        TileEntitySign sign = (TileEntitySign) te;
        if (isRegionDeclarationSign(sign)) {
            return;
        }

        String name = getFlatSignText(sign);
        if (!name.isEmpty()) {
            NamedLocationsData data = NamedLocationsData.get(event.getWorld());
            if (data != null) {
                data.removeLocation(name);
                event.getPlayer().sendMessage(new TextComponentString("Bot Helpers: Unregistered location '" + name + "'"));
            }
        }
    }

    private static boolean handleNamedStickUse(PlayerInteractEvent.RightClickBlock event) {
        if (!isNamedStick(event.getItemStack())) {
            return false;
        }

        TileEntity te = event.getWorld().getTileEntity(event.getPos());
        if (te instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) te;
            String regionName = getRegionName(sign);
            if (regionName != null) {
                applyRegionSelectionToSign(event, sign, regionName);
                return true;
            }
        }

        RegionSelection selection = getSelection(event.getEntityPlayer());
        if (selection.firstPos == null || (selection.firstPos != null && selection.secondPos != null)) {
            selection.firstPos = event.getPos();
            selection.secondPos = null;
            sendAqua(event.getEntityPlayer(), "Region point 1 set to " + formatPos(event.getPos()) + ".");
        } else {
            selection.secondPos = event.getPos();
            sendAqua(event.getEntityPlayer(), "Region point 2 set to " + formatPos(event.getPos()) + ". Right-click the REGION sign with this stick to apply.");
        }

        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
        return true;
    }

    private static boolean handleSignInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().isEmpty()) {
            return false;
        }

        IBlockState state = event.getWorld().getBlockState(event.getPos());
        if (!isSignState(state)) {
            return false;
        }

        TileEntity te = event.getWorld().getTileEntity(event.getPos());
        if (!(te instanceof TileEntitySign)) {
            return false;
        }

        TileEntitySign sign = (TileEntitySign) te;
        String regionName = getRegionName(sign);
        if (regionName != null) {
            BotRegionsData data = BotRegionsData.get(event.getWorld());
            BotRegionsData.Region region = data == null ? null : data.getRegionBySign(event.getPos());
            if (region == null) {
                sendAqua(event.getEntityPlayer(), "Region '" + regionName + "' is ready. Use a named stick to set point 1 and point 2, then right-click this sign with the stick to apply.");
            } else {
                printRegionDetails(event.getEntityPlayer(), region);
            }
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
            return true;
        }

        BlockPos targetPos = event.getPos().down();
        if (state.getBlock() == Blocks.WALL_SIGN) {
            EnumFacing facing = state.getValue(net.minecraft.block.BlockWallSign.FACING);
            targetPos = event.getPos().offset(facing.getOpposite());
        }

        IBlockState targetState = event.getWorld().getBlockState(targetPos);
        String blockTypeName = targetState.getBlock().getLocalizedName();

        NamedLocationsData data = NamedLocationsData.get(event.getWorld());
        if (data != null) {
            String name = getFlatSignText(sign);
            if (!name.isEmpty()) {
                data.addLocation(name, targetPos, blockTypeName);
                event.getEntityPlayer().sendMessage(new TextComponentString("Bot Helpers: Registered location '" + name + "' at (" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ") on " + blockTypeName));
            }
        }

        return true;
    }

    private static void applyRegionSelectionToSign(PlayerInteractEvent.RightClickBlock event, TileEntitySign sign, String regionName) {
        RegionSelection selection = getSelection(event.getEntityPlayer());
        BotRegionsData data = BotRegionsData.get(event.getWorld());
        BotRegionsData.Region existing = data == null ? null : data.getRegionBySign(event.getPos());

        if (selection.firstPos == null || selection.secondPos == null) {
            if (existing != null) {
                printRegionDetails(event.getEntityPlayer(), existing);
                sendAqua(event.getEntityPlayer(), "Set two new points with a named stick, then right-click this sign again to update the region.");
            } else {
                sendAqua(event.getEntityPlayer(), "Set point 1 and point 2 with a named stick before applying the region.");
            }
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (data != null) {
            data.addOrUpdateRegion(regionName, event.getPos(), selection.firstPos, selection.secondPos);
            updateRegionSign(sign, regionName, selection.firstPos, selection.secondPos);
            sendAqua(event.getEntityPlayer(), "Applied region '" + regionName + "' from " + formatPos(selection.firstPos) + " to " + formatPos(selection.secondPos) + ".");
        }

        selection.clear();
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void updateRegionSign(TileEntitySign sign, String regionName, BlockPos firstPos, BlockPos secondPos) {
        sign.signText[0] = new TextComponentString(TextFormatting.AQUA + "REGION");
        sign.signText[1] = new TextComponentString(TextFormatting.AQUA + regionName);
        sign.signText[2] = new TextComponentString(TextFormatting.AQUA + shortPos(firstPos));
        sign.signText[3] = new TextComponentString(TextFormatting.AQUA + shortPos(secondPos));
        sign.markDirty();
        if (sign.getWorld() != null) {
            IBlockState state = sign.getWorld().getBlockState(sign.getPos());
            sign.getWorld().notifyBlockUpdate(sign.getPos(), state, state, 3);
        }
    }

    private static void printRegionDetails(net.minecraft.entity.player.EntityPlayer player, BotRegionsData.Region region) {
        sendAqua(player, "Region '" + region.name + "': " + formatPos(region.firstPos) + " -> " + formatPos(region.secondPos));
    }

    private static RegionSelection getSelection(net.minecraft.entity.player.EntityPlayer player) {
        RegionSelection selection = REGION_SELECTIONS.get(player.getUniqueID());
        if (selection == null) {
            selection = new RegionSelection();
            REGION_SELECTIONS.put(player.getUniqueID(), selection);
        }
        return selection;
    }

    private static boolean isNamedStick(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.STICK && stack.hasDisplayName();
    }

    private static boolean isSignState(IBlockState state) {
        return state.getBlock() == Blocks.STANDING_SIGN || state.getBlock() == Blocks.WALL_SIGN;
    }

    private static boolean isRegionDeclarationSign(TileEntitySign sign) {
        return "REGION".equalsIgnoreCase(getSignLine(sign, 0));
    }

    private static String getRegionName(TileEntitySign sign) {
        if (!isRegionDeclarationSign(sign)) {
            return null;
        }

        String name = getSignLine(sign, 1);
        return name.isEmpty() ? null : name;
    }

    private static String getFlatSignText(TileEntitySign sign) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            String line = getSignLine(sign, i);
            if (!line.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(line);
            }
        }
        return sb.toString().trim();
    }

    private static String getSignLine(TileEntitySign sign, int index) {
        if (sign == null || index < 0 || index >= sign.signText.length || sign.signText[index] == null) {
            return "";
        }
        return sign.signText[index].getUnformattedText().trim();
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static void sendAqua(net.minecraft.entity.player.EntityPlayer player, String message) {
        player.sendMessage(new TextComponentString(TextFormatting.AQUA + message));
    }

    private static boolean isBotMentioned(String message, String botName) {
        if (botName == null || botName.trim().isEmpty()) {
            return false;
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        String normalizedBotName = "@" + botName.toLowerCase(Locale.ROOT);
        int start = normalizedMessage.indexOf(normalizedBotName);

        while (start >= 0) {
            int end = start + normalizedBotName.length();
            if (end >= normalizedMessage.length() || !Character.isLetterOrDigit(normalizedMessage.charAt(end))) {
                return true;
            }
            start = normalizedMessage.indexOf(normalizedBotName, start + 1);
        }

        return false;
    }

    private static final class RegionSelection {
        private BlockPos firstPos;
        private BlockPos secondPos;

        private void clear() {
            firstPos = null;
            secondPos = null;
        }
    }
}
