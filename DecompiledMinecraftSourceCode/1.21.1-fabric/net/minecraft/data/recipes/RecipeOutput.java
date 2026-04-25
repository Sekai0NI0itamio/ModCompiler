package net.minecraft.data.recipes;

import net.fabricmc.fabric.api.datagen.v1.recipe.FabricRecipeExporter;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

public interface RecipeOutput extends FabricRecipeExporter {
	void accept(ResourceLocation resourceLocation, Recipe<?> recipe, @Nullable AdvancementHolder advancementHolder);

	Advancement.Builder advancement();
}
