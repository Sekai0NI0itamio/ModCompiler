/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Narratable;

@Environment(value=EnvType.CLIENT)
public interface Selectable
extends Narratable {
    public SelectionType getType();

    default public boolean isNarratable() {
        return true;
    }

    @Environment(value=EnvType.CLIENT)
    public static enum SelectionType {
        NONE,
        HOVERED,
        FOCUSED;


        public boolean isFocused() {
            return this == FOCUSED;
        }
    }
}

