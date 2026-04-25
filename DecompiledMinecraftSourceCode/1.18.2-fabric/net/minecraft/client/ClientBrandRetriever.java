/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.obfuscate.DontObfuscate;

@Environment(value=EnvType.CLIENT)
public class ClientBrandRetriever {
    public static final String VANILLA = "vanilla";

    @DontObfuscate
    public static String getClientModName() {
        return VANILLA;
    }
}

