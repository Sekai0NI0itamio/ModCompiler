package com.botfriend;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class FriendRecord {
    private UUID ownerId;
    private String ownerName;
    private UUID friendId;
    private String friendName;
    private int dimension;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private float health;
    private String activeMode;
    private String memorySummary;
    private String goalPrompt;
    private boolean selfCodingEnabled;
    private NBTTagList inventoryData;

    public FriendRecord(UUID ownerId, String ownerName, UUID friendId, String friendName) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.friendId = friendId;
        this.friendName = friendName;
        this.dimension = 0;
        this.health = 20.0F;
        this.activeMode = BotFriendConfig.profile.defaultMode;
        this.memorySummary = "";
        this.goalPrompt = "";
        this.inventoryData = new NBTTagList();
    }

    public static FriendRecord fromTag(NBTTagCompound tag) {
        FriendRecord record = new FriendRecord(
            UUID.fromString(tag.getString("ownerId")),
            tag.getString("ownerName"),
            UUID.fromString(tag.getString("friendId")),
            tag.getString("friendName")
        );
        record.dimension = tag.getInteger("dimension");
        record.x = tag.getDouble("x");
        record.y = tag.getDouble("y");
        record.z = tag.getDouble("z");
        record.yaw = tag.getFloat("yaw");
        record.pitch = tag.getFloat("pitch");
        record.health = tag.hasKey("health") ? tag.getFloat("health") : 20.0F;
        record.activeMode = tag.getString("activeMode");
        record.memorySummary = tag.getString("memorySummary");
        record.goalPrompt = tag.getString("goalPrompt");
        record.selfCodingEnabled = tag.getBoolean("selfCodingEnabled");
        record.inventoryData = tag.getTagList("inventory", 10);
        return record;
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("ownerId", ownerId.toString());
        tag.setString("ownerName", ownerName == null ? "" : ownerName);
        tag.setString("friendId", friendId.toString());
        tag.setString("friendName", friendName);
        tag.setInteger("dimension", dimension);
        tag.setDouble("x", x);
        tag.setDouble("y", y);
        tag.setDouble("z", z);
        tag.setFloat("yaw", yaw);
        tag.setFloat("pitch", pitch);
        tag.setFloat("health", health);
        tag.setString("activeMode", activeMode == null ? "" : activeMode);
        tag.setString("memorySummary", memorySummary == null ? "" : memorySummary);
        tag.setString("goalPrompt", goalPrompt == null ? "" : goalPrompt);
        tag.setBoolean("selfCodingEnabled", selfCodingEnabled);
        tag.setTag("inventory", inventoryData == null ? new NBTTagList() : inventoryData.copy());
        return tag;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public UUID getFriendId() {
        return friendId;
    }

    public String getFriendName() {
        return friendName;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public String getActiveMode() {
        return activeMode;
    }

    public void setActiveMode(String activeMode) {
        this.activeMode = activeMode;
    }

    public String getMemorySummary() {
        return memorySummary == null ? "" : memorySummary;
    }

    public void setMemorySummary(String memorySummary) {
        this.memorySummary = memorySummary == null ? "" : memorySummary;
    }

    public String getGoalPrompt() {
        return goalPrompt == null ? "" : goalPrompt;
    }

    public void setGoalPrompt(String goalPrompt) {
        this.goalPrompt = goalPrompt == null ? "" : goalPrompt;
    }

    public boolean isSelfCodingEnabled() {
        return selfCodingEnabled;
    }

    public void setSelfCodingEnabled(boolean selfCodingEnabled) {
        this.selfCodingEnabled = selfCodingEnabled;
    }

    public NBTTagList getInventoryData() {
        return inventoryData == null ? new NBTTagList() : inventoryData.copy();
    }

    public void setInventoryData(NBTTagList inventoryData) {
        this.inventoryData = inventoryData == null ? new NBTTagList() : inventoryData.copy();
    }
}
