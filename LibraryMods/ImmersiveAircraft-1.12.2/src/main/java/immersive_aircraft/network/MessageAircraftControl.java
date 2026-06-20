package immersive_aircraft.network;

import io.netty.buffer.ByteBuf;
import immersive_aircraft.entity.AbstractAircraftEntity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client -> server packet carrying the player's current movement and look
 * intent. The server uses it to drive the aircraft they're riding.
 */
public class MessageAircraftControl implements IMessage {
    private float forward;
    private float strafe;
    private float pitch;
    private float yaw;

    public MessageAircraftControl() {
    }

    public MessageAircraftControl(float forward, float strafe, float pitch, float yaw) {
        this.forward = forward;
        this.strafe = strafe;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.forward = buf.readFloat();
        this.strafe = buf.readFloat();
        this.pitch = buf.readFloat();
        this.yaw = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(forward);
        buf.writeFloat(strafe);
        buf.writeFloat(pitch);
        buf.writeFloat(yaw);
    }

    public static class Handler implements IMessageHandler<MessageAircraftControl, IMessage> {
        @Override
        public IMessage onMessage(MessageAircraftControl message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player.getRidingEntity() instanceof AbstractAircraftEntity) {
                AbstractAircraftEntity aircraft = (AbstractAircraftEntity) player.getRidingEntity();
                // Apply yaw (turn) and pitch (climb) deltas
                float yawDelta = -message.strafe * aircraft.yawSpeedDeg;
                float pitchDelta = -message.forward * aircraft.pitchSpeedDeg;
                aircraft.rotationYaw = MathHelper.wrapDegrees(aircraft.rotationYaw + yawDelta);
                aircraft.rotationPitch = MathHelper.clamp(aircraft.rotationPitch + pitchDelta, -45f, 45f);
            }
            return null;
        }
    }
}
