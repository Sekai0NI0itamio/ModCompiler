/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.extensions;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeI18n;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

public interface IForgeGameTestHelper {
    private GameTestHelper self() {
        return (GameTestHelper) this;
    }

    default void say(String message) {
        this.say(message, Style.f_131099_);
    }

    default void say(String message, Style style) {
        var component = ForgeI18n.getPattern(message) != null ? Component.m_237115_(message) : Component.m_237113_(message);
        this.say(component.m_130948_(style));
    }

    default void say(Component component) {
        this.self().m_177100_().m_6907_().forEach(p -> p.m_213846_(component));
    }

    default void assertTrue(boolean value, Supplier<String> message) {
        if (!value)
            throw new GameTestAssertException(message.get());
    }

    default void assertFalse(boolean value, Supplier<String> message) {
        if (value)
            throw new GameTestAssertException(message.get());
    }

    default <N> void assertValueEqual(N expected, N actual, String name, String message) {
        this.assertValueEqual(expected, actual, name, () -> message);
    }

    default <N> void assertValueEqual(N expected, N actual, String name, Supplier<String> message) {
        if (!Objects.equals(expected, actual))
            throw new GameTestAssertException("%s -- Expected %s to be %s, but was %s".formatted(message.get(), name, expected, actual));
    }

    default <N> void assertValueEqual(N[] expected, N[] actual, String name, String message) {
        this.assertValueEqual(expected, actual, name, () -> message);
    }

    default <N> void assertValueEqual(N[] expected, N[] actual, String name, Supplier<String> message) {
        if (!Objects.deepEquals(expected, actual))
            throw new GameTestAssertException("%s -- Expected %s to be %s, but was %s".formatted(message.get(), name, Arrays.toString(expected), Arrays.toString(actual)));
    }

    default <E> Registry<E> registry(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.self().m_177100_().m_9598_().m_175515_(registryKey);
    }

    default <E> HolderLookup.RegistryLookup<E> registryLookup(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.self().m_177100_().m_9598_().m_255025_(registryKey);
    }

    default ServerPlayer makeMockServerPlayer() {
        var level = self().m_177100_();
        var cookie = CommonListenerCookie.m_294081_(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var player = new ServerPlayer(level.m_7654_(), level, cookie.f_290628_(), cookie.f_290565_()) {
            public boolean m_5833_() {
                return false;
            }

            public boolean m_7500_() {
                return true;
            }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        @SuppressWarnings("unused") // Constructor has side effects
        var channel = new EmbeddedChannel(connection);
        var server = level.m_7654_();

        var listener = new ServerGamePacketListenerImpl(server, connection, player, cookie);
        var info = GameProtocols.f_315992_.m_324476_(RegistryFriendlyByteBuf.m_324635_(server.m_206579_()));
        connection.m_324855_(info, listener);
        return player;
    }

    /**
     * Registers an event listener that will be unregistered when the test is finished running.
     */
    default <E extends Event> void addEventListener(Consumer<E> consumer) {
        MinecraftForge.EVENT_BUS.addListener(consumer);
        self().addCleanup(success -> MinecraftForge.EVENT_BUS.unregister(consumer));
    }

    /**
     * Registers an event listener that will be unregistered when the test is finished running.
     */
    default void registerEventListener(Object handler) {
        MinecraftForge.EVENT_BUS.register(handler);
        self().addCleanup(success -> MinecraftForge.EVENT_BUS.unregister(handler));
    }

    /**
     * Creates a floor of stone blocks at the bottom of the test area.
     */
    default void makeFloor() {
        makeFloor(Blocks.f_50069_);
    }

    /**
     * Creates a floor of the specified block under the test area.
     */
    default void makeFloor(Block block) {
        makeFloor(block, -1);
    }

    /**
     * Creates a floor of the specified block at the specified height.
     */
    default void makeFloor(Block block, int height) {
        var bounds = self().m_177448_();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < (int) bounds.m_82362_(); x++) {
            for (int y = 0; y < (int) bounds.m_82385_(); y++) {
                pos.m_122178_(x, height, y);
                if (self().m_177232_(pos).m_60713_(Blocks.f_50016_))
                    self().m_177245_(pos, block);
            }
        }
    }

    default BlockState setAndAssertBlock(int x, int y, int z, Block block) {
        return this.setAndAssertBlock(x, y, z, block.m_49966_());
    }

    default BlockState setAndAssertBlock(int x, int y, int z, BlockState state) {
        return this.setAndAssertBlock(new BlockPos(x, y, z), state);
    }

    default BlockState setAndAssertBlock(BlockPos pos, Block block) {
        return this.setAndAssertBlock(pos, block.m_49966_());
    }

    default BlockState setAndAssertBlock(BlockPos pos, BlockState state) {
        this.assertTrue(
                this.self().m_177100_().m_7731_(this.self().m_177449_(pos), state, Block.f_152402_),
                () -> "Failed to set block at pos %s : %s".formatted(pos, state.m_60734_())
        );
        return state;
    }

    default <T> Flag<T> flag(String name) {
        return new Flag<>(name);
    }

    default IntFlag intFlag(String name) {
        return new IntFlag(name);
    }

    default BoolFlag boolFlag(String name) {
        return new BoolFlag(name);
    }

    public static class Flag<T> {
        private final String name;
        @Nullable
        protected T value = null;

        public Flag(String name) {
            this.name = name;
        }

        public void set(T value) {
            this.value = value;
        }

        @Nullable
        public T get() {
            return this.value;
        }

        public void assertUnset() {
            this.assertUnset((Supplier<String>) null);
        }

        public void assertUnset(String message) {
            this.assertUnset(message != null ? () -> message : null);
        }

        public void assertUnset(Supplier<String> message) {
            if (this.value != null) {
                String s = message != null ? message.get() + " -- " : "";
                throw new GameTestAssertException(s + "Expected " + name + " to be null, but was " + this.value);
            }
        }

        public void assertSet() {
            this.assertSet((Supplier<String>) null);
        }

        public void assertSet(String message) {
            this.assertSet(message != null ? () -> message : null);
        }

        public void assertSet(Supplier<String> message) {
            if (this.value == null) {
                String s = message != null ? message.get() + " -- " : "";
                throw new GameTestAssertException(s + "Flag " + name + " was never set");
            }
        }

        public void assertEquals(T expected) {
            this.assertEquals(expected, (Supplier<String>) null);
        }

        public void assertEquals(T expected, String message) {
            this.assertEquals(expected, message != null ? () -> message : null);
        }

        public void assertEquals(T expected, Supplier<String> message) {
            assertSet(message);
            if (expected != null && !expected.equals(this.value)) {
                String s = message != null ? message.get() + " -- " : "";
                throw new GameTestAssertException(s + "Expected " + name + " to be " + expected + ", but was " + this.value);
            }
        }
    }

    public static class IntFlag extends Flag<Long> {
        public IntFlag(String name) {
            super(name);
        }

        public void set(long value) {
            super.set(value);
        }

        public byte getByte() {
            return this.value == null ? -1 : this.value.byteValue();
        }

        public int getInt() {
            return this.value == null ? -1 : this.value.intValue();
        }

        public long getLong() {
            return this.value == null ? -1 : this.value.longValue();
        }

        public void assertEquals(int expected) {
            super.assertEquals((long) expected);
        }

        public void assertEquals(long expected) {
            super.assertEquals(expected);
        }

        public void assertEquals(int expected, String message) {
            super.assertEquals((long) expected, message);
        }

        public void assertEquals(long expected, String message) {
            super.assertEquals(expected, message);
        }

        public void assertEquals(int expected, Supplier<String> message) {
            super.assertEquals((long) expected, message);
        }

        public void assertEquals(long expected, Supplier<String> message) {
            super.assertEquals(expected, message);
        }
    }

    public static class BoolFlag extends Flag<Boolean> {
        public BoolFlag(String name) {
            super(name);
        }

        public void set(boolean value) {
            super.set(value);
        }

        public boolean getBool() {
            return this.value != null && this.value;
        }

        public void assertEquals(boolean expected) {
            super.assertEquals(expected);
        }

        public void assertEquals(boolean expected, String message) {
            super.assertEquals(expected, message);
        }

        public void assertEquals(boolean expected, Supplier<String> message) {
            super.assertEquals(expected, message);
        }
    }
}
