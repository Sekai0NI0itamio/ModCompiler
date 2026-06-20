package immersive_aircraft.entity;

import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;

/**
 * A small, fast biplane. Lighter on lift and more agile than an airship.
 */
public class BiplaneEntity extends AbstractAircraftEntity {
    public BiplaneEntity(World worldIn) {
        super(worldIn);
        // Biplane is small and quick
        this.thrust = 0.085f;
        this.lift = 0.05f;
        this.dragForward = 0.992f;
        this.gravity = 0.04f;
        this.stallSpeed = 0.04f;
        this.mass = 0.8f;
        this.setSize(1.75f, 0.85f);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // Twin-prop smoke: emit on alternating sides. The world.isRemote check
        // ensures particles only spawn on the client, even though onUpdate
        // itself runs on both sides.
        if (this.world.isRemote && this.ticksExisted % 2 == 0 && this.getEnginePower() > 0.05f) {
            float yaw = (float) Math.toRadians(this.rotationYaw);
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            double sideX = -fz;
            double sideZ = fx;
            double sideSign = (this.ticksExisted % 4 == 0) ? 1.0 : -1.0;
            double px = this.posX + fx * 1.3d + sideX * 0.3d * sideSign;
            double pz = this.posZ + fz * 1.3d + sideZ * 0.3d * sideSign;
            double py = this.posY + 0.4d;
            this.world.spawnParticle(EnumParticleTypes.SMOKE,
                    px, py, pz,
                    fx * 0.05d, 0.02d, fz * 0.05d);
        }
    }
}
