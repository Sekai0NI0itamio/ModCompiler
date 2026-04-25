package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import java.nio.ByteBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@DontObfuscate
public abstract class GpuBuffer implements AutoCloseable {
    public static final int USAGE_MAP_READ = 1;
    public static final int USAGE_MAP_WRITE = 2;
    public static final int USAGE_HINT_CLIENT_STORAGE = 4;
    public static final int USAGE_COPY_DST = 8;
    public static final int USAGE_COPY_SRC = 16;
    public static final int USAGE_VERTEX = 32;
    public static final int USAGE_INDEX = 64;
    public static final int USAGE_UNIFORM = 128;
    public static final int USAGE_UNIFORM_TEXEL_BUFFER = 256;
    private final int usage;
    public int size;

    public GpuBuffer(int p_361832_, int p_407906_) {
        this.size = p_407906_;
        this.usage = p_361832_;
    }

    public int size() {
        return this.size;
    }

    public int usage() {
        return this.usage;
    }

    public abstract boolean isClosed();

    @Override
    public abstract void close();

    public GpuBufferSlice slice(int p_406440_, int p_406094_) {
        if (p_406440_ >= 0 && p_406094_ >= 0 && p_406440_ + p_406094_ <= this.size) {
            return new GpuBufferSlice(this, p_406440_, p_406094_);
        } else {
            throw new IllegalArgumentException(
                "Offset of " + p_406440_ + " and length " + p_406094_ + " would put new slice outside buffer's range (of 0," + p_406094_ + ")"
            );
        }
    }

    public GpuBufferSlice slice() {
        return new GpuBufferSlice(this, 0, this.size);
    }

    @OnlyIn(Dist.CLIENT)
    @DontObfuscate
    public interface MappedView extends AutoCloseable {
        ByteBuffer data();

        @Override
        void close();
    }
}
