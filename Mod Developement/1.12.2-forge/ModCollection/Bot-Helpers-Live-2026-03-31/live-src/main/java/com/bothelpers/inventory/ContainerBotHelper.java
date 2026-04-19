package com.bothelpers.inventory;

import com.bothelpers.entity.EntityBotHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerBotHelper extends Container {
    private static final int BOT_SPECIAL_SLOT_COUNT = 5;
    private static final int BOT_MAIN_SLOT_START = BOT_SPECIAL_SLOT_COUNT;
    private static final int BOT_SLOT_COUNT = 41;
    private static final int PLAYER_SLOT_START = BOT_SLOT_COUNT;

    private final EntityBotHelper bot;

    public ContainerBotHelper(InventoryPlayer playerInventory, EntityBotHelper botHelper) {
        this.bot = botHelper;
        this.bot.botInventory.openInventory(playerInventory.player);

        this.addSlotToContainer(new Slot(this.bot.botInventory, 39, 8, 30));
        this.addSlotToContainer(new Slot(this.bot.botInventory, 38, 26, 30));
        this.addSlotToContainer(new Slot(this.bot.botInventory, 37, 44, 30));
        this.addSlotToContainer(new Slot(this.bot.botInventory, 36, 62, 30));
        this.addSlotToContainer(new Slot(this.bot.botInventory, 40, 98, 30));

        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                this.addSlotToContainer(new Slot(this.bot.botInventory, 9 + r * 9 + c, 8 + c * 18, 64 + r * 18));
            }
        }

        for (int c = 0; c < 9; ++c) {
            this.addSlotToContainer(new Slot(this.bot.botInventory, c, 8 + c * 18, 134));
        }

        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlotToContainer(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 166 + l * 18));
            }
        }

        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlotToContainer(new Slot(playerInventory, i1, 8 + i1 * 18, 224));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return this.bot.botInventory.isUsableByPlayer(playerIn) && this.bot.isEntityAlive() && this.bot.getDistance(playerIn) < 8.0F;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        this.bot.botInventory.closeInventory(playerIn);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slot = index >= 0 && index < this.inventorySlots.size() ? this.inventorySlots.get(index) : null;
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getStack();
        ItemStack original = slotStack.copy();

        if (index < BOT_SLOT_COUNT) {
            if (!this.mergeItemStack(slotStack, PLAYER_SLOT_START, this.inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.mergeItemStack(slotStack, BOT_MAIN_SLOT_START, BOT_SLOT_COUNT, false)
                && !this.mergeItemStack(slotStack, 0, BOT_SPECIAL_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        if (slotStack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(playerIn, slotStack);
        return original;
    }
}
