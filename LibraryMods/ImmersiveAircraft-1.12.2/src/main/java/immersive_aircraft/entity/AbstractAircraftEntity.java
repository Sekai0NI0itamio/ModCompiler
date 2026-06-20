package immersive_aircraft.entity;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Base class for all aircraft. Holds physics state and movement logic adapted
 * from the 1.20.1 Immersive Aircraft mod. Models the core of a vehicle: a
 * controllable body that can be mounted, flown with WASD + pitch inputs, and
 * that uses a simple lift/drag approximation in place of the modern mod's
 * JOML-matrix-based simulation.
 */
public abstract class AbstractAircraftEntity extends Entity implements IEntityAdditionalSpawnData {

    // DataWatcher indices (avoid clashes with vanilla - choose high numbers)
    private static final int DW_ENGINE_TARGET = 20;
    private static final int DW_ENGINE_POWER = 21;
    private static final int DW_HEALTH = 22;
    private static final int DW_BOOST = 23;
    private static final int DW_LOW_FUEL = 24;

    // --- Tunable flight parameters (overridable per aircraft) ---
    protected float engineReactionSpeed = 20.0f;
    protected float yawSpeedDeg = 1.6f;
    protected float pitchSpeedDeg = 1.2f;
    protected float rollFactorDeg = 8.0f;
    protected float lift = 0.045f;
    protected float thrust = 0.075f;
    protected float dragForward = 0.992f;
    protected float dragVertical = 0.97f;
    protected float gravity = 0.06f;
    protected float stallSpeed = 0.05f;
    protected float mass = 1.0f;
    protected float maxFuel = 1000.0f;
    protected float lowFuel = 200.0f;
    protected float fuelConsumption = 0.05f; // per tick at full throttle

    // --- Runtime state ---
    protected float enginePowerSmoothed = 0.0f;
    protected float propellerRotation = 0.0f;
    protected float lastForwardInput = 0.0f;
    protected float lastStrafeInput = 0.0f;
    protected float lastPitchInput = 0.0f;
    protected float roll = 0.0f;
    protected float prevRoll = 0.0f;
    protected int fuel = 1000;

    public AbstractAircraftEntity(World worldIn) {
        super(worldIn);
        this.preventEntitySpawning = true;
        this.setSize(1.75f, 0.85f);
        this.stepHeight = 0.6f;
    }

    @Override
    protected void entityInit() {
        // 1.12.2 DataWatcher uses raw integer IDs and Object values.
        this.getDataWatcher().addObject(DW_ENGINE_TARGET, 0.0f);
        this.getDataWatcher().addObject(DW_ENGINE_POWER, 0.0f);
        this.getDataWatcher().addObject(DW_HEALTH, 1.0f);
        this.getDataWatcher().addObject(DW_BOOST, 0);
        this.getDataWatcher().addObject(DW_LOW_FUEL, (byte) 0);
    }

    // --- DataWatcher helper methods (untyped Object API) ---

    @SuppressWarnings("unchecked")
    private <T> T watchableGet(int id) {
        return (T) this.getDataWatcher().getWatchableObject(id).getValue();
    }

    private void watchableSet(int id, Object value) {
        this.getDataWatcher().updateObject(id, value);
    }

    private boolean watchableGetBool(int id) {
        return ((Byte) this.getDataWatcher().getWatchableObject(id).getValue()) != 0;
    }

    private void watchableSetBool(int id, boolean value) {
        this.getDataWatcher().updateObject(id, (byte) (value ? 1 : 0));
    }

    // --- Fuel & engine helpers ---

    public float getEngineTarget() {
        return watchableGet(DW_ENGINE_TARGET);
    }

    public void setEngineTarget(float target) {
        watchableSet(DW_ENGINE_TARGET, MathHelper.clamp(target, 0.0f, 1.0f));
    }

    public float getEnginePower() {
        return enginePowerSmoothed;
    }

    public float getHealth() {
        return watchableGet(DW_HEALTH);
    }

    public void setHealth(float h) {
        watchableSet(DW_HEALTH, h);
    }

    public int getFuel() {
        return fuel;
    }

    public boolean isFuelLow() {
        return this.fuel < lowFuel;
    }

    // --- Mounting / interaction ---

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (!this.world.isRemote && this.getPassengers().isEmpty()) {
            player.startRiding(this);
            return true;
        }
        return super.processInitialInteract(player, hand);
    }

    @Override
    public boolean canBeSteered() {
        return true;
    }

    @Override
    public double getMountedYOffset() {
        return 0.4d;
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox() {
        return this.getEntityBoundingBox();
    }

    // --- Update loop ---

    @Override
    public void onUpdate() {
        super.onUpdate();

        prevRoll = roll;
        propellerRotation = (propellerRotation + getEnginePower() * 1.5f) % 360.0f;

        Entity controller = getControllingPassenger();
        if (controller != null) {
            handleControlInputs((EntityPlayer) controller);
        } else {
            // No pilot - ease engine back down
            setEngineTarget(getEngineTarget() * 0.9f);
            lastForwardInput = 0;
            lastStrafeInput = 0;
            lastPitchInput = 0;
        }

        // Engine power ramp
        float target = getEngineTarget();
        float reactionSteps = engineReactionSpeed / mass;
        enginePowerSmoothed += (target - enginePowerSmoothed) / Math.max(1.0f, reactionSteps);
        watchableSet(DW_ENGINE_POWER, enginePowerSmoothed);

        if (!this.world.isRemote) {
            // Fuel consumption
            if (fuel > 0) {
                fuel -= Math.max(0, (int) (getEnginePower() * fuelConsumption));
                if (fuel < 0) fuel = 0;
            }
            watchableSetBool(DW_LOW_FUEL, isFuelLow());
        }

        // On ground behaviour
        if (this.onGround) {
            roll = roll * 0.9f;
        } else {
            roll = -lastStrafeInput * rollFactorDeg;
        }

        // Reset NaN state
        if (Double.isNaN(motionX) || Double.isNaN(motionY) || Double.isNaN(motionZ)) {
            motionX = motionY = motionZ = 0;
        }

        applyMovement();
        this.move(MoverType.SELF, motionX, motionY, motionZ);

        // Collisions with other entities
        if (!this.world.isRemote) {
            List<Entity> passengers = this.getPassengers();
            for (Entity p : passengers) {
                p.fallDistance = 0;
            }
        }

        // Smoke particles for engine
        if (this.world.isRemote && getEnginePower() > 0.05f && this.ticksExisted % 2 == 0) {
            emitSmoke();
        }

        // Damage wobble
        if (this.hurtTime > 0) {
            this.rotationYaw += (this.rand.nextFloat() - 0.5f) * this.hurtTime * 0.05f;
        }
    }

    protected void handleControlInputs(EntityPlayer player) {
        // Throttle
        boolean throttleUp = player.movementInput.jump;
        boolean throttleDown = player.isSneaking();
        float target = getEngineTarget();
        if (throttleUp) target = Math.min(1.0f, target + 0.04f);
        if (throttleDown) target = Math.max(0.0f, target - 0.04f);
        setEngineTarget(target);

        // Use movement input from the player object (works in single + multi)
        float forward = player.moveForward;   // W/S
        float strafe = player.moveStrafing;   // A/D

        if (forward > 0) {
            lastForwardInput = forward;
        }
        if (strafe != 0) {
            lastStrafeInput = strafe;
        }
    }

    @Override
    public void updatePassenger(Entity passenger) {
        if (passenger instanceof EntityPlayer) {
            // Sit passenger slightly above origin
            float yawRadians = (float) Math.toRadians(this.rotationYaw);
            double sin = Math.sin(yawRadians);
            double cos = Math.cos(yawRadians);
            double offX = -sin * 0.4d;
            double offZ = cos * 0.4d;
            passenger.setPosition(this.posX + offX, this.posY + getMountedYOffset(), this.posZ + offZ);
        } else {
            super.updatePassenger(passenger);
        }
    }

    /**
     * Apply simple flight physics: lift, thrust, drag, gravity.
     */
    protected void applyMovement() {
        float yaw = (float) Math.toRadians(this.rotationYaw);
        float pitch = (float) Math.toRadians(this.rotationPitch);

        // Forward speed in horizontal plane
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        // Thrust from engine along forward direction
        float engine = getEnginePower();
        double thrustX = forwardX * engine * thrust;
        double thrustZ = forwardZ * engine * thrust;
        this.motionX += thrustX;
        this.motionZ += thrustZ;

        // Vertical thrust
        this.motionY += engine * thrust * Math.sin(pitch) * 0.3f;

        // Airspeed
        double horizSpeed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (horizSpeed > stallSpeed) {
            // Lift based on airspeed and how level the aircraft is
            float liftFactor = (float) (horizSpeed * lift * Math.cos(pitch));
            this.motionY += liftFactor;
        }

        // Drag
        this.motionX *= dragForward;
        this.motionZ *= dragForward;
        this.motionY *= dragVertical;

        // Gravity (reduced when flying)
        this.motionY -= gravity;

        // Prevent falling through floor when no engine power
        if (this.onGround && engine < 0.05f) {
            this.motionY = 0;
        }
    }

    @SideOnly(Side.CLIENT)
    private void emitSmoke() {
        float yaw = (float) Math.toRadians(this.rotationYaw);
        float pitch = (float) Math.toRadians(this.rotationPitch);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double px = this.posX + forwardX * 1.3d;
        double pz = this.posZ + forwardZ * 1.3d;
        double py = this.posY + 0.4d + Math.sin(pitch) * 1.0d;
        for (int i = 0; i < 2; i++) {
            this.world.spawnParticle(EnumParticleTypes.SMOKE,
                    px + (this.rand.nextDouble() - 0.5d) * 0.1d,
                    py + (this.rand.nextDouble() - 0.5d) * 0.1d,
                    pz + (this.rand.nextDouble() - 0.5d) * 0.1d,
                    forwardX * 0.05d, 0.05d, forwardZ * 0.05d);
        }
    }

    // --- Damage & despawn ---

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.isEntityInvulnerable(source)) return false;
        if (this.world.isRemote) return true;
        this.setHealth(Math.max(0.0f, this.getHealth() - amount / 20.0f));
        if (this.getHealth() <= 0.0f) {
            this.setDead();
        }
        return true;
    }

    // --- NBT / Spawn data ---

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.fuel = compound.getInteger("Fuel");
        if (compound.hasKey("Health")) {
            this.setHealth(compound.getFloat("Health"));
        }
        this.setEngineTarget(compound.getFloat("Engine"));
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("Fuel", this.fuel);
        compound.setFloat("Health", this.getHealth());
        compound.setFloat("Engine", this.getEngineTarget());
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeFloat(this.rotationYaw);
        buffer.writeFloat(this.rotationPitch);
        buffer.writeInt(this.fuel);
    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {
        this.rotationYaw = additionalData.readFloat();
        this.rotationPitch = additionalData.readFloat();
        this.fuel = additionalData.readInt();
    }

    // --- Camera / look ---

    @Override
    public void applyOrientationToEntity(Entity entityToUpdate) {
        // Use the player's existing orientation so the head still turns freely.
        entityToUpdate.setRenderYawOffset(this.rotationYaw);
        entityToUpdate.prevRotationYaw = this.rotationYaw;
        entityToUpdate.rotationYawHead = entityToUpdate.getRotationYawHead();
    }

    public float getPropellerRotation(float partialTicks) {
        return propellerRotation + (1.0f - partialTicks) * getEnginePower();
    }

    public float getRoll(float partialTicks) {
        return prevRoll + (roll - prevRoll) * partialTicks;
    }
}
