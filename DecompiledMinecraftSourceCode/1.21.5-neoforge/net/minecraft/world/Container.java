package net.minecraft.world;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface Container extends Clearable, Iterable<ItemStack> {
    float DEFAULT_DISTANCE_BUFFER = 4.0F;

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int p_18941_);

    ItemStack removeItem(int p_18942_, int p_18943_);

    ItemStack removeItemNoUpdate(int p_18951_);

    void setItem(int p_18944_, ItemStack p_18945_);

    default int getMaxStackSize() {
        return 99;
    }

    default int getMaxStackSize(ItemStack p_329589_) {
        return Math.min(this.getMaxStackSize(), p_329589_.getMaxStackSize());
    }

    void setChanged();

    boolean stillValid(Player p_18946_);

    default void startOpen(Player p_18955_) {
    }

    default void stopOpen(Player p_18954_) {
    }

    default boolean canPlaceItem(int p_18952_, ItemStack p_18953_) {
        return true;
    }

    default boolean canTakeItem(Container p_273520_, int p_272681_, ItemStack p_273702_) {
        return true;
    }

    default int countItem(Item p_18948_) {
        int i = 0;

        for (ItemStack itemstack : this) {
            if (itemstack.getItem().equals(p_18948_)) {
                i += itemstack.getCount();
            }
        }

        return i;
    }

    default boolean hasAnyOf(Set<Item> p_18950_) {
        return this.hasAnyMatching(p_216873_ -> !p_216873_.isEmpty() && p_18950_.contains(p_216873_.getItem()));
    }

    default boolean hasAnyMatching(Predicate<ItemStack> p_216875_) {
        for (ItemStack itemstack : this) {
            if (p_216875_.test(itemstack)) {
                return true;
            }
        }

        return false;
    }

    static boolean stillValidBlockEntity(BlockEntity p_273154_, Player p_273222_) {
        return stillValidBlockEntity(p_273154_, p_273222_, 4.0F);
    }

    static boolean stillValidBlockEntity(BlockEntity p_272877_, Player p_272670_, float p_328395_) {
        Level level = p_272877_.getLevel();
        BlockPos blockpos = p_272877_.getBlockPos();
        if (level == null) {
            return false;
        } else {
            return level.getBlockEntity(blockpos) != p_272877_ ? false : p_272670_.canInteractWithBlock(blockpos, p_328395_);
        }
    }

    @Override
    default Iterator<ItemStack> iterator() {
        return new Container.ContainerIterator(this);
    }

    public static class ContainerIterator implements Iterator<ItemStack> {
        private final Container container;
        private int index;
        private final int size;

        public ContainerIterator(Container p_396630_) {
            this.container = p_396630_;
            this.size = p_396630_.getContainerSize();
        }

        @Override
        public boolean hasNext() {
            return this.index < this.size;
        }

        public ItemStack next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            } else {
                return this.container.getItem(this.index++);
            }
        }
    }
}
