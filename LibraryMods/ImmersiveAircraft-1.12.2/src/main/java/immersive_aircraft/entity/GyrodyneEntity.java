package immersive_aircraft.entity;

import net.minecraft.world.World;

/**
 * Helicopter-style gyrodyne: very nimble, smaller size, but lower top speed.
 */
public class GyrodyneEntity extends AbstractAircraftEntity {
    public GyrodyneEntity(World worldIn) {
        super(worldIn);
        this.thrust = 0.07f;
        this.lift = 0.04f;
        this.dragForward = 0.985f;
        this.gravity = 0.05f;
        this.stallSpeed = 0.04f;
        this.mass = 0.9f;
        this.rollFactorDeg = 12.0f;
        this.yawSpeedDeg = 2.0f;
        this.setSize(1.3f, 0.6f);
    }
}
