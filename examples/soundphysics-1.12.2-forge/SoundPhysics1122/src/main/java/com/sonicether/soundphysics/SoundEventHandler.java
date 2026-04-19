package com.sonicether.soundphysics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundSetupEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class SoundEventHandler {

    private static int tickCounter;
    private static Field soundManagerField;
    private static Field playingSoundsField;
    private static Field sourceField;

    static {
        try {
            for (Field f : SoundManager.class.getDeclaredFields()) {
                if (f.getType() == Map.class) {
                    f.setAccessible(true);
                    playingSoundsField = f;
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onSoundSetup(SoundSetupEvent event) {
        SoundPhysicsMod.LOGGER.info("[Sound Physics] Sound system initialized, setting up EFX...");
        SoundPhysicsMod.initializeEFX();
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (!SoundPhysicsMod.isEFXReady() || !SoundPhysicsConfig.enabled) return;

        ISound sound = event.getSound();
        if (sound == null) return;

        SoundCategory category = sound.getCategory();
        double x = event.getSound().getXPosF();
        double y = event.getSound().getYPosF();
        double z = event.getSound().getZPosF();

        if (x == 0 && y == 0 && z == 0) return;

        int sourceID = findSourceID(sound);
        if (sourceID > 0) {
            SoundPhysics.processSound(sourceID, x, y, z, category, sound.getSoundLocation());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!SoundPhysicsMod.isEFXReady()) return;

        tickCounter++;
        if (tickCounter % 4 != 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        // Periodically re-sync reverb params
        if (tickCounter % 200 == 0) {
            SoundPhysics.syncReverbParams();
        }
    }

    @SuppressWarnings("unchecked")
    private static int findSourceID(ISound sound) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (soundManagerField == null) {
                for (Field f : mc.getClass().getDeclaredFields()) {
                    if (f.getType() == SoundManager.class) {
                        f.setAccessible(true);
                        soundManagerField = f;
                        break;
                    }
                }
            }
            if (soundManagerField == null) return -1;

            SoundManager soundManager = (SoundManager) soundManagerField.get(mc);
            if (playingSoundsField == null) {
                for (Field f : SoundManager.class.getDeclaredFields()) {
                    if (f.getType() == Map.class) {
                        f.setAccessible(true);
                        playingSoundsField = f;
                        break;
                    }
                }
            }
            if (playingSoundsField == null) return -1;

            Map<ISound, ?> playingSounds = (Map<ISound, ?>) playingSoundsField.get(soundManager);
            Object sourceObj = playingSounds.get(sound);
            if (sourceObj == null) return -1;

            if (sourceField == null) {
                for (Field f : sourceObj.getClass().getDeclaredFields()) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        f.setAccessible(true);
                        sourceField = f;
                        break;
                    }
                }
            }
            if (sourceField == null) return -1;

            return sourceField.getInt(sourceObj);
        } catch (Exception e) {
            if (SoundPhysicsConfig.debugLogging) {
                SoundPhysicsMod.LOGGER.debug("[Sound Physics] Could not find source ID: {}", e.getMessage());
            }
            return -1;
        }
    }
}
