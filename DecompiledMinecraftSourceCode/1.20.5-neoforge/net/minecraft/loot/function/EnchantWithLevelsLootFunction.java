/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.loot.function;

import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.function.LootFunctionTypes;
import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.loot.provider.number.LootNumberProviderTypes;
import net.minecraft.util.math.random.Random;

public class EnchantWithLevelsLootFunction
extends ConditionalLootFunction {
    public static final MapCodec<EnchantWithLevelsLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> EnchantWithLevelsLootFunction.addConditionsField(instance).and(instance.group(((MapCodec)LootNumberProviderTypes.CODEC.fieldOf("levels")).forGetter(function -> function.range), ((MapCodec)Codec.BOOL.fieldOf("treasure")).orElse(false).forGetter(function -> function.treasureEnchantmentsAllowed))).apply((Applicative<EnchantWithLevelsLootFunction, ?>)instance, EnchantWithLevelsLootFunction::new));
    private final LootNumberProvider range;
    private final boolean treasureEnchantmentsAllowed;

    EnchantWithLevelsLootFunction(List<LootCondition> conditions, LootNumberProvider range, boolean treasureEnchantmentsAllowed) {
        super(conditions);
        this.range = range;
        this.treasureEnchantmentsAllowed = treasureEnchantmentsAllowed;
    }

    public LootFunctionType<EnchantWithLevelsLootFunction> getType() {
        return LootFunctionTypes.ENCHANT_WITH_LEVELS;
    }

    @Override
    public Set<LootContextParameter<?>> getRequiredParameters() {
        return this.range.getRequiredParameters();
    }

    @Override
    public ItemStack process(ItemStack stack, LootContext context) {
        Random random = context.getRandom();
        return EnchantmentHelper.enchant(context.getWorld().getEnabledFeatures(), random, stack, this.range.nextInt(context), this.treasureEnchantmentsAllowed);
    }

    public static Builder builder(LootNumberProvider range) {
        return new Builder(range);
    }

    public static class Builder
    extends ConditionalLootFunction.Builder<Builder> {
        private final LootNumberProvider range;
        private boolean treasureEnchantmentsAllowed;

        public Builder(LootNumberProvider range) {
            this.range = range;
        }

        @Override
        protected Builder getThisBuilder() {
            return this;
        }

        public Builder allowTreasureEnchantments() {
            this.treasureEnchantmentsAllowed = true;
            return this;
        }

        @Override
        public LootFunction build() {
            return new EnchantWithLevelsLootFunction(this.getConditions(), this.range, this.treasureEnchantmentsAllowed);
        }

        @Override
        protected /* synthetic */ ConditionalLootFunction.Builder getThisBuilder() {
            return this.getThisBuilder();
        }
    }
}

