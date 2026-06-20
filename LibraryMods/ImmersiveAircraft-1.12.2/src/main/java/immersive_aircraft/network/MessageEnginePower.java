package immersive_aircraft.network;

import io.netty.buffer.ByteBuf;
import immersive_aircraft.entity.AbstractAircraftEntity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client -> server packet that tells the server to set the aircraft's engine
 * power to a new value (0..1).
 */
public class MessageEnginePower implements IMessage {
    private float target;

    public MessageEnginePower() {
    }

    public MessageEnginePower(float target) {
        this.target = target;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.target = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(target);
    }

    public static class Handler implements IMessageHandler<MessageEnginePower, IMessage> {
        @Override
        public IMessage onMessage(MessageEnginePower message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player.getRidingEntity() instanceof AbstractAircraftEntity) {
                ((AbstractAircraftEntity) player.getRidingEntity()).setEngineTarget(message.target);
            }
            return null;
        }
    }
}
