package immersive_aircraft.entity;

import net.minecraft.world.World;

/**
 * A larger cargo-carrying airship. Same floatiness as the basic airship but
 * with extra drag/mass to feel heavier.
 */
public class CargoAirshipEntity extends AbstractAircraftEntity {
    public CargoAirshipEntity(World worldIn) {
        super(worldIn);
        this.thrust = 0.05f;
        this.lift = 0.085f;
        this.dragForward = 0.98f;
        this.gravity = 0.02f;
        this.stallSpeed = 0.02f;
        this.mass = 1.8f;
        this.setSize(1.75f, 3.0f);
    }
}
