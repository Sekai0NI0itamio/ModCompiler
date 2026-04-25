/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public class GlException
extends RuntimeException {
    public GlException(String message) {
        super(message);
    }

    public GlException(String message, Throwable cause) {
        super(message, cause);
    }
}

