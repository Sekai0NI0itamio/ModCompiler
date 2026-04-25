/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.PacketBundleHandler;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

public interface NetworkState<T extends PacketListener> {
    public NetworkPhase id();

    public NetworkSide side();

    public PacketCodec<ByteBuf, Packet<? super T>> codec();

    @Nullable
    public PacketBundleHandler bundleHandler();

    public static interface Factory<T extends PacketListener, B extends ByteBuf> {
        public NetworkState<T> bind(Function<ByteBuf, B> var1);
    }
}

