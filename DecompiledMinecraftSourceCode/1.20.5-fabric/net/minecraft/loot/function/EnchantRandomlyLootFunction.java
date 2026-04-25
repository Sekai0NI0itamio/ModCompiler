/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.loot.function;

import com.mojang.datafixers.kinds.Applicative;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.function.LootFunctionTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;

public class EnchantRandomlyLootFunction
extends ConditionalLootFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<RegistryEntryList<Enchantment>> ENCHANTMENT_LIST_CODEC = Registries.ENCHANTMENT.getEntryCodec().listOf().xmap(RegistryEntryList::of, enchantments -> enchantments.stream().toList());
    public static final MapCodec<EnchantRandomlyLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> EnchantRandomlyLootFunction.addConditionsField(instance).and(ENCHANTMENT_LIST_CODEC.optionalFieldOf("enchantments").forGetter(function -> function.enchantments)).apply((Applicative<EnchantRandomlyLootFunction, ?>)instance, EnchantRandomlyLootFunction::new));
    private final Optional<RegistryEntryList<Enchantment>> enchantments;

    EnchantRandomlyLootFunction(List<LootCondition> conditions, Optional<RegistryEntryList<Enchantment>> enchantments) {
        super(conditions);
        this.enchantments = enchantments;
    }

    public LootFunctionType<EnchantRandomlyLootFunction> getType() {
        return LootFunctionTypes.ENCHANT_RANDOMLY;
    }

    @Override
    public ItemStack process(ItemStack stack, LootContext context) {
        Random random = context.getRandom();
        Optional optional = this.enchantments.flatMap(enchantments -> enchantments.getRandom(random)).or(() -> {
            boolean bl = stack.isOf(Items.BOOK);
            List<RegistryEntry.Reference> list = Registries.ENCHANTMENT.streamEntries().filter(entry -> ((Enchantment)entry.value()).isEnabled(context.getWorld().getEnabledFeatures())).filter(enchantment -> ((Enchantment)enchantment.value()).isAvailableForRandomSelection()).filter(enchantment -> bl || ((Enchantment)enchantment.value()).isAcceptableItem(stack)).toList();
            return Util.getRandomOrEmpty(list, random);
        });
        if (optional.isEmpty()) {
            LOGGER.warn("Couldn't find a compatible enchantment for {}", (Object)stack);
            return stack;
        }
        return EnchantRandomlyLootFunction.addEnchantmentToStack(stack, (Enchantment)((RegistryEntry)optional.get()).value(), random);
    }

    private static ItemStack addEnchantmentToStack(ItemStack stack, Enchantment enchantment, Random random) {
        int i = MathHelper.nextInt(random, enchantment.getMinLevel(), enchantment.getMaxLevel());
        if (stack.isOf(Items.BOOK)) {
            stack = new ItemStack(Items.ENCHANTED_BOOK);
        }
        stack.addEnchantment(enchantment, i);
        return stack;
    }

    public static Builder create() {
        return new Builder();
    }

    public static ConditionalLootFunction.Builder<?> builder() {
        return EnchantRandomlyLootFunction.builder(conditions -> new EnchantRandomlyLootFunction((List<LootCondition>)conditions, Optional.empty()));
    }

    public static class Builder
    extends ConditionalLootFunction.Builder<Builder> {
        private final List<RegistryEntry<Enchantment>> enchantments = new ArrayList<RegistryEntry<Enchantment>>();

        @Override
        protected Builder getThisBuilder() {
            return this;
        }

        public Builder add(Enchantment enchantment) {
            this.enchantments.add(enchantment.getRegistryEntry());
            return this;
        }

        @Override
        public LootFunction build() {
            return new EnchantRandomlyLootFunction(this.getConditions(), this.enchantments.isEmpty() ? Optional.empty() : Optional.of(RegistryEntryList.of(this.enchantments)));
        }

        @Override
        protected /* synthetic */ ConditionalLootFunction.Builder getThisBuilder() {
            return this.getThisBuilder();
        }
    }
}

