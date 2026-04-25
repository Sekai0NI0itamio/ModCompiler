/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.PacketBundleHandler;
import net.minecraft.network.handler.SideValidatingDispatchingCodecBuilder;
import net.minecraft.network.listener.ClientPacketListener;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.listener.ServerPacketListener;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.BundleSplitterPacket;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

public class NetworkStateBuilder<T extends PacketListener, B extends ByteBuf> {
    private final NetworkPhase type;
    private final NetworkSide side;
    private final List<PacketType<T, ?, B>> packetTypes = new ArrayList();
    @Nullable
    private PacketBundleHandler bundleHandler;

    public NetworkStateBuilder(NetworkPhase type, NetworkSide side) {
        this.type = type;
        this.side = side;
    }

    public <P extends Packet<? super T>> NetworkStateBuilder<T, B> add(net.minecraft.network.packet.PacketType<P> id, PacketCodec<? super B, P> codec) {
        this.packetTypes.add(new PacketType(id, codec));
        return this;
    }

    public <P extends BundlePacket<? super T>, D extends BundleSplitterPacket<? super T>> NetworkStateBuilder<T, B> addBundle(net.minecraft.network.packet.PacketType<P> id, Function<Iterable<Packet<? super T>>, P> bundler, D splitter) {
        PacketCodec packetCodec = PacketCodec.unit(splitter);
        net.minecraft.network.packet.PacketType<BundleSplitterPacket<? super T>> packetType = splitter.getPacketId();
        this.packetTypes.add(new PacketType(packetType, packetCodec));
        this.bundleHandler = PacketBundleHandler.create(id, bundler, splitter);
        return this;
    }

    private PacketCodec<ByteBuf, Packet<? super T>> createCodec(Function<ByteBuf, B> bufUpgrader, List<PacketType<T, ?, B>> packetTypes) {
        SideValidatingDispatchingCodecBuilder sideValidatingDispatchingCodecBuilder = new SideValidatingDispatchingCodecBuilder(this.side);
        for (PacketType packetType : packetTypes) {
            packetType.add(sideValidatingDispatchingCodecBuilder, bufUpgrader);
        }
        return sideValidatingDispatchingCodecBuilder.build();
    }

    public NetworkState<T> build(Function<ByteBuf, B> bufUpgrader) {
        return new NetworkStateImpl(this.type, this.side, this.createCodec(bufUpgrader, this.packetTypes), this.bundleHandler);
    }

    public NetworkState.Factory<T, B> buildFactory() {
        List<PacketType<T, ?, B>> list = List.copyOf(this.packetTypes);
        PacketBundleHandler packetBundleHandler = this.bundleHandler;
        return bufUpgrader -> new NetworkStateImpl(this.type, this.side, this.createCodec(bufUpgrader, list), packetBundleHandler);
    }

    private static <L extends PacketListener> NetworkState<L> build(NetworkPhase type, NetworkSide side, Consumer<NetworkStateBuilder<L, PacketByteBuf>> registrar) {
        NetworkStateBuilder networkStateBuilder = new NetworkStateBuilder(type, side);
        registrar.accept(networkStateBuilder);
        return networkStateBuilder.build(PacketByteBuf::new);
    }

    public static <T extends ServerPacketListener> NetworkState<T> c2s(NetworkPhase type, Consumer<NetworkStateBuilder<T, PacketByteBuf>> registrar) {
        return NetworkStateBuilder.build(type, NetworkSide.SERVERBOUND, registrar);
    }

    public static <T extends ClientPacketListener> NetworkState<T> s2c(NetworkPhase type, Consumer<NetworkStateBuilder<T, PacketByteBuf>> registrar) {
        return NetworkStateBuilder.build(type, NetworkSide.CLIENTBOUND, registrar);
    }

    private static <L extends PacketListener, B extends ByteBuf> NetworkState.Factory<L, B> buildFactory(NetworkPhase type, NetworkSide side, Consumer<NetworkStateBuilder<L, B>> registrar) {
        NetworkStateBuilder networkStateBuilder = new NetworkStateBuilder(type, side);
        registrar.accept(networkStateBuilder);
        return networkStateBuilder.buildFactory();
    }

    public static <T extends ServerPacketListener, B extends ByteBuf> NetworkState.Factory<T, B> c2sFactory(NetworkPhase type, Consumer<NetworkStateBuilder<T, B>> registrar) {
        return NetworkStateBuilder.buildFactory(type, NetworkSide.SERVERBOUND, registrar);
    }

    public static <T extends ClientPacketListener, B extends ByteBuf> NetworkState.Factory<T, B> s2cFactory(NetworkPhase type, Consumer<NetworkStateBuilder<T, B>> registrar) {
        return NetworkStateBuilder.buildFactory(type, NetworkSide.CLIENTBOUND, registrar);
    }

    record PacketType<T extends PacketListener, P extends Packet<? super T>, B extends ByteBuf>(net.minecraft.network.packet.PacketType<P> id, PacketCodec<? super B, P> codec) {
        public void add(SideValidatingDispatchingCodecBuilder<ByteBuf, T> builder, Function<ByteBuf, B> bufUpgrader) {
            PacketCodec<ByteBuf, P> packetCodec = this.codec.mapBuf(bufUpgrader);
            builder.add(this.id, packetCodec);
        }
    }

    record NetworkStateImpl<L extends PacketListener>(NetworkPhase id, NetworkSide side, PacketCodec<ByteBuf, Packet<? super L>> codec, @Nullable PacketBundleHandler bundleHandler) implements NetworkState<L>
    {
        @Override
        @Nullable
        public PacketBundleHandler bundleHandler() {
            return this.bundleHandler;
        }
    }
}

