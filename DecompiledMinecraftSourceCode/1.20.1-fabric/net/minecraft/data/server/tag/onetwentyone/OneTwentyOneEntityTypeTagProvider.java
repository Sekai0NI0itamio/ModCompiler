/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.data.server.tag.onetwentyone;

import java.util.concurrent.CompletableFuture;
import net.minecraft.data.DataOutput;
import net.minecraft.data.server.tag.ValueLookupTagProvider;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.TagKey;

public class OneTwentyOneEntityTypeTagProvider
extends ValueLookupTagProvider<EntityType<?>> {
    public OneTwentyOneEntityTypeTagProvider(DataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookupFuture) {
        super(output, RegistryKeys.ENTITY_TYPE, registryLookupFuture, (T entityType) -> entityType.getRegistryEntry().registryKey());
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup lookup) {
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.FALL_DAMAGE_IMMUNE)).add(EntityType.BREEZE);
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.DEFLECTS_PROJECTILES)).add(EntityType.BREEZE);
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.CAN_TURN_IN_BOATS)).add(EntityType.BREEZE);
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.IMPACT_PROJECTILES)).add(EntityType.WIND_CHARGE, EntityType.BREEZE_WIND_CHARGE);
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE)).add(EntityType.BREEZE, EntityType.SKELETON, EntityType.BOGGED, EntityType.STRAY, EntityType.ZOMBIE, EntityType.HUSK, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.SLIME);
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.SKELETONS)).add(EntityType.BOGGED);
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.IMMUNE_TO_INFESTED)).add(EntityType.SILVERFISH);
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.IMMUNE_TO_OOZING)).add(EntityType.SLIME);
        ((ValueLookupTagProvider.ObjectBuilder)this.getOrCreateTagBuilder((TagKey)EntityTypeTags.REDIRECTABLE_PROJECTILE)).add(EntityType.WIND_CHARGE, EntityType.BREEZE_WIND_CHARGE);
    }
}

