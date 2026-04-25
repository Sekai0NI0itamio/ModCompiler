package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@DontObfuscate
public record GpuBufferSlice(GpuBuffer buffer, int offset, int length) {
    public GpuBufferSlice slice(int p_410261_, int p_410472_) {
        if (p_410261_ >= 0 && p_410472_ >= 0 && p_410261_ + p_410472_ < this.length) {
            return new GpuBufferSlice(this.buffer, this.offset + p_410261_, p_410472_);
        } else {
            throw new IllegalArgumentException(
                "Offset of "
                    + p_410261_
                    + " and length "
                    + p_410472_
                    + " would put new slice outside existing slice's range (of "
                    + p_410261_
                    + ","
                    + p_410472_
                    + ")"
            );
        }
    }
}
