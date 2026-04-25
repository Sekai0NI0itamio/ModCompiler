/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.util;

import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public interface INBTBuilder {
    default Builder nbt() {
        return new Builder();
    }

    public static class Builder implements INBTBuilder {
        private final CompoundTag tag = new CompoundTag();

        public CompoundTag build() {
            return tag;
        }

        public Builder tag(String key, Tag value) {
            tag.m_128365_(key, value);
            return this;
        }

        public Builder putByte(String key, byte value) {
           tag.m_128344_(key, value);
           return this;
        }

        public Builder putShort(String key, short value) {
            tag.m_128376_(key, value);
            return this;
        }

        public Builder putInt(String key, int value) {
            tag.m_128405_(key, value);
            return this;
        }

        public Builder putLong(String key, long value) {
            tag.m_128356_(key, value);
            return this;
        }

        public Builder putFloat(String key, float value) {
            tag.m_128350_(key, value);
            return this;
        }

        public Builder putDouble(String key, double value) {
            tag.m_128347_(key, value);
            return this;
        }

        public Builder putByteArray(String key, byte... value) {
            tag.m_128382_(key, value);
            return this;
        }

        public Builder putByteArray(String key, List<Byte> value) {
            tag.m_177853_(key, value);
            return this;
        }

        public Builder putIntArray(String key, int... value) {
            tag.m_128385_(key, value);
            return this;
        }

        public Builder putIntArray(String key, List<Integer> value) {
            tag.m_128408_(key, value);
            return this;
        }

        public Builder putLongArray(String key, long... value) {
            tag.m_128388_(key, value);
            return this;
        }

        public Builder putLongArray(String key, List<Long> value) {
            tag.m_128428_(key, value);
            return this;
        }

        public Builder put(String key, boolean value) {
            tag.m_128379_(key, value);
            return this;
        }

        public Builder put(String key, String value) {
            tag.m_128359_(key, value);
            return this;
        }

        public Builder put(String key, UUID value) {
            tag.m_128362_(key, value);
            return this;
        }
    }
}
