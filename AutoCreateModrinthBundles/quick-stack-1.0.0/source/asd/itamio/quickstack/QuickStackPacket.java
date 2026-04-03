package asd.itamio.quickstack;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class QuickStackPacket implements IMessage {
    
    public QuickStackPacket() {
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
    }
    
    public static class Handler implements IMessageHandler<QuickStackPacket, IMessage> {
        @Override
        public IMessage onMessage(QuickStackPacket message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                QuickStackServerHandler.performQuickStack(ctx.getServerHandler().player);
            });
            return null;
        }
    }
}
