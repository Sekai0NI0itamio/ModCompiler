package com.bothelpers.entity;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.nbt.NBTTagCompound;

public class EntityBotHelper extends EntityCreature {

    public InventoryBasic botInventory;
    public int happiness = 0;

    public EntityBotHelper(World worldIn) {
        super(worldIn);
        this.setSize(0.6F, 1.8F);
        this.botInventory = new InventoryBasic("BotInventory", false, 36);
        this.setupAI();
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
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D); // Player health
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D); 
    }

    @Override
    protected boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (!this.world.isRemote) {
            // Setup stub for the Right Click GUI
            // player.openGui(BotHelpers.instance, GUI_ID, this.world, this.getEntityId(), 0, 0);
            player.sendMessage(new net.minecraft.util.text.TextComponentString("<" + this.getCustomNameTag() + "> Accessing my configuration..."));
        }
        return true;
    }

    @Override
    public boolean canBePushed() {
        return true;
    }
}