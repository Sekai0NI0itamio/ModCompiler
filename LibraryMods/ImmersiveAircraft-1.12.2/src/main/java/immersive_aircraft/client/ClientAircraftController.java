package immersive_aircraft.client;

import immersive_aircraft.entity.AbstractAircraftEntity;
import immersive_aircraft.network.MessageAircraftControl;
import immersive_aircraft.network.MessageEnginePower;
import immersive_aircraft.network.Messages;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-only tick hook. While the local player is riding an aircraft we
 * throttle the engine and send control packets to the server.
 */
@SideOnly(Side.CLIENT)
public class ClientAircraftController {

    private float lastEngineTarget = -1f;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;
        if (!(player.getRidingEntity() instanceof AbstractAircraftEntity aircraft)) {
            lastEngineTarget = -1f;
            return;
        }

        // Throttle: space increases, shift decreases
        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            aircraft.setEngineTarget(Math.min(1.0f, aircraft.getEngineTarget() + 0.04f));
        }
        if (mc.gameSettings.keyBindSneak.isKeyDown()) {
            aircraft.setEngineTarget(Math.max(0.0f, aircraft.getEngineTarget() - 0.04f));
        }

        // Send engine update if changed
        if (Math.abs(aircraft.getEngineTarget() - lastEngineTarget) > 0.001f) {
            Messages.INSTANCE.sendToServer(new MessageEnginePower(aircraft.getEngineTarget()));
            lastEngineTarget = aircraft.getEngineTarget();
        }

        // Throttle control packets (10 per second is plenty)
        tickCounter++;
        if (tickCounter >= 2) {
            tickCounter = 0;
            float forward = player.movementInput.moveForward;
            float strafe = player.movementInput.moveStrafe;
            float pitch = 0f;
            float yaw = 0f;
            Messages.INSTANCE.sendToServer(new MessageAircraftControl(forward, strafe, pitch, yaw));
        }

        // Visual smoke trail in air
        if (aircraft.getEnginePower() > 0.05f && aircraft.ticksExisted % 4 == 0) {
            double yawRad = Math.toRadians(aircraft.rotationYaw);
            double fx = -Math.sin(yawRad);
            double fz = Math.cos(yawRad);
            double px = aircraft.posX + fx * 1.3d;
            double pz = aircraft.posZ + fz * 1.3d;
            double py = aircraft.posY + 0.4d;
            mc.world.spawnParticle(EnumParticleTypes.SMOKE, px, py, pz,
                    fx * 0.05d, 0.02d, fz * 0.05d);
        }
    }
}
