/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.recipe;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.dynamic.Codecs;

public record RawShapedRecipe(int width, int height, DefaultedList<Ingredient> ingredients, Optional<Data> data) {
    private static final int MAX_WIDTH_AND_HEIGHT = 3;
    public static final MapCodec<RawShapedRecipe> CODEC = Data.CODEC.flatXmap(RawShapedRecipe::fromData, recipe -> recipe.data().map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Cannot encode unpacked recipe")));
    public static final PacketCodec<RegistryByteBuf, RawShapedRecipe> PACKET_CODEC = PacketCodec.of(RawShapedRecipe::writeToBuf, RawShapedRecipe::readFromBuf);

    public static RawShapedRecipe create(Map<Character, Ingredient> key, String ... pattern) {
        return RawShapedRecipe.create(key, List.of(pattern));
    }

    public static RawShapedRecipe create(Map<Character, Ingredient> key, List<String> pattern) {
        Data data = new Data(key, pattern);
        return RawShapedRecipe.fromData(data).getOrThrow();
    }

    private static DataResult<RawShapedRecipe> fromData(Data data) {
        String[] strings = RawShapedRecipe.removePadding(data.pattern);
        int i = strings[0].length();
        int j = strings.length;
        DefaultedList<Ingredient> defaultedList = DefaultedList.ofSize(i * j, Ingredient.EMPTY);
        CharArraySet charSet = new CharArraySet(data.key.keySet());
        for (int k = 0; k < strings.length; ++k) {
            String string = strings[k];
            for (int l = 0; l < string.length(); ++l) {
                Ingredient ingredient;
                char c = string.charAt(l);
                Ingredient ingredient2 = ingredient = c == ' ' ? Ingredient.EMPTY : data.key.get(Character.valueOf(c));
                if (ingredient == null) {
                    return DataResult.error(() -> "Pattern references symbol '" + c + "' but it's not defined in the key");
                }
                charSet.remove(c);
                defaultedList.set(l + i * k, ingredient);
            }
        }
        if (!charSet.isEmpty()) {
            return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + String.valueOf(charSet));
        }
        return DataResult.success(new RawShapedRecipe(i, j, defaultedList, Optional.of(data)));
    }

    /**
     * Removes empty space from around the recipe pattern.
     * 
     * <p>Turns patterns such as:
     * <pre>
     * {@code
     * "   o"
     * "   a"
     * "    "
     * }
     * </pre>
     * Into:
     * <pre>
     * {@code
     * "o"
     * "a"
     * }
     * </pre>
     * 
     * @return a new recipe pattern with all leading and trailing empty rows/columns removed
     */
    @VisibleForTesting
    static String[] removePadding(List<String> pattern) {
        int i = Integer.MAX_VALUE;
        int j = 0;
        int k = 0;
        int l = 0;
        for (int m = 0; m < pattern.size(); ++m) {
            String string = pattern.get(m);
            i = Math.min(i, RawShapedRecipe.findFirstSymbol(string));
            int n = RawShapedRecipe.findLastSymbol(string);
            j = Math.max(j, n);
            if (n < 0) {
                if (k == m) {
                    ++k;
                }
                ++l;
                continue;
            }
            l = 0;
        }
        if (pattern.size() == l) {
            return new String[0];
        }
        String[] strings = new String[pattern.size() - l - k];
        for (int o = 0; o < strings.length; ++o) {
            strings[o] = pattern.get(o + k).substring(i, j + 1);
        }
        return strings;
    }

    private static int findFirstSymbol(String line) {
        int i;
        for (i = 0; i < line.length() && line.charAt(i) == ' '; ++i) {
        }
        return i;
    }

    private static int findLastSymbol(String line) {
        int i;
        for (i = line.length() - 1; i >= 0 && line.charAt(i) == ' '; --i) {
        }
        return i;
    }

    public boolean matches(RecipeInputInventory inventory) {
        for (int i = 0; i <= inventory.getWidth() - this.width; ++i) {
            for (int j = 0; j <= inventory.getHeight() - this.height; ++j) {
                if (this.matches(inventory, i, j, true)) {
                    return true;
                }
                if (!this.matches(inventory, i, j, false)) continue;
                return true;
            }
        }
        return false;
    }

    private boolean matches(RecipeInputInventory inventory, int offsetX, int offsetY, boolean flipped) {
        for (int i = 0; i < inventory.getWidth(); ++i) {
            for (int j = 0; j < inventory.getHeight(); ++j) {
                int k = i - offsetX;
                int l = j - offsetY;
                Ingredient ingredient = Ingredient.EMPTY;
                if (k >= 0 && l >= 0 && k < this.width && l < this.height) {
                    ingredient = flipped ? this.ingredients.get(this.width - k - 1 + l * this.width) : this.ingredients.get(k + l * this.width);
                }
                if (ingredient.test(inventory.getStack(i + j * inventory.getWidth()))) continue;
                return false;
            }
        }
        return true;
    }

    private void writeToBuf(RegistryByteBuf buf) {
        buf.writeVarInt(this.width);
        buf.writeVarInt(this.height);
        for (Ingredient ingredient : this.ingredients) {
            Ingredient.PACKET_CODEC.encode(buf, ingredient);
        }
    }

    private static RawShapedRecipe readFromBuf(RegistryByteBuf buf) {
        int i = buf.readVarInt();
        int j = buf.readVarInt();
        DefaultedList<Ingredient> defaultedList = DefaultedList.ofSize(i * j, Ingredient.EMPTY);
        defaultedList.replaceAll(ingredient -> (Ingredient)Ingredient.PACKET_CODEC.decode(buf));
        return new RawShapedRecipe(i, j, defaultedList, Optional.empty());
    }

    public record Data(Map<Character, Ingredient> key, List<String> pattern) {
        private static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().comapFlatMap(pattern -> {
            if (pattern.size() > 3) {
                return DataResult.error(() -> "Invalid pattern: too many rows, 3 is maximum");
            }
            if (pattern.isEmpty()) {
                return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
            }
            int i = ((String)pattern.get(0)).length();
            for (String string : pattern) {
                if (string.length() > 3) {
                    return DataResult.error(() -> "Invalid pattern: too many columns, 3 is maximum");
                }
                if (i == string.length()) continue;
                return DataResult.error(() -> "Invalid pattern: each row must be the same width");
            }
            return DataResult.success(pattern);
        }, Function.identity());
        private static final Codec<Character> KEY_ENTRY_CODEC = Codec.STRING.comapFlatMap(keyEntry -> {
            if (keyEntry.length() != 1) {
                return DataResult.error(() -> "Invalid key entry: '" + keyEntry + "' is an invalid symbol (must be 1 character only).");
            }
            if (" ".equals(keyEntry)) {
                return DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.");
            }
            return DataResult.success(Character.valueOf(keyEntry.charAt(0)));
        }, String::valueOf);
        public static final MapCodec<Data> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(((MapCodec)Codecs.strictUnboundedMap(KEY_ENTRY_CODEC, Ingredient.DISALLOW_EMPTY_CODEC).fieldOf("key")).forGetter(data -> data.key), ((MapCodec)PATTERN_CODEC.fieldOf("pattern")).forGetter(data -> data.pattern)).apply((Applicative<Data, ?>)instance, Data::new));
    }
}

