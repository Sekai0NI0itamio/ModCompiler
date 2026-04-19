package com.sonicether.soundphysics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class SoundPhysicsConfig {
    public static boolean enabled = true;
    public static double blockAbsorption = 0.4;
    public static double defaultReflectivity = 0.5;
    public static double defaultOcclusion = 1.0;
    public static double nonFullBlockOcclusionFactor = 0.4;
    public static double maxOcclusion = 12.0;
    public static int maxOcclusionRays = 16;
    public static double occlusionVariation = 0.2;
    public static double reverbGain = 1.0;
    public static double reverbBrightness = 1.0;
    public static double reverbDistance = 1.5;
    public static double reverbAttenuationDistance = 2.0;
    public static double airAbsorption = 0.1;
    public static double underwaterFilter = 0.7;
    public static double maxProcessingDistance = 64.0;
    public static int rayCount = 16;
    public static int rayBounces = 3;
    public static boolean skipAmbientSounds = true;
    public static boolean debugLogging = false;

    private static File configFile;

    public static void load(File configDir) {
        configFile = new File(configDir, "sound_physics.cfg");
        if (!configFile.exists()) {
            save();
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
            blockAbsorption = Double.parseDouble(props.getProperty("blockAbsorption", "0.4"));
            defaultReflectivity = Double.parseDouble(props.getProperty("defaultReflectivity", "0.5"));
            defaultOcclusion = Double.parseDouble(props.getProperty("defaultOcclusion", "1.0"));
            nonFullBlockOcclusionFactor = Double.parseDouble(props.getProperty("nonFullBlockOcclusionFactor", "0.4"));
            maxOcclusion = Double.parseDouble(props.getProperty("maxOcclusion", "12.0"));
            maxOcclusionRays = Integer.parseInt(props.getProperty("maxOcclusionRays", "16"));
            occlusionVariation = Double.parseDouble(props.getProperty("occlusionVariation", "0.2"));
            reverbGain = Double.parseDouble(props.getProperty("reverbGain", "1.0"));
            reverbBrightness = Double.parseDouble(props.getProperty("reverbBrightness", "1.0"));
            reverbDistance = Double.parseDouble(props.getProperty("reverbDistance", "1.5"));
            reverbAttenuationDistance = Double.parseDouble(props.getProperty("reverbAttenuationDistance", "2.0"));
            airAbsorption = Double.parseDouble(props.getProperty("airAbsorption", "0.1"));
            underwaterFilter = Double.parseDouble(props.getProperty("underwaterFilter", "0.7"));
            maxProcessingDistance = Double.parseDouble(props.getProperty("maxProcessingDistance", "64.0"));
            rayCount = Integer.parseInt(props.getProperty("rayCount", "16"));
            rayBounces = Integer.parseInt(props.getProperty("rayBounces", "3"));
            skipAmbientSounds = Boolean.parseBoolean(props.getProperty("skipAmbientSounds", "true"));
            debugLogging = Boolean.parseBoolean(props.getProperty("debugLogging", "false"));
        } catch (Exception e) {
            SoundPhysicsMod.LOGGER.error("[Sound Physics] Failed to load config: {}", e.getMessage());
        }
    }

    public static void save() {
        if (configFile == null) return;
        Properties props = new Properties();
        props.setProperty("enabled", String.valueOf(enabled));
        props.setProperty("blockAbsorption", String.valueOf(blockAbsorption));
        props.setProperty("defaultReflectivity", String.valueOf(defaultReflectivity));
        props.setProperty("defaultOcclusion", String.valueOf(defaultOcclusion));
        props.setProperty("nonFullBlockOcclusionFactor", String.valueOf(nonFullBlockOcclusionFactor));
        props.setProperty("maxOcclusion", String.valueOf(maxOcclusion));
        props.setProperty("maxOcclusionRays", String.valueOf(maxOcclusionRays));
        props.setProperty("occlusionVariation", String.valueOf(occlusionVariation));
        props.setProperty("reverbGain", String.valueOf(reverbGain));
        props.setProperty("reverbBrightness", String.valueOf(reverbBrightness));
        props.setProperty("reverbDistance", String.valueOf(reverbDistance));
        props.setProperty("reverbAttenuationDistance", String.valueOf(reverbAttenuationDistance));
        props.setProperty("airAbsorption", String.valueOf(airAbsorption));
        props.setProperty("underwaterFilter", String.valueOf(underwaterFilter));
        props.setProperty("maxProcessingDistance", String.valueOf(maxProcessingDistance));
        props.setProperty("rayCount", String.valueOf(rayCount));
        props.setProperty("rayBounces", String.valueOf(rayBounces));
        props.setProperty("skipAmbientSounds", String.valueOf(skipAmbientSounds));
        props.setProperty("debugLogging", String.valueOf(debugLogging));
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "Sound Physics Configuration");
        } catch (Exception e) {
            SoundPhysicsMod.LOGGER.error("[Sound Physics] Failed to save config: {}", e.getMessage());
        }
    }
}
