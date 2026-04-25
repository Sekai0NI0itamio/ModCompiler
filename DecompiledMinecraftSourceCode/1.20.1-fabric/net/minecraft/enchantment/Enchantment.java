/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.enchantment;

import com.google.common.collect.Maps;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.resource.featuretoggle.ToggleableFeature;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

public class Enchantment
implements ToggleableFeature {
    private final Properties properties;
    @Nullable
    protected String translationKey;
    private final RegistryEntry.Reference<Enchantment> registryEntry = Registries.ENCHANTMENT.createEntry(this);

    public static Cost constantCost(int base) {
        return new Cost(base, 0);
    }

    public static Cost leveledCost(int base, int perLevel) {
        return new Cost(base, perLevel);
    }

    public static Properties properties(TagKey<Item> supportedItems, TagKey<Item> primaryItems, int weight, int maxLevel, Cost minCost, Cost maxCost, int anvilCost, EquipmentSlot ... slots) {
        return new Properties(supportedItems, Optional.of(primaryItems), weight, maxLevel, minCost, maxCost, anvilCost, FeatureFlags.DEFAULT_ENABLED_FEATURES, slots);
    }

    public static Properties properties(TagKey<Item> supportedItems, int weight, int maxLevel, Cost minCost, Cost maxCost, int anvilCost, EquipmentSlot ... slots) {
        return new Properties(supportedItems, Optional.empty(), weight, maxLevel, minCost, maxCost, anvilCost, FeatureFlags.DEFAULT_ENABLED_FEATURES, slots);
    }

    public static Properties properties(TagKey<Item> supportedItems, int weight, int maxLevel, Cost minCost, Cost maxCost, int anvilCost, FeatureSet requiredFeatures, EquipmentSlot ... slots) {
        return new Properties(supportedItems, Optional.empty(), weight, maxLevel, minCost, maxCost, anvilCost, requiredFeatures, slots);
    }

    @Nullable
    public static Enchantment byRawId(int id) {
        return (Enchantment)Registries.ENCHANTMENT.get(id);
    }

    public Enchantment(Properties properties) {
        this.properties = properties;
    }

    public Map<EquipmentSlot, ItemStack> getEquipment(LivingEntity entity) {
        EnumMap<EquipmentSlot, ItemStack> map = Maps.newEnumMap(EquipmentSlot.class);
        for (EquipmentSlot equipmentSlot : this.properties.slots()) {
            ItemStack itemStack = entity.getEquippedStack(equipmentSlot);
            if (itemStack.isEmpty()) continue;
            map.put(equipmentSlot, itemStack);
        }
        return map;
    }

    public final TagKey<Item> getApplicableItems() {
        return this.properties.supportedItems();
    }

    public final boolean isPrimaryItem(ItemStack stack) {
        return this.properties.primaryItems.isEmpty() || stack.isIn(this.properties.primaryItems.get());
    }

    public final int getWeight() {
        return this.properties.weight();
    }

    public final int getAnvilCost() {
        return this.properties.anvilCost();
    }

    public final int getMinLevel() {
        return 1;
    }

    public final int getMaxLevel() {
        return this.properties.maxLevel();
    }

    public final int getMinPower(int level) {
        return this.properties.minCost().forLevel(level);
    }

    public final int getMaxPower(int level) {
        return this.properties.maxCost().forLevel(level);
    }

    public int getProtectionAmount(int level, DamageSource source) {
        return 0;
    }

    public float getAttackDamage(int level, @Nullable EntityType<?> entityType) {
        return 0.0f;
    }

    /**
     * {@return whether this enchantment can exist on an item stack with the
     * {@code other} enchantment and the {@code other} enchantment can exist
     * with this enchantment}
     */
    public final boolean canCombine(Enchantment other) {
        return this.canAccept(other) && other.canAccept(this);
    }

    /**
     * {@return whether this enchantment can exist on an item stack with the
     * {@code other} enchantment}
     */
    protected boolean canAccept(Enchantment other) {
        return this != other;
    }

    protected String getOrCreateTranslationKey() {
        if (this.translationKey == null) {
            this.translationKey = Util.createTranslationKey("enchantment", Registries.ENCHANTMENT.getId(this));
        }
        return this.translationKey;
    }

    public String getTranslationKey() {
        return this.getOrCreateTranslationKey();
    }

    public Text getName(int level) {
        MutableText mutableText = Text.translatable(this.getTranslationKey());
        if (this.isCursed()) {
            mutableText.formatted(Formatting.RED);
        } else {
            mutableText.formatted(Formatting.GRAY);
        }
        if (level != 1 || this.getMaxLevel() != 1) {
            mutableText.append(ScreenTexts.SPACE).append(Text.translatable("enchantment.level." + level));
        }
        return mutableText;
    }

    public boolean isAcceptableItem(ItemStack stack) {
        return stack.getItem().getRegistryEntry().isIn(this.properties.supportedItems());
    }

    public void onTargetDamaged(LivingEntity user, Entity target, int level) {
    }

    public void onUserDamaged(LivingEntity user, Entity attacker, int level) {
    }

    public void onAttack(LivingEntity attacket, Entity target, int level) {
    }

    public boolean isTreasure() {
        return false;
    }

    public boolean isCursed() {
        return false;
    }

    /**
     * {@return whether this enchantment will appear in the enchanted book trade
     * offers of librarian villagers}
     */
    public boolean isAvailableForEnchantedBookOffer() {
        return true;
    }

    /**
     * {@return whether this enchantment will appear in the enchanting table or
     * loots with random enchant function}
     */
    public boolean isAvailableForRandomSelection() {
        return true;
    }

    @Deprecated
    public RegistryEntry.Reference<Enchantment> getRegistryEntry() {
        return this.registryEntry;
    }

    @Override
    public FeatureSet getRequiredFeatures() {
        return this.properties.requiredFeatures();
    }

    public record Cost(int base, int perLevel) {
        public int forLevel(int level) {
            return this.base + this.perLevel * (level - 1);
        }
    }

    public record Properties(TagKey<Item> supportedItems, Optional<TagKey<Item>> primaryItems, int weight, int maxLevel, Cost minCost, Cost maxCost, int anvilCost, FeatureSet requiredFeatures, EquipmentSlot[] slots) {
    }
}

