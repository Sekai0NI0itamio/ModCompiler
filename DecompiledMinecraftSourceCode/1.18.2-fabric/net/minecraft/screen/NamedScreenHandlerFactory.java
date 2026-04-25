/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.screen;

import net.minecraft.screen.ScreenHandlerFactory;
import net.minecraft.text.Text;

public interface NamedScreenHandlerFactory
extends ScreenHandlerFactory {
    /**
     * Returns the title of this screen handler; will be a part of the open
     * screen packet sent to the client.
     */
    public Text getDisplayName();
}

