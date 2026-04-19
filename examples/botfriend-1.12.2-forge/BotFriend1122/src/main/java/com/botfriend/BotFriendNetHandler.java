package com.botfriend;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;

final class BotFriendNetHandler extends NetHandlerPlayServer {
    private static NetworkManager createNetworkManager() {
        return new NetworkManager(EnumPacketDirection.SERVERBOUND);
    }

    BotFriendNetHandler(MinecraftServer server, EntityPlayerMP player) {
        this(server, player, createNetworkManager());
    }

    private BotFriendNetHandler(MinecraftServer server, EntityPlayerMP player, NetworkManager manager) {
        super(server, manager, player);
        manager.setNetHandler(this);
    }

    @Override
    public void update() {
        // Synthetic friends do not have a live network channel to tick.
    }

    @Override
    public void sendPacket(Packet<?> packetIn) {
        // Synthetic friends never receive server packets through a real socket.
    }

    @Override
    public void disconnect(ITextComponent textComponent) {
        // Ignore disconnect requests for synthetic friends.
    }

    @Override
    public void onDisconnect(ITextComponent reason) {
        // Ignore disconnect callbacks for synthetic friends.
    }
}
