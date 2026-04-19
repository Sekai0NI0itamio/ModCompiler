package net.mcreator.ashenremains.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AshenremainsModParticleTypes {
   public static final DeferredRegister<ParticleType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "ashenremains");
   public static final RegistryObject<SimpleParticleType> ASH_PARTICLES = REGISTRY.register("ash_particles", () -> new SimpleParticleType(false));
}
