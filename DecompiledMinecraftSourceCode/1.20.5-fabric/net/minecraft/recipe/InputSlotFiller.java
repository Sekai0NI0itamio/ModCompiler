/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.recipe;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.ArrayList;
import java.util.Iterator;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CraftFailedResponseS2CPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeGridAligner;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class InputSlotFiller<C extends Inventory>
implements RecipeGridAligner<Integer> {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final RecipeMatcher matcher = new RecipeMatcher();
    protected PlayerInventory inventory;
    protected AbstractRecipeScreenHandler<C> handler;

    public InputSlotFiller(AbstractRecipeScreenHandler<C> handler) {
        this.handler = handler;
    }

    public void fillInputSlots(ServerPlayerEntity entity, @Nullable RecipeEntry<? extends Recipe<C>> recipe, boolean craftAll) {
        if (recipe == null || !entity.getRecipeBook().contains(recipe)) {
            return;
        }
        this.inventory = entity.getInventory();
        if (!this.canReturnInputs() && !entity.isCreative()) {
            return;
        }
        this.matcher.clear();
        entity.getInventory().populateRecipeFinder(this.matcher);
        this.handler.populateRecipeFinder(this.matcher);
        if (this.matcher.match(recipe.value(), null)) {
            this.fillInputSlots(recipe, craftAll);
        } else {
            this.returnInputs();
            entity.networkHandler.sendPacket(new CraftFailedResponseS2CPacket(entity.currentScreenHandler.syncId, recipe));
        }
        entity.getInventory().markDirty();
    }

    protected void returnInputs() {
        for (int i = 0; i < this.handler.getCraftingSlotCount(); ++i) {
            if (!this.handler.canInsertIntoSlot(i)) continue;
            ItemStack itemStack = this.handler.getSlot(i).getStack().copy();
            this.inventory.offer(itemStack, false);
            this.handler.getSlot(i).setStackNoCallbacks(itemStack);
        }
        this.handler.clearCraftingSlots();
    }

    protected void fillInputSlots(RecipeEntry<? extends Recipe<C>> recipe, boolean craftAll) {
        int j;
        boolean bl = this.handler.matches(recipe);
        int i = this.matcher.countCrafts(recipe, null);
        if (bl) {
            for (j = 0; j < this.handler.getCraftingHeight() * this.handler.getCraftingWidth() + 1; ++j) {
                ItemStack itemStack;
                if (j == this.handler.getCraftingResultSlotIndex() || (itemStack = this.handler.getSlot(j).getStack()).isEmpty() || Math.min(i, itemStack.getMaxCount()) >= itemStack.getCount() + 1) continue;
                return;
            }
        }
        j = this.getAmountToFill(craftAll, i, bl);
        IntArrayList intList = new IntArrayList();
        if (this.matcher.match(recipe.value(), intList, j)) {
            int k = j;
            IntListIterator intListIterator = intList.iterator();
            while (intListIterator.hasNext()) {
                int m;
                int l = (Integer)intListIterator.next();
                ItemStack itemStack2 = RecipeMatcher.getStackFromId(l);
                if (itemStack2.isEmpty() || (m = itemStack2.getMaxCount()) >= k) continue;
                k = m;
            }
            j = k;
            if (this.matcher.match(recipe.value(), intList, j)) {
                this.returnInputs();
                this.alignRecipeToGrid(this.handler.getCraftingWidth(), this.handler.getCraftingHeight(), this.handler.getCraftingResultSlotIndex(), recipe, intList.iterator(), j);
            }
        }
    }

    @Override
    public void acceptAlignedInput(Iterator<Integer> inputs, int slot, int amount, int gridX, int gridY) {
        Slot slot2 = this.handler.getSlot(slot);
        ItemStack itemStack = RecipeMatcher.getStackFromId(inputs.next());
        if (!itemStack.isEmpty()) {
            for (int i = 0; i < amount; ++i) {
                this.fillInputSlot(slot2, itemStack);
            }
        }
    }

    protected int getAmountToFill(boolean craftAll, int limit, boolean recipeInCraftingSlots) {
        int i = 1;
        if (craftAll) {
            i = limit;
        } else if (recipeInCraftingSlots) {
            i = Integer.MAX_VALUE;
            for (int j = 0; j < this.handler.getCraftingWidth() * this.handler.getCraftingHeight() + 1; ++j) {
                ItemStack itemStack;
                if (j == this.handler.getCraftingResultSlotIndex() || (itemStack = this.handler.getSlot(j).getStack()).isEmpty() || i <= itemStack.getCount()) continue;
                i = itemStack.getCount();
            }
            if (i != Integer.MAX_VALUE) {
                ++i;
            }
        }
        return i;
    }

    protected void fillInputSlot(Slot slot, ItemStack stack) {
        int i = this.inventory.indexOf(stack);
        if (i == -1) {
            return;
        }
        ItemStack itemStack = this.inventory.getStack(i);
        if (itemStack.isEmpty()) {
            return;
        }
        if (itemStack.getCount() > 1) {
            this.inventory.removeStack(i, 1);
        } else {
            this.inventory.removeStack(i);
        }
        if (slot.getStack().isEmpty()) {
            slot.setStackNoCallbacks(itemStack.copyWithCount(1));
        } else {
            slot.getStack().increment(1);
        }
    }

    private boolean canReturnInputs() {
        ArrayList<ItemStack> list = Lists.newArrayList();
        int i = this.getFreeInventorySlots();
        for (int j = 0; j < this.handler.getCraftingWidth() * this.handler.getCraftingHeight() + 1; ++j) {
            ItemStack itemStack;
            if (j == this.handler.getCraftingResultSlotIndex() || (itemStack = this.handler.getSlot(j).getStack().copy()).isEmpty()) continue;
            int k = this.inventory.getOccupiedSlotWithRoomForStack(itemStack);
            if (k == -1 && list.size() <= i) {
                for (ItemStack itemStack2 : list) {
                    if (!ItemStack.areItemsEqual(itemStack2, itemStack) || itemStack2.getCount() == itemStack2.getMaxCount() || itemStack2.getCount() + itemStack.getCount() > itemStack2.getMaxCount()) continue;
                    itemStack2.increment(itemStack.getCount());
                    itemStack.setCount(0);
                    break;
                }
                if (itemStack.isEmpty()) continue;
                if (list.size() < i) {
                    list.add(itemStack);
                    continue;
                }
                return false;
            }
            if (k != -1) continue;
            return false;
        }
        return true;
    }

    private int getFreeInventorySlots() {
        int i = 0;
        for (ItemStack itemStack : this.inventory.main) {
            if (!itemStack.isEmpty()) continue;
            ++i;
        }
        return i;
    }
}

