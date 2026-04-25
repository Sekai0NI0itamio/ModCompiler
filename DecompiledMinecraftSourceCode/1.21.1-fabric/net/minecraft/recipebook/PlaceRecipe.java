package net.minecraft.recipebook;

import java.util.Iterator;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

public interface PlaceRecipe<T> {
	default void placeRecipe(int i, int j, int k, RecipeHolder<?> recipeHolder, Iterator<T> iterator, int l) {
		int m = i;
		int n = j;
		if (recipeHolder.value() instanceof ShapedRecipe shapedRecipe) {
			m = shapedRecipe.getWidth();
			n = shapedRecipe.getHeight();
		}

		int o = 0;

		for (int p = 0; p < j; p++) {
			if (o == k) {
				o++;
			}

			boolean bl = n < j / 2.0F;
			int q = Mth.floor(j / 2.0F - n / 2.0F);
			if (bl && q > p) {
				o += i;
				p++;
			}

			for (int r = 0; r < i; r++) {
				if (!iterator.hasNext()) {
					return;
				}

				bl = m < i / 2.0F;
				q = Mth.floor(i / 2.0F - m / 2.0F);
				int s = m;
				boolean bl2 = r < m;
				if (bl) {
					s = q + m;
					bl2 = q <= r && r < q + m;
				}

				if (bl2) {
					this.addItemToSlot((T)iterator.next(), o, l, r, p);
				} else if (s == r) {
					o += i - r;
					break;
				}

				o++;
			}
		}
	}

	void addItemToSlot(T object, int i, int j, int k, int l);
}
