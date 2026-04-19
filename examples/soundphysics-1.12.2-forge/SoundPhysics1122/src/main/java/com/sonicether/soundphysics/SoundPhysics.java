package com.sonicether.soundphysics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import java.util.regex.Pattern;

public class SoundPhysics {

    private static final float PHI = 1.618033988F;
    private static final Pattern AMBIENT_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.]+:ambient\\..*$");

    private static int auxFXSlot0, auxFXSlot1, auxFXSlot2, auxFXSlot3;
    private static int reverb0, reverb1, reverb2, reverb3;
    private static int directFilter0;
    private static int sendFilter0, sendFilter1, sendFilter2, sendFilter3;
    private static int maxAuxSends;

    static void setupEFX() {
        long currentContext = ALC10.alcGetCurrentContext();
        long currentDevice = ALC10.alcGetContextsDevice(currentContext);

        if (!ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
            SoundPhysicsMod.LOGGER.error("[Sound Physics] EFX Extension not found. Aborting.");
            return;
        }
        SoundPhysicsMod.LOGGER.info("[Sound Physics] EFX Extension found.");

        maxAuxSends = ALC10.alcGetInteger(currentDevice, EXTEfx.ALC_MAX_AUXILIARY_SENDS);
        SoundPhysicsMod.LOGGER.info("[Sound Physics] Max auxiliary sends: {}", maxAuxSends);

        auxFXSlot0 = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot0, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);
        auxFXSlot1 = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot1, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);
        auxFXSlot2 = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot2, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);
        auxFXSlot3 = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot3, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);

        reverb0 = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverb0, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        reverb1 = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverb1, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        reverb2 = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverb2, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        reverb3 = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverb3, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);

        directFilter0 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(directFilter0, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        sendFilter0 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter0, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        sendFilter1 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter1, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        sendFilter2 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter2, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        sendFilter3 = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter3, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

        syncReverbParams();
    }

    public static void syncReverbParams() {
        if (auxFXSlot0 == 0) return;
        setReverbParams(ReverbParams.getReverb0(), auxFXSlot0, reverb0);
        setReverbParams(ReverbParams.getReverb1(), auxFXSlot1, reverb1);
        setReverbParams(ReverbParams.getReverb2(), auxFXSlot2, reverb2);
        setReverbParams(ReverbParams.getReverb3(), auxFXSlot3, reverb3);
    }

    public static void processSound(int sourceID, double posX, double posY, double posZ,
                                     SoundCategory category, ResourceLocation sound) {
        if (!SoundPhysicsConfig.enabled || sourceID <= 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        World world = mc.world;

        if (player == null || world == null) {
            setDefaultEnvironment(sourceID);
            return;
        }

        Vec3d soundPos = new Vec3d(posX, posY, posZ);
        Vec3d playerPos = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        double distance = playerPos.distanceTo(soundPos);

        if (distance > SoundPhysicsConfig.maxProcessingDistance) {
            setDefaultEnvironment(sourceID);
            return;
        }

        if (SoundPhysicsConfig.skipAmbientSounds && AMBIENT_PATTERN.matcher(sound.toString()).matches()) {
            setDefaultEnvironment(sourceID);
            return;
        }

        float absorptionCoeff = (float) (SoundPhysicsConfig.blockAbsorption * 3.0);

        // Occlusion calculation
        double occlusion = calculateOcclusion(world, soundPos, playerPos);
        float directCutoff = (float) Math.exp(-occlusion * absorptionCoeff);
        float directGain = (float) Math.pow(directCutoff, 0.1);

        // Underwater filtering
        if (player.isInWater()) {
            directCutoff *= 1.0F - (float) SoundPhysicsConfig.underwaterFilter;
        }

        // Reverb send calculation via raycasting
        int numRays = SoundPhysicsConfig.rayCount;
        int rayBounces = SoundPhysicsConfig.rayBounces;
        float rcpTotalRays = 1.0F / (numRays * rayBounces);
        float maxDistance = 64.0F;
        float gAngle = PHI * (float) Math.PI * 2.0F;

        float sendGain0 = 0, sendGain1 = 0, sendGain2 = 0, sendGain3 = 0;
        float sendCutoff0 = 1, sendCutoff1 = 1, sendCutoff2 = 1, sendCutoff3 = 1;

        for (int i = 0; i < numRays; i++) {
            float fiN = (float) i / numRays;
            float longitude = gAngle * i;
            float latitude = (float) Math.asin(fiN * 2.0F - 1.0F);

            Vec3d rayDir = new Vec3d(
                Math.cos(latitude) * Math.cos(longitude),
                Math.cos(latitude) * Math.sin(longitude),
                Math.sin(latitude));

            Vec3d rayEnd = soundPos.add(rayDir.scale(maxDistance));
            RayTraceResult hit = rayTrace(world, soundPos, rayEnd);

            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                double rayLength = soundPos.distanceTo(hit.hitVec);
                BlockPos hitBlock = hit.getBlockPos();
                IBlockState hitState = world.getBlockState(hitBlock);
                float blockReflectivity = getBlockReflectivity(hitState);
                float energyTowardsPlayer = 0.25F * (blockReflectivity * 0.75F + 0.25F);
                float totalRayDistance = (float) rayLength;

                for (int j = 0; j < rayBounces; j++) {
                    Vec3d newDir = reflect(rayDir, new Vec3d(hit.sideHit.getDirectionVec()));
                    Vec3d newStart = hit.hitVec;
                    Vec3d newEnd = newStart.add(newDir.scale(maxDistance));
                    RayTraceResult newHit = rayTrace(world, newStart, newEnd);

                    if (newHit != null && newHit.typeOfHit == RayTraceResult.Type.BLOCK) {
                        totalRayDistance += (float) newStart.distanceTo(newHit.hitVec);
                        BlockPos newHitBlock = newHit.getBlockPos();
                        IBlockState newHitState = world.getBlockState(newHitBlock);
                        float bounceReflectivity = getBlockReflectivity(newHitState);
                        rayDir = newDir;
                        hit = newHit;
                    } else {
                        totalRayDistance += (float) newStart.distanceTo(playerPos);
                        break;
                    }

                    if (totalRayDistance < SoundPhysicsConfig.reverbAttenuationDistance) continue;

                    float reflectionDelay = Math.max(totalRayDistance, 0.0F) * 0.12F * blockReflectivity;
                    float cross0 = 1.0F - MathHelper.clamp(Math.abs(reflectionDelay - 0.0F), 0.0F, 1.0F);
                    float cross1 = 1.0F - MathHelper.clamp(Math.abs(reflectionDelay - 1.0F), 0.0F, 1.0F);
                    float cross2 = 1.0F - MathHelper.clamp(Math.abs(reflectionDelay - 2.0F), 0.0F, 1.0F);
                    float cross3 = MathHelper.clamp(reflectionDelay - 2.0F, 0.0F, 1.0F);

                    sendGain0 += cross0 * energyTowardsPlayer * 6.4F * rcpTotalRays;
                    sendGain1 += cross1 * energyTowardsPlayer * 12.8F * rcpTotalRays;
                    sendGain2 += cross2 * energyTowardsPlayer * 12.8F * rcpTotalRays;
                    sendGain3 += cross3 * energyTowardsPlayer * 12.8F * rcpTotalRays;
                }
            }
        }

        sendGain0 = MathHelper.clamp(sendGain0, 0.0F, 1.0F);
        sendGain1 = MathHelper.clamp(sendGain1, 0.0F, 1.0F);
        sendGain2 = MathHelper.clamp(sendGain2 * 1.05F - 0.05F, 0.0F, 1.0F);
        sendGain3 = MathHelper.clamp(sendGain3 * 1.05F - 0.05F, 0.0F, 1.0F);

        // Distance-based reverb attenuation
        float maxSoundDist = AL10.alGetSourcef(sourceID, AL10.AL_MAX_DISTANCE);
        if (maxSoundDist > 0) {
            float sendMult = 1.0F - Math.min((float) distance / (maxSoundDist * (float) SoundPhysicsConfig.reverbDistance), 1.0F);
            sendGain0 *= sendMult;
            sendGain1 *= sendMult;
            sendGain2 *= sendMult;
            sendGain3 *= sendMult;
        }

        if (player.isInWater()) {
            sendCutoff0 *= 0.4F;
            sendCutoff1 *= 0.4F;
            sendCutoff2 *= 0.4F;
            sendCutoff3 *= 0.4F;
        }

        setEnvironment(sourceID, sendGain0, sendGain1, sendGain2, sendGain3,
            sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain);
    }

    public static void setDefaultEnvironment(int sourceID) {
        setEnvironment(sourceID, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1);
    }

    public static void setEnvironment(int sourceID, float sendGain0, float sendGain1, float sendGain2, float sendGain3,
                                       float sendCutoff0, float sendCutoff1, float sendCutoff2, float sendCutoff3,
                                       float directCutoff, float directGain) {
        if (!SoundPhysicsConfig.enabled || sourceID <= 0) return;

        try {
            if (maxAuxSends >= 4) {
                EXTEfx.alFilterf(sendFilter0, EXTEfx.AL_LOWPASS_GAIN, sendGain0);
                EXTEfx.alFilterf(sendFilter0, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff0);
                AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot0, 3, sendFilter0);
            }
            if (maxAuxSends >= 3) {
                EXTEfx.alFilterf(sendFilter1, EXTEfx.AL_LOWPASS_GAIN, sendGain1);
                EXTEfx.alFilterf(sendFilter1, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff1);
                AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot1, 2, sendFilter1);
            }
            if (maxAuxSends >= 2) {
                EXTEfx.alFilterf(sendFilter2, EXTEfx.AL_LOWPASS_GAIN, sendGain2);
                EXTEfx.alFilterf(sendFilter2, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff2);
                AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot2, 1, sendFilter2);
            }
            if (maxAuxSends >= 1) {
                EXTEfx.alFilterf(sendFilter3, EXTEfx.AL_LOWPASS_GAIN, sendGain3);
                EXTEfx.alFilterf(sendFilter3, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff3);
                AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot3, 0, sendFilter3);
            }

            EXTEfx.alFilterf(directFilter0, EXTEfx.AL_LOWPASS_GAIN, directGain);
            EXTEfx.alFilterf(directFilter0, EXTEfx.AL_LOWPASS_GAINHF, directCutoff);
            AL11.alSourcei(sourceID, EXTEfx.AL_DIRECT_FILTER, directFilter0);
            AL11.alSourcef(sourceID, EXTEfx.AL_AIR_ABSORPTION_FACTOR, (float) SoundPhysicsConfig.airAbsorption);
        } catch (Exception e) {
            SoundPhysicsMod.LOGGER.warn("[Sound Physics] Error setting environment: {}", e.getMessage());
        }
    }

    private static double calculateOcclusion(World world, Vec3d soundPos, Vec3d playerPos) {
        double occlusion = 0;
        BlockPos lastPos = new BlockPos(soundPos);
        int maxRays = SoundPhysicsConfig.maxOcclusionRays;

        for (int i = 0; i < maxRays; i++) {
            RayTraceResult hit = rayTrace(world, soundPos, playerPos);
            if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) break;

            BlockPos hitPos = hit.getBlockPos();
            if (hitPos.equals(lastPos)) break;
            lastPos = hitPos;

            IBlockState state = world.getBlockState(hitPos);
            float blockOcclusion = getBlockOcclusion(state);

            Vec3d dirVec = soundPos.subtract(hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5);
            EnumFacing side = EnumFacing.getFacingFromVector((float) dirVec.x, (float) dirVec.y, (float) dirVec.z);

            if (!state.isSideSolid(world, hitPos, side)) {
                blockOcclusion *= SoundPhysicsConfig.nonFullBlockOcclusionFactor;
            }

            occlusion += blockOcclusion;
            if (occlusion > SoundPhysicsConfig.maxOcclusion) break;
        }

        return Math.min(occlusion, SoundPhysicsConfig.maxOcclusion);
    }

    private static RayTraceResult rayTrace(World world, Vec3d start, Vec3d end) {
        return world.rayTraceBlocks(start, end, false, true, false);
    }

    private static Vec3d reflect(Vec3d dir, Vec3d normal) {
        double dot = dir.dotProduct(normal) * 2.0;
        return new Vec3d(dir.x - dot * normal.x, dir.y - dot * normal.y, dir.z - dot * normal.z);
    }

    private static float getBlockReflectivity(IBlockState state) {
        String name = state.getBlock().getRegistryName() != null ? state.getBlock().getRegistryName().toString() : "";
        if (name.contains("stone") || name.contains("deepslate") || name.contains("netherite")) return 1.5F;
        if (name.contains("iron") || name.contains("copper") || name.contains("gold") || name.contains("metal")) return 1.25F;
        if (name.contains("wood") || name.contains("log") || name.contains("plank")) return 0.4F;
        if (name.contains("wool") || name.contains("honey") || name.contains("moss")) return 0.1F;
        if (name.contains("gravel") || name.contains("grass") || name.contains("dirt")) return 0.3F;
        if (name.contains("glass")) return 0.6F;
        if (name.contains("sand")) return 0.2F;
        return (float) SoundPhysicsConfig.defaultReflectivity;
    }

    private static float getBlockOcclusion(IBlockState state) {
        String name = state.getBlock().getRegistryName() != null ? state.getBlock().getRegistryName().toString() : "";
        if (name.contains("wool") || name.contains("bed")) return 1.5F;
        if (name.contains("moss") || name.contains("sponge")) return 0.75F;
        if (name.contains("honey")) return 0.5F;
        if (name.contains("glass") || name.contains("ice")) return 0.1F;
        if (name.contains("water")) return 0.25F;
        if (name.contains("lava")) return 0.75F;
        if (name.contains("vine") || name.contains("sapling") || name.contains("flower") || name.contains("scaffold")) return 0.0F;
        return (float) SoundPhysicsConfig.defaultOcclusion;
    }

    private static void setReverbParams(ReverbParams r, int auxFXSlot, int reverbSlot) {
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DENSITY, r.density);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DIFFUSION, r.diffusion);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_GAIN, r.gain);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_GAINHF, r.gainHF);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DECAY_TIME, r.decayTime);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, r.decayHFRatio);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, r.reflectionsGain);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, r.lateReverbGain);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, r.lateReverbDelay);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, r.airAbsorptionGainHF);
        EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, r.roomRolloffFactor);
        EXTEfx.alAuxiliaryEffectSloti(auxFXSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbSlot);
    }
}
