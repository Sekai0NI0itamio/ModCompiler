package com.botfriend;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

public final class BotFriendPlayer extends FakePlayer {
    private final FriendManager manager;
    private final FriendRecord record;
    private long tickCounter;
    private double gotoX;
    private double gotoY;
    private double gotoZ;
    private boolean hasGotoTarget;
    private BlockPos mineTarget;
    private String mineNearestNamedBlock;
    private UUID combatTargetId;
    private long lastPositionSyncTick;
    private String mode;
    private int failedPathTicks;

    public BotFriendPlayer(WorldServer world, GameProfile profile, FriendManager manager, FriendRecord record) {
        super(world, profile);
        this.manager = manager;
        this.record = record;
        this.mode = record.getActiveMode();
        this.stepHeight = 1.0F;
        this.experienceLevel = 0;
        this.setHealth(Math.max(1.0F, record.getHealth()));
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4.0D);
        this.capabilities.disableDamage = false;
        if (world.getMinecraftServer() != null) {
            this.connection = new BotFriendNetHandler(world.getMinecraftServer(), this);
        }
        restoreInventory();
    }

    public String getFriendName() {
        return record.getFriendName();
    }

    public String getOwnerName() {
        return record.getOwnerName();
    }

    public FriendRecord getRecord() {
        return record;
    }

    public void initializeSpawn(Vec3d position, EntityPlayerMP owner) {
        setPositionAndRotation(position.x, position.y, position.z, owner.rotationYaw + 180.0F, 0.0F);
        this.renderYawOffset = this.rotationYaw;
        this.rotationYawHead = this.rotationYaw;
        this.rotationPitch = 0.0F;
        record.setDimension(this.dimension);
        record.setX(position.x);
        record.setY(position.y);
        record.setZ(position.z);
        record.setYaw(this.rotationYaw);
        record.setPitch(this.rotationPitch);
        record.setOwnerName(owner.getName());
    }

    public void restorePosition() {
        setPositionAndRotation(record.getX(), record.getY(), record.getZ(), record.getYaw(), record.getPitch());
        this.renderYawOffset = this.rotationYaw;
        this.rotationYawHead = this.rotationYaw;
        this.rotationPitch = record.getPitch();
        this.mode = record.getActiveMode();
    }

    public void tickFriend() {
        tickCounter++;
        if (this.world.isRemote) {
            return;
        }
        if (this.ticksExisted % 20 == 0) {
            heal(0.1F);
        }
        if ("follow".equalsIgnoreCase(mode)) {
            followOwnerInternal(false);
        } else if ("guard".equalsIgnoreCase(mode)) {
            guardOwnerInternal();
        } else if ("goto".equalsIgnoreCase(mode) && hasGotoTarget) {
            moveToward(gotoX + 0.5D, gotoY, gotoZ + 0.5D, 0.32D);
            if (getDistance(gotoX, gotoY, gotoZ) <= 2.0D) {
                hasGotoTarget = false;
                mode = "stay";
                record.setActiveMode(mode);
            }
        }
        if (mineTarget != null || mineNearestNamedBlock != null) {
            tickMining();
        }
        if (combatTargetId != null) {
            tickCombat();
        } else if ("guard".equalsIgnoreCase(mode)) {
            attackNearest(null);
        }
        pickupNearbyItems();
        snapshot();
    }

    public void followOwner() {
        mode = "follow";
        record.setActiveMode(mode);
        hasGotoTarget = false;
        mineTarget = null;
        mineNearestNamedBlock = null;
        combatTargetId = null;
    }

    public void guardOwner() {
        mode = "guard";
        record.setActiveMode(mode);
        hasGotoTarget = false;
    }

    public void stopActions() {
        mode = "stay";
        record.setActiveMode(mode);
        hasGotoTarget = false;
        mineTarget = null;
        mineNearestNamedBlock = null;
        combatTargetId = null;
        failedPathTicks = 0;
    }

    public void setMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if ("follow".equals(normalized)) {
            followOwner();
            return;
        }
        if ("guard".equals(normalized)) {
            guardOwner();
            return;
        }
        if ("stay".equals(normalized) || "idle".equals(normalized)) {
            stopActions();
            return;
        }
        if ("goto".equals(normalized)) {
            this.mode = "goto";
            record.setActiveMode(this.mode);
        }
    }

    public void setGotoTarget(double x, double y, double z) {
        this.gotoX = x;
        this.gotoY = y;
        this.gotoZ = z;
        this.hasGotoTarget = true;
        this.mode = "goto";
        record.setActiveMode(mode);
        this.mineTarget = null;
        this.mineNearestNamedBlock = null;
    }

    public void setMineTarget(BlockPos pos) {
        if (pos == null) {
            return;
        }
        this.mineTarget = pos;
        this.mineNearestNamedBlock = null;
        this.mode = "mine";
        record.setActiveMode(mode);
    }

    public void setMineNearestNamedBlock(String blockName) {
        this.mineNearestNamedBlock = blockName == null ? null : blockName.trim().toLowerCase(Locale.ROOT);
        this.mineTarget = null;
        this.mode = "mine";
        record.setActiveMode(mode);
    }

    public void equipNamedItem(String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return;
        }
        String normalized = itemName.trim().toLowerCase(Locale.ROOT);
        for (int slot = 0; slot < inventory.mainInventory.size(); slot++) {
            ItemStack stack = inventory.mainInventory.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String stackName = stack.getItem().getRegistryName() == null ? "" : stack.getItem().getRegistryName().getResourcePath();
            if (!normalized.equals(stackName)) {
                continue;
            }
            if (stack.getItem() instanceof ItemArmor) {
                EntityEquipmentSlot equipmentSlot = ((ItemArmor) stack.getItem()).armorType;
                setItemStackToSlot(equipmentSlot, stack.copy());
                inventory.mainInventory.set(slot, ItemStack.EMPTY);
                return;
            }
            inventory.currentItem = slot;
            return;
        }
    }

    public void attackNearest(String entityType) {
        EntityLivingBase nearest = null;
        double bestDistance = Double.MAX_VALUE;
        AxisAlignedBB box = getEntityBoundingBox().grow(16.0D);
        List<EntityLivingBase> nearby = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        for (EntityLivingBase candidate : nearby) {
            if (candidate == null || candidate == this || candidate.isDead) {
                continue;
            }
            if (!(candidate instanceof IMob) && entityType == null) {
                continue;
            }
            if (entityType != null && !entityType.trim().isEmpty()) {
                String name = candidate.getName() == null ? "" : candidate.getName().toLowerCase(Locale.ROOT);
                String typeName = candidate.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                String needle = entityType.trim().toLowerCase(Locale.ROOT);
                if (!name.contains(needle) && !typeName.contains(needle)) {
                    continue;
                }
            }
            double distance = getDistanceSq(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = candidate;
            }
        }
        if (nearest != null) {
            this.combatTargetId = nearest.getUniqueID();
            this.mode = "attack";
            record.setActiveMode(mode);
        }
    }

    public void say(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        manager.broadcastFriendChat(this, message.trim());
    }

    public void tellOwner(String message) {
        EntityPlayerMP owner = manager.getOwner(record.getOwnerId());
        if (owner != null) {
            owner.sendMessage(new TextComponentString("[BotFriend] " + message));
        }
    }

    public String describeState() {
        return "mode=" + mode
            + ",pos=(" + format(posX) + "," + format(posY) + "," + format(posZ) + ")"
            + ",health=" + format(getHealth())
            + ",food=" + foodStats.getFoodLevel();
    }

    public String describeInventory() {
        List<String> items = new ArrayList<>();
        for (ItemStack stack : inventory.mainInventory) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == null || stack.getItem().getRegistryName() == null) {
                continue;
            }
            items.add(stack.getItem().getRegistryName().getResourcePath() + " x" + stack.getCount());
        }
        return items.isEmpty() ? "empty" : String.join(", ", items);
    }

    public String describeNearbyEntities() {
        AxisAlignedBB box = getEntityBoundingBox().grow(12.0D);
        List<EntityLivingBase> nearby = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        List<String> names = new ArrayList<>();
        for (EntityLivingBase entity : nearby) {
            if (entity == this || entity.isDead) {
                continue;
            }
            names.add(entity.getName());
        }
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    public String describeNearbyBlocks() {
        BlockPos center = getPosition();
        List<String> blocks = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    Block block = world.getBlockState(pos).getBlock();
                    if (block == Blocks.AIR || block.getRegistryName() == null) {
                        continue;
                    }
                    String name = block.getRegistryName().getResourcePath();
                    if (!blocks.contains(name)) {
                        blocks.add(name);
                    }
                }
            }
        }
        return blocks.isEmpty() ? "none" : String.join(", ", blocks);
    }

    public long getLastPositionSyncTick() {
        return lastPositionSyncTick;
    }

    public void markPositionSynced(long tick) {
        this.lastPositionSyncTick = tick;
    }

    public boolean shouldSelfPrompt() {
        if (combatTargetId != null || mineTarget != null || mineNearestNamedBlock != null || hasGotoTarget) {
            return false;
        }
        return "follow".equalsIgnoreCase(mode)
            || "guard".equalsIgnoreCase(mode)
            || "stay".equalsIgnoreCase(mode);
    }

    public boolean isReadyForSelfPrompt() {
        return tickCounter >= 100L;
    }

    public boolean shouldStayVisibleTo(EntityPlayerMP viewer) {
        return viewer != null
            && viewer.dimension == this.dimension
            && viewer.getDistanceSq(this) <= 128.0D * 128.0D;
    }

    public void snapshot() {
        record.setDimension(this.dimension);
        record.setX(this.posX);
        record.setY(this.posY);
        record.setZ(this.posZ);
        record.setYaw(this.rotationYaw);
        record.setPitch(this.rotationPitch);
        record.setHealth(this.getHealth());
        record.setActiveMode(mode);
        NBTTagList inventoryTag = new NBTTagList();
        inventory.writeToNBT(inventoryTag);
        record.setInventoryData(inventoryTag);
    }

    private void restoreInventory() {
        NBTTagList inventoryTag = record.getInventoryData();
        if (inventoryTag != null) {
            inventory.readFromNBT(inventoryTag);
        }
    }

    private void followOwnerInternal(boolean forceTeleport) {
        EntityPlayerMP owner = manager.getOwner(record.getOwnerId());
        if (owner == null) {
            return;
        }
        record.setOwnerName(owner.getName());
        double distanceSq = getDistanceSq(owner);
        if (distanceSq > 48.0D * 48.0D || forceTeleport) {
            Vec3d position = manager.computeSpawnInFront(owner);
            setPositionAndRotation(position.x, position.y, position.z, owner.rotationYaw + 180.0F, 0.0F);
            failedPathTicks = 0;
            return;
        }
        if (distanceSq > 4.0D * 4.0D) {
            moveToward(owner.posX, owner.posY, owner.posZ, 0.25D);
        } else if (distanceSq < 2.0D * 2.0D) {
            double dx = posX - owner.posX;
            double dz = posZ - owner.posZ;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > 0.01D) {
                motionX = dx / horizontal * 0.15D;
                motionZ = dz / horizontal * 0.15D;
                move(MoverType.SELF, motionX, motionY, motionZ);
            }
        }
    }

    private void guardOwnerInternal() {
        followOwnerInternal(false);
        if (combatTargetId == null) {
            attackNearest(null);
        }
    }

    private void tickMining() {
        if (mineTarget == null && mineNearestNamedBlock != null) {
            mineTarget = findNearestBlock(mineNearestNamedBlock, 24);
            if (mineTarget == null) {
                mineNearestNamedBlock = null;
                mode = "follow";
                record.setActiveMode(mode);
                return;
            }
        }
        if (mineTarget == null) {
            return;
        }
        IBlockState state = world.getBlockState(mineTarget);
        if (state == null || state.getBlock() == Blocks.AIR) {
            mineTarget = null;
            mineNearestNamedBlock = null;
            mode = "follow";
            record.setActiveMode(mode);
            return;
        }
        if (distanceSqToCenter(mineTarget) > 4.0D * 4.0D) {
            moveToward(mineTarget.getX() + 0.5D, mineTarget.getY(), mineTarget.getZ() + 0.5D, 0.28D);
            return;
        }
        swingArm(EnumHand.MAIN_HAND);
        world.destroyBlock(mineTarget, true);
        mineTarget = null;
        mineNearestNamedBlock = null;
        mode = "follow";
        record.setActiveMode(mode);
    }

    private void tickCombat() {
        Entity target = manager.findEntityByUuid(this, combatTargetId);
        if (!(target instanceof EntityLivingBase) || target.isDead) {
            combatTargetId = null;
            if ("attack".equals(mode)) {
                mode = "follow";
                record.setActiveMode(mode);
            }
            return;
        }
        EntityLivingBase living = (EntityLivingBase) target;
        double distanceSq = getDistanceSq(living);
        if (distanceSq > 3.2D * 3.2D) {
            moveToward(living.posX, living.posY, living.posZ, 0.33D);
            return;
        }
        lookAt(living.posX, living.posY + living.getEyeHeight(), living.posZ);
        swingArm(EnumHand.MAIN_HAND);
        attackTargetEntityWithCurrentItem(living);
    }

    private void moveToward(double targetX, double targetY, double targetZ, double speed) {
        lookAt(targetX, targetY + 0.1D, targetZ);
        double dx = targetX - posX;
        double dz = targetZ - posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.001D) {
            motionX = 0.0D;
            motionZ = 0.0D;
            return;
        }
        if (collidedHorizontally && onGround) {
            jump();
        }
        motionX = dx / horizontal * speed;
        motionZ = dz / horizontal * speed;
        if (targetY > posY + 1.0D && onGround) {
            jump();
        }
        if (horizontal < 1.2D) {
            failedPathTicks = 0;
        } else {
            failedPathTicks++;
        }
        move(MoverType.SELF, motionX, motionY, motionZ);
        if (failedPathTicks > 100) {
            setPositionAndUpdate(targetX, targetY, targetZ);
            failedPathTicks = 0;
        }
    }

    private void pickupNearbyItems() {
        AxisAlignedBB box = getEntityBoundingBox().grow(1.5D);
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(this, box);
        for (Entity entity : entities) {
            if (entity instanceof EntityItem) {
                ((EntityItem) entity).onCollideWithPlayer(this);
            }
        }
    }

    private BlockPos findNearestBlock(String blockName, int range) {
        if (blockName == null || blockName.isEmpty()) {
            return null;
        }
        BlockPos origin = getPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -4; dy <= 6; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    Block block = world.getBlockState(pos).getBlock();
                    if (block == Blocks.AIR || block.getRegistryName() == null) {
                        continue;
                    }
                    if (!block.getRegistryName().getResourcePath().equalsIgnoreCase(blockName)) {
                        continue;
                    }
                    double distance = distanceSqToCenter(pos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private void lookAt(double x, double y, double z) {
        double dx = x - posX;
        double dy = y - (posY + getEyeHeight());
        double dz = z - posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        this.rotationYaw = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.rotationPitch = (float) (-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI)));
        this.rotationYawHead = this.rotationYaw;
        this.renderYawOffset = this.rotationYaw;
    }

    private double distanceSqToCenter(BlockPos pos) {
        double dx = pos.getX() + 0.5D - posX;
        double dy = pos.getY() + 0.5D - posY;
        double dz = pos.getZ() + 0.5D - posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
