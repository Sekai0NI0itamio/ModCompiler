/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.world.gen.random;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.random.AbstractRandom;

public interface RandomDeriver {
    default public AbstractRandom createRandom(BlockPos pos) {
        return this.createRandom(pos.getX(), pos.getY(), pos.getZ());
    }

    default public AbstractRandom createRandom(Identifier id) {
        return this.createRandom(id.toString());
    }

    public AbstractRandom createRandom(String var1);

    public AbstractRandom createRandom(int var1, int var2, int var3);

    @VisibleForTesting
    public void addDebugInfo(StringBuilder var1);
}

