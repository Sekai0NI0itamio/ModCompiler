/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.entity;

import net.minecraft.inventory.Inventory;
import net.minecraft.util.annotation.Debug;

public interface InventoryOwner {
    @Debug
    public Inventory getInventory();
}

