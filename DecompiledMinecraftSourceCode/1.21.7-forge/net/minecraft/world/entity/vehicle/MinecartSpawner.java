package net.minecraft.world.entity.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class MinecartSpawner extends AbstractMinecart {
    private final BaseSpawner spawner = new BaseSpawner() {
        @Override
        public void broadcastEvent(Level p_150342_, BlockPos p_150343_, int p_150344_) {
            p_150342_.broadcastEntityEvent(MinecartSpawner.this, (byte)p_150344_);
        }

        @Override
        @org.jetbrains.annotations.Nullable
        public net.minecraft.world.entity.Entity getSpawnerEntity() {
            return MinecartSpawner.this;
        }
    };
    private final Runnable ticker;

    public MinecartSpawner(EntityType<? extends MinecartSpawner> p_38623_, Level p_38624_) {
        super(p_38623_, p_38624_);
        this.ticker = this.createTicker(p_38624_);
    }

    @Override
    protected Item getDropItem() {
        return Items.MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.MINECART);
    }

    private Runnable createTicker(Level p_150335_) {
        return p_150335_ instanceof ServerLevel
            ? () -> this.spawner.serverTick((ServerLevel)p_150335_, this.blockPosition())
            : () -> this.spawner.clientTick(p_150335_, this.blockPosition());
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.SPAWNER.defaultBlockState();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput p_406268_) {
        super.readAdditionalSaveData(p_406268_);
        this.spawner.load(this.level(), this.blockPosition(), p_406268_);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput p_408866_) {
        super.addAdditionalSaveData(p_408866_);
        this.spawner.save(p_408866_);
    }

    @Override
    public void handleEntityEvent(byte p_38631_) {
        this.spawner.onEventTriggered(this.level(), p_38631_);
    }

    @Override
    public void tick() {
        super.tick();
        this.ticker.run();
    }

    public BaseSpawner getSpawner() {
        return this.spawner;
    }
}
