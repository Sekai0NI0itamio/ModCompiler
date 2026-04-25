/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.realms.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

@Environment(value=EnvType.CLIENT)
public interface Errable {
    public void error(Text var1);

    default public void error(String errorMessage) {
        this.error(new LiteralText(errorMessage));
    }
}

