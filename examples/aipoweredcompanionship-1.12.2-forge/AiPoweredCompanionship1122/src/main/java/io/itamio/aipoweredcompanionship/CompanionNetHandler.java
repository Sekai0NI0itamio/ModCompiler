package io.itamio.aipoweredcompanionship;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;

final class CompanionNetHandler extends NetHandlerPlayServer {
    private static NetworkManager createNetworkManager() {
        return new NetworkManager(EnumPacketDirection.SERVERBOUND);
    }

    CompanionNetHandler(MinecraftServer server, EntityPlayerMP player) {
        super(server, createNetworkManager(), player);
    }

    @Override
    public void sendPacket(Packet<?> packet) {}

    @Override
    public void disconnect(ITextComponent reason) {}

    @Override
    public void onDisconnect(ITextComponent reason) {}
}
