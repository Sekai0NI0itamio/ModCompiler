/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.datafixer.fix;

import com.mojang.datafixers.schemas.Schema;
import net.minecraft.datafixer.fix.PointOfInterestRenameFix;

public class BeehiveRenameFix
extends PointOfInterestRenameFix {
    public BeehiveRenameFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected String rename(String input) {
        return input.equals("minecraft:bee_hive") ? "minecraft:beehive" : input;
    }
}

