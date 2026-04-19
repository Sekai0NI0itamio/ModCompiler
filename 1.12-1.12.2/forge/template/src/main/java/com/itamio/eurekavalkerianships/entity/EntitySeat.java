package com.itamio.eurekavalkerianships.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.block.state.IBlockState;
import com.itamio.eurekavalkerianships.block.BlockShipHelm;

public class EntitySeat extends Entity {

    private BlockPos helmPos;

    public EntitySeat(World worldIn) {
        super(worldIn);
        this.setSize(0.0F, 0.0F);
    }

    public EntitySeat(World worldIn, BlockPos pos) {
        this(worldIn);
        this.helmPos = pos;
        this.setPosition(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!this.world.isRemote) {
            if (this.getPassengers().isEmpty() || this.world.isAirBlock(new BlockPos(this))) {
                this.setDead();
            }
        }
    }

    @Override
    public double getMountedYOffset() {
        return 0.0D; // Adjust height as necessary
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox() {
        return null;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        if (compound.hasKey("HelmX")) {
            this.helmPos = new BlockPos(compound.getInteger("HelmX"), compound.getInteger("HelmY"), compound.getInteger("HelmZ"));
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        if (this.helmPos != null) {
            compound.setInteger("HelmX", this.helmPos.getX());
            compound.setInteger("HelmY", this.helmPos.getY());
            compound.setInteger("HelmZ", this.helmPos.getZ());
        }
    }

    public BlockPos getHelmPos() {
        return helmPos;
    }
}
