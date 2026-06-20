package immersive_aircraft.entity;

import net.minecraft.world.World;

/**
 * A small quadrocopter: zippy, very responsive, but weak lift.
 */
public class QuadrocopterEntity extends AbstractAircraftEntity {
    public QuadrocopterEntity(World worldIn) {
        super(worldIn);
        this.thrust = 0.07f;
        this.lift = 0.035f;
        this.dragForward = 0.988f;
        this.gravity = 0.06f;
        this.stallSpeed = 0.06f;
        this.mass = 0.7f;
        this.rollFactorDeg = 10.0f;
        this.yawSpeedDeg = 1.8f;
        this.setSize(1.5f, 0.5f);
    }
}
