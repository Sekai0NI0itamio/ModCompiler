package io.itamio.aipoweredcompanionship;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;

import java.util.*;

public final class CompanionEntity extends FakePlayer {
    private static final GameProfile PROFILE = new GameProfile(
        UUID.nameUUIDFromBytes("Companion".getBytes()), "Companion");

    private final String companionName;
    private final CompanionRecord record;
    private final CompanionManager manager;
    private long tickCounter;

    private int cachedFloorX, cachedFloorY, cachedFloorZ;
    private String cachedBlockScan = "";
    private boolean blockScanDirty = true;

    public CompanionEntity(WorldServer world, String name, UUID ownerUuid, CompanionManager manager) {
        super(world, PROFILE);
        this.companionName = name;
        this.record = new CompanionRecord(name, ownerUuid);
        this.manager = manager;
        this.cachedFloorX = Integer.MIN_VALUE;
        this.cachedFloorY = Integer.MIN_VALUE;
        this.cachedFloorZ = Integer.MIN_VALUE;
    }

    public String getCompanionName() {
        return companionName;
    }

    public CompanionRecord getRecord() {
        return record;
    }

    public CompanionManager getManager() {
        return manager;
    }

    public long getTickCounter() {
        return tickCounter;
    }

    public void companionTick() {
        tickCounter++;
        if (tickCounter % 4L == 0L) {
            collectNearbyItems();
        }
    }

    public String scanBlocks(int radius) {
        int fx = MathHelper.floor(posX);
        int fy = MathHelper.floor(posY);
        int fz = MathHelper.floor(posZ);
        if (fx == cachedFloorX && fy == cachedFloorY && fz == cachedFloorZ && !blockScanDirty) {
            return cachedBlockScan;
        }
        cachedFloorX = fx;
        cachedFloorY = fy;
        cachedFloorZ = fz;
        blockScanDirty = false;

        BlockPos center = new BlockPos(fx, fy, fz);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk == null) continue;
                    Block block = chunk.getBlockState(pos).getBlock();
                    if (block == Blocks.AIR) continue;
                    String name = Block.REGISTRY.getNameForObject(block).toString();
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        cachedBlockScan = sb.toString();
        return cachedBlockScan;
    }

    public String scanInventory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(stack.getItem().getRegistryName())
                    .append('x').append(stack.getCount());
                if (stack.isItemStackDamageable()) {
                    sb.append(" dmg=").append(stack.getItemDamage());
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "empty";
    }

    public String retrieveTerrain(int radius) {
        int fx = MathHelper.floor(posX);
        int fy = MathHelper.floor(posY);
        int fz = MathHelper.floor(posZ);
        BlockPos center = new BlockPos(fx, fy, fz);
        StringBuilder sb = new StringBuilder();
        sb.append("Terrain around (").append(fx).append(',').append(fy).append(',').append(fz).append("):\n");
        int sample = Math.max(1, radius / 3);
        for (int dz = -radius; dz <= radius; dz += sample) {
            for (int dx = -radius; dx <= radius; dx += sample) {
                int topY = world.getHeight(center.getX() + dx, center.getZ() + dz) - 1;
                Chunk chunk = world.getChunkProvider().getLoadedChunk((center.getX() + dx) >> 4, (center.getZ() + dz) >> 4);
                String blockName = "air";
                if (chunk != null && topY >= 0) {
                    Block top = chunk.getBlockState(new BlockPos(center.getX() + dx, topY, center.getZ() + dz)).getBlock();
                    blockName = Block.REGISTRY.getNameForObject(top).toString();
                }
                sb.append('(').append(dx).append(',').append(topY - fy).append(',').append(dz).append(')').append(blockName).append('\n');
            }
        }
        return sb.toString();
    }

    public String dig(int rx, int ry, int rz) {
        BlockPos target = new BlockPos(MathHelper.floor(posX) + rx, MathHelper.floor(posY) + ry, MathHelper.floor(posZ) + rz);
        double distSq = getDistanceSq(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distSq > 5.0 * 5.0) {
            Vec3d dir = new Vec3d(target.getX() + 0.5 - posX, 0, target.getZ() + 0.5 - posY);
            double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (len > 0.5) {
                double moveX = dir.x / len * Math.min(len, 2.0);
                double moveZ = dir.z / len * Math.min(len, 2.0);
                setPosition(posX + moveX * 0.1, posY, posZ + moveZ * 0.1);
            }
            return "Moving to block at (" + target.getX() + "," + target.getY() + "," + target.getZ() + ")";
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(target.getX() >> 4, target.getZ() >> 4);
        if (chunk == null) return "Chunk not loaded.";
        Block block = chunk.getBlockState(target).getBlock();
        if (block == Blocks.AIR) return "Block is air.";
        String blockName = Block.REGISTRY.getNameForObject(block).toString();
        boolean broken = world.destroyBlock(target, true);
        blockScanDirty = true;
        return broken ? "Dug " + blockName + " at (" + target.getX() + "," + target.getY() + "," + target.getZ() + ")" : "Failed to dig " + blockName;
    }

    public String placeSlot(int hotbarSlot, int rx, int ry, int rz) {
        BlockPos target = new BlockPos(MathHelper.floor(posX) + rx, MathHelper.floor(posY) + ry, MathHelper.floor(posZ) + rz);
        if (getDistanceSq(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 5.0 * 5.0) {
            setPosition(posX + (target.getX() + 0.5 - posX) * 0.1, posY, posZ + (target.getZ() + 0.5 - posZ) * 0.1);
            return "Moving to placement at (" + target.getX() + "," + target.getY() + "," + target.getZ() + ")";
        }
        if (hotbarSlot < 0 || hotbarSlot > 8) return "Invalid hotbar slot (0-8).";
        int prevSlot = inventory.currentItem;
        inventory.currentItem = hotbarSlot;
        ItemStack held = getHeldItemMainhand();
        if (held.isEmpty()) {
            inventory.currentItem = prevSlot;
            return "Hotbar slot " + hotbarSlot + " is empty.";
        }
        EnumHand hand = EnumHand.MAIN_HAND;
        net.minecraft.item.ItemBlock itemBlock = held.getItem() instanceof net.minecraft.item.ItemBlock ? (net.minecraft.item.ItemBlock) held.getItem() : null;
        if (itemBlock == null) {
            inventory.currentItem = prevSlot;
            return "Item in slot " + hotbarSlot + " is not a placeable block.";
        }
        net.minecraft.util.EnumFacing facing = net.minecraft.util.EnumFacing.UP;
        BlockPos placePos = target.down();
        net.minecraft.util.EnumActionResult result = ForgeHooks.onPlaceItemIntoWorld(held, this, world, placePos, facing, 0.5F, 1.0F, 0.5F, hand);
        inventory.currentItem = prevSlot;
        blockScanDirty = true;
        return result == net.minecraft.util.EnumActionResult.SUCCESS
            ? "Placed block at (" + target.getX() + "," + target.getY() + "," + target.getZ() + ")"
            : "Failed to place block.";
    }

    public String placeByName(String blockName, int rx, int ry, int rz) {
        int slot = findItemInInventory(blockName);
        if (slot < 0) return "No " + blockName + " found in inventory.";
        return placeSlot(slot, rx, ry, rz);
    }

    private int findItemInInventory(String name) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String regName = String.valueOf(stack.getItem().getRegistryName());
                if (regName.equalsIgnoreCase(name) || regName.endsWith(":" + name)) {
                    return i < 9 ? i : -1;
                }
            }
        }
        return -1;
    }

    private void collectNearbyItems() {
        double range = 3.0;
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class,
            new AxisAlignedBB(posX - range, posY - range, posZ - range, posX + range, posY + range, posZ + range));
        for (EntityItem item : items) {
            if (item.isDead) continue;
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;
            if (inventory.addItemStackToInventory(stack)) {
                item.setDead();
            }
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        manager.onCompanionDeath(this);
    }

    public void invalidateBlockScan() {
        blockScanDirty = true;
    }
}
