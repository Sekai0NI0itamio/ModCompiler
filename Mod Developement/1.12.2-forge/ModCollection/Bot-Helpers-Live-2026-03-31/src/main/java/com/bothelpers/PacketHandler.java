package com.bothelpers;

import com.bothelpers.network.PacketOpenBotGui;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class PacketHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("bothelpers");
    private static int packetId = 0;

    public static void registerMessages() {
        INSTANCE.registerMessage(PacketOpenBotGui.Handler.class, PacketOpenBotGui.class, packetId++, Side.SERVER);
    }
}