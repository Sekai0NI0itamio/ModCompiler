package com.bothelpers.network;

import com.bothelpers.Main;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOpenBotGui implements IMessage {
    private int entityId;
    private int guiId;

    public PacketOpenBotGui() {}

    public PacketOpenBotGui(int entityId, int guiId) {
        this.entityId = entityId;
        this.guiId = guiId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.guiId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.guiId);
    }

    public static class Handler implements IMessageHandler<PacketOpenBotGui, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenBotGui message, MessageContext ctx) {
            EntityPlayerMP serverPlayer = ctx.getServerHandler().player;
            serverPlayer.getServerWorld().addScheduledTask(() -> {
                serverPlayer.openGui(Main.instance, message.guiId, serverPlayer.world, message.entityId, 0, 0);
            });
            return null;
        }
    }
}