/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;

@Environment(value=EnvType.CLIENT)
public interface SoundInstanceListener {
    public void onSoundPlayed(SoundInstance var1, WeightedSoundSet var2);
}

