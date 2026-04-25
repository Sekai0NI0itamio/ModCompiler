/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.gui.screen.recipebook;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;

@Environment(value=EnvType.CLIENT)
public interface RecipeBookProvider {
    public void refreshRecipeBook();

    public RecipeBookWidget getRecipeBookWidget();
}

