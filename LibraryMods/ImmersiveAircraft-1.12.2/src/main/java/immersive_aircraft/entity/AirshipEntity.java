package immersive_aircraft.entity;

import net.minecraft.world.World;

/**
 * A heavy but slow airship that floats thanks to high lift. Acts as the
 * "transport" entry in the mod, like the original Airship.
 */
public class AirshipEntity extends AbstractAircraftEntity {
    public AirshipEntity(World worldIn) {
        super(worldIn);
        this.thrust = 0.06f;
        this.lift = 0.075f;
        this.dragForward = 0.985f;
        this.gravity = 0.02f;
        this.stallSpeed = 0.02f;
        this.mass = 1.4f;
        this.setSize(1.5f, 3.0f);
    }
}
