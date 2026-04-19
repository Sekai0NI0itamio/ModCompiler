package com.bothelpers.entity;

import com.bothelpers.GuiHandler;
import com.bothelpers.Main;
import com.bothelpers.script.BotJobScriptRunner;
import com.bothelpers.script.BotPresenceManager;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumHand;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class EntityBotHelper extends EntityCreature {

    public InventoryBasic botInventory;
    
    // Status tracking for Happiness
    public int daysSinceLastSleep = 0;
    public boolean hasJob = false;
    public boolean isRestingTime = false;
    public float botFoodLevel = 20.0f;
    private boolean initializedDayState = false;
    private boolean lastKnownDaytime = true;

    public EntityBotHelper(World worldIn) {
        super(worldIn);
        this.setSize(0.6F, 1.8F);
        // 41 standard slot sizes: 36 standard inv, 4 armor, 1 offhand
        this.botInventory = new InventoryBasic("BotInventory", false, 41);
        this.setupAI();
        this.enablePersistence();
        this.setCanPickUpLoot(true);
        this.addTag("player");
    }

    private void setupAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIOpenDoor(this, true));
        this.tasks.addTask(2, new EntityAIAvoidEntity(this, EntityMob.class, 8.0F, 0.6D, 0.6D));
        this.tasks.addTask(3, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(4, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0.0D);
    }

    public int getHappiness() {
        int h = 0;
        if (hasFoodInInventory()) h++;
        if (daysSinceLastSleep <= 2) h++;
        if (this.getHealth() >= this.getMaxHealth()) h++;
        if (hasJob) h++;
        if (isRestingTime) h++;
        return Math.min(5, h);
    }

    private boolean hasFoodInInventory() {
        for (int i = 0; i < botInventory.getSizeInventory(); i++) {
            ItemStack stack = botInventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemFood) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (hand == EnumHand.MAIN_HAND) {
            if (this.world.isRemote) {
                // Open purely client-side status GUI
                player.openGui(Main.instance, GuiHandler.BOT_STATUS_GUI_ID, this.world, this.getEntityId(), 0, 0);
            }
            return true;
        }
        return super.processInteract(player, hand);
    }

    @Override
    public boolean canBePushed() { return true; }

    private int getArmSwingAnimationEnd() {
        if (this.isPotionActive(net.minecraft.init.MobEffects.HASTE)) {
            return 6 - (1 + this.getActivePotionEffect(net.minecraft.init.MobEffects.HASTE).getAmplifier());
        } else {
            return this.isPotionActive(net.minecraft.init.MobEffects.MINING_FATIGUE) ? 6 + (1 + this.getActivePotionEffect(net.minecraft.init.MobEffects.MINING_FATIGUE).getAmplifier()) * 2 : 6;
        }
    }

    public void triggerMainHandSwing() {
        if (!this.isSwingInProgress || this.swingProgressInt >= this.getArmSwingAnimationEnd() / 2 || this.swingProgressInt < 0) {
            this.swingProgressInt = -1;
            this.isSwingInProgress = true;
            this.swingingHand = EnumHand.MAIN_HAND;
            if (!this.world.isRemote) {
                this.world.setEntityState(this, (byte) 0);
            }
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!this.world.isRemote) {
            BotPresenceManager.tick(this);
            collectNearbyItems();
            updateTimeBasedEvents();
            BotJobScriptRunner.tick(this);
        }
    }

    @Override
    public void setDead() {
        BotJobScriptRunner.clear(this);
        BotPresenceManager.release(this);
        super.setDead();
    }

    private void updateTimeBasedEvents() {
        boolean isDaytime = this.world.isDaytime();

        if (!this.initializedDayState) {
            this.initializedDayState = true;
            this.lastKnownDaytime = isDaytime;
            return;
        }

        if (this.lastKnownDaytime != isDaytime) {
            this.lastKnownDaytime = isDaytime;
            this.isRestingTime = !isDaytime;
            BotJobScriptRunner.runEvent(this, null, isDaytime ? "When Day Starts" : "When Day Ends");
        }
    }

    private void collectNearbyItems() {
        if (this.ticksExisted % 5 != 0) {
            return;
        }

        List<EntityItem> nearbyItems = this.world.getEntitiesWithinAABB(EntityItem.class, this.getEntityBoundingBox().grow(1.0D, 0.5D, 1.0D));
        for (EntityItem entityItem : nearbyItems) {
            collectEntityItem(entityItem);
        }
    }

    public void collectDropsAt(BlockPos pos) {
        if (pos == null || this.world.isRemote) {
            return;
        }

        Block blockBelow = this.world.getBlockState(pos.down()).getBlock();
        if (blockBelow == Blocks.HOPPER) {
            return;
        }

        AxisAlignedBB area = new AxisAlignedBB(pos).grow(0.9D, 0.9D, 0.9D);
        List<EntityItem> nearbyItems = this.world.getEntitiesWithinAABB(EntityItem.class, area);
        for (EntityItem entityItem : nearbyItems) {
            collectEntityItem(entityItem);
        }
    }

    public ItemStack storeItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = insertIntoInventory(stack.copy());
        this.botInventory.markDirty();
        return remaining;
    }

    private void collectEntityItem(EntityItem entityItem) {
        if (entityItem == null || !entityItem.isEntityAlive()) {
            return;
        }

        ItemStack stack = entityItem.getItem();
        if (stack.isEmpty()) {
            return;
        }

        int originalCount = stack.getCount();
        ItemStack remaining = insertIntoInventory(stack.copy());
        int pickedUp = originalCount - remaining.getCount();

        if (pickedUp <= 0) {
            return;
        }

        if (remaining.isEmpty()) {
            entityItem.setDead();
        } else {
            entityItem.setItem(remaining);
        }

        this.botInventory.markDirty();
        this.onItemPickup(entityItem, pickedUp);
        this.world.playSound(
            null,
            this.posX,
            this.posY,
            this.posZ,
            SoundEvents.ENTITY_ITEM_PICKUP,
            this.getSoundCategory(),
            0.2F,
            ((this.rand.nextFloat() - this.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F
        );
    }

    private ItemStack insertIntoInventory(ItemStack stack) {
        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < 36 && !remaining.isEmpty(); slot++) {
            ItemStack existing = this.botInventory.getStackInSlot(slot);
            if (existing.isEmpty()
                || !ItemStack.areItemsEqual(existing, remaining)
                || !ItemStack.areItemStackTagsEqual(existing, remaining)) {
                continue;
            }

            int maxStackSize = Math.min(existing.getMaxStackSize(), this.botInventory.getInventoryStackLimit());
            int space = maxStackSize - existing.getCount();
            if (space <= 0) {
                continue;
            }

            int moved = Math.min(space, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
            this.botInventory.setInventorySlotContents(slot, existing);
        }

        for (int slot = 0; slot < 36 && !remaining.isEmpty(); slot++) {
            ItemStack existing = this.botInventory.getStackInSlot(slot);
            if (!existing.isEmpty()) {
                continue;
            }

            int moved = Math.min(Math.min(remaining.getMaxStackSize(), this.botInventory.getInventoryStackLimit()), remaining.getCount());
            ItemStack movedStack = remaining.copy();
            movedStack.setCount(moved);
            this.botInventory.setInventorySlotContents(slot, movedStack);
            remaining.shrink(moved);
        }

        return remaining;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        NBTTagList nbttaglist = new NBTTagList();
        for (int i = 0; i < this.botInventory.getSizeInventory(); ++i) {
            ItemStack itemstack = this.botInventory.getStackInSlot(i);
            if (!itemstack.isEmpty()) {
                NBTTagCompound nbttagcompound = new NBTTagCompound();
                nbttagcompound.setByte("Slot", (byte)i);
                itemstack.writeToNBT(nbttagcompound);
                nbttaglist.appendTag(nbttagcompound);
            }
        }
        compound.setTag("BotInventory", nbttaglist);
        compound.setInteger("DaysSinceSleep", daysSinceLastSleep);
        compound.setBoolean("HasJob", hasJob);
        compound.setBoolean("IsResting", isRestingTime);
        compound.setFloat("FoodLevel", botFoodLevel);
        compound.setBoolean("InitializedDayState", initializedDayState);
        compound.setBoolean("LastKnownDaytime", lastKnownDaytime);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        NBTTagList nbttaglist = compound.getTagList("BotInventory", 10);
        for (int i = 0; i < nbttaglist.tagCount(); ++i) {
            NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
            int j = nbttagcompound.getByte("Slot") & 255;
            if (j >= 0 && j < this.botInventory.getSizeInventory()) {
                this.botInventory.setInventorySlotContents(j, new ItemStack(nbttagcompound));
            }
        }
        if (compound.hasKey("DaysSinceSleep")) daysSinceLastSleep = compound.getInteger("DaysSinceSleep");
        if (compound.hasKey("HasJob")) hasJob = compound.getBoolean("HasJob");
        if (compound.hasKey("IsResting")) isRestingTime = compound.getBoolean("IsResting");
        if (compound.hasKey("FoodLevel")) botFoodLevel = compound.getFloat("FoodLevel");
        if (compound.hasKey("InitializedDayState")) initializedDayState = compound.getBoolean("InitializedDayState");
        if (compound.hasKey("LastKnownDaytime")) lastKnownDaytime = compound.getBoolean("LastKnownDaytime");
        this.addTag("player");
    }
}
