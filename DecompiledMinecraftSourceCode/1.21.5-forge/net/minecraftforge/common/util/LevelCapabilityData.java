/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.codecs.RecordCodecBuilder;

@SuppressWarnings("deprecation")
public class LevelCapabilityData extends SavedData {
    public static final SavedDataType<LevelCapabilityData> TYPE = new SavedDataType<LevelCapabilityData>(
        "capabilities",
        LevelCapabilityData::new,
        ctx ->
            RecordCodecBuilder.create(b ->
                b.group(
                    CompoundTag.CODEC.fieldOf("data").forGetter(i -> {
                        if (i.serializable == null)
                        	return new CompoundTag();
                        return i.serializable.serializeNBT(ctx.levelOrThrow().registryAccess());
                    })
                ).apply(b, nbt -> new LevelCapabilityData(ctx, nbt))
            ),
        null
    );

    @Nullable
    private final INBTSerializable<CompoundTag> serializable;

    private LevelCapabilityData(SavedData.Context ctx) {
        this.serializable = ctx.levelOrThrow().getCapabilityDispatcher();
    }

    private LevelCapabilityData(SavedData.Context ctx, CompoundTag data) {
        this(ctx);
        if (this.serializable != null)
            this.serializable.deserializeNBT(ctx.levelOrThrow().registryAccess(), data);
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}
