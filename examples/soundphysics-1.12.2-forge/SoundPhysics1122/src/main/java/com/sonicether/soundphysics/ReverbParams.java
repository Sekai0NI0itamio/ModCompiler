package com.sonicether.soundphysics;

public class ReverbParams {
    public float density, diffusion, gain, gainHF;
    public float decayTime, decayHFRatio;
    public float reflectionsGain, lateReverbGain, lateReverbDelay;
    public float airAbsorptionGainHF, roomRolloffFactor;

    public ReverbParams(float density, float diffusion, float gain, float gainHF,
                        float decayTime, float decayHFRatio, float reflectionsGain,
                        float lateReverbGain, float lateReverbDelay,
                        float airAbsorptionGainHF, float roomRolloffFactor) {
        this.density = density; this.diffusion = diffusion;
        this.gain = gain; this.gainHF = gainHF;
        this.decayTime = decayTime; this.decayHFRatio = decayHFRatio;
        this.reflectionsGain = reflectionsGain; this.lateReverbGain = lateReverbGain;
        this.lateReverbDelay = lateReverbDelay;
        this.airAbsorptionGainHF = airAbsorptionGainHF;
        this.roomRolloffFactor = roomRolloffFactor;
    }

    public static ReverbParams getReverb0() {
        double rg = SoundPhysicsConfig.reverbGain;
        double rb = SoundPhysicsConfig.reverbBrightness;
        return new ReverbParams(0.0F, 0.0F, (float)(0.32 * rg), (float)(0.85 * rb), 0.15F, 0.2F, 0.0F, (float)(0.18 * rg), 0.005F, (float)(-0.5 * rb), 0.0F);
    }

    public static ReverbParams getReverb1() {
        double rg = SoundPhysicsConfig.reverbGain;
        double rb = SoundPhysicsConfig.reverbBrightness;
        return new ReverbParams(0.0F, 1.0F, (float)(0.5 * rg), (float)(0.7 * rb), 0.55F, 0.5F, 0.012F, (float)(0.25 * rg), 0.012F, (float)(-0.6 * rb), 0.0F);
    }

    public static ReverbParams getReverb2() {
        double rg = SoundPhysicsConfig.reverbGain;
        double rb = SoundPhysicsConfig.reverbBrightness;
        return new ReverbParams(0.0F, 1.0F, (float)(0.65 * rg), (float)(0.55 * rb), 1.68F, 0.7F, 0.02F, (float)(0.35 * rg), 0.025F, (float)(-0.75 * rb), 0.0F);
    }

    public static ReverbParams getReverb3() {
        double rg = SoundPhysicsConfig.reverbGain;
        double rb = SoundPhysicsConfig.reverbBrightness;
        return new ReverbParams(0.0F, 1.0F, (float)(0.8 * rg), (float)(0.4 * rb), 4.142F, 0.85F, 0.03F, (float)(0.5 * rg), 0.04F, (float)(-0.9 * rb), 0.0F);
    }
}
