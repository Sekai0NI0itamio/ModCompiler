package com.mojang.blaze3d.opengl;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public abstract class BufferStorage {
    public static BufferStorage create(GLCapabilities p_408913_, Set<String> p_406550_) {
        if (p_408913_.GL_ARB_buffer_storage && GlDevice.USE_GL_ARB_buffer_storage) {
            p_406550_.add("GL_ARB_buffer_storage");
            return new BufferStorage.Immutable();
        } else {
            return new BufferStorage.Mutable();
        }
    }

    public abstract GlBuffer createBuffer(DirectStateAccess p_409107_, @Nullable Supplier<String> p_410473_, int p_406282_, int p_409157_);

    public abstract GlBuffer createBuffer(DirectStateAccess p_409765_, @Nullable Supplier<String> p_407196_, int p_407308_, ByteBuffer p_408422_);

    public abstract GlBuffer.GlMappedView mapBuffer(DirectStateAccess p_409915_, GlBuffer p_409719_, int p_408800_, int p_410308_, int p_406322_);

    @OnlyIn(Dist.CLIENT)
    static class Immutable extends BufferStorage {
        @Override
        public GlBuffer createBuffer(DirectStateAccess p_407964_, @Nullable Supplier<String> p_407920_, int p_408114_, int p_406165_) {
            int i = p_407964_.createBuffer();
            p_407964_.bufferStorage(i, p_406165_, GlConst.bufferUsageToGlFlag(p_408114_));
            ByteBuffer bytebuffer = this.tryMapBufferPersistent(p_407964_, p_408114_, i, p_406165_);
            return new GlBuffer(p_407920_, p_407964_, p_408114_, p_406165_, i, bytebuffer);
        }

        @Override
        public GlBuffer createBuffer(DirectStateAccess p_410114_, @Nullable Supplier<String> p_410547_, int p_410484_, ByteBuffer p_408084_) {
            int i = p_410114_.createBuffer();
            int j = p_408084_.remaining();
            p_410114_.bufferStorage(i, p_408084_, GlConst.bufferUsageToGlFlag(p_410484_));
            ByteBuffer bytebuffer = this.tryMapBufferPersistent(p_410114_, p_410484_, i, j);
            return new GlBuffer(p_410547_, p_410114_, p_410484_, j, i, bytebuffer);
        }

        @Nullable
        private ByteBuffer tryMapBufferPersistent(DirectStateAccess p_409012_, int p_407006_, int p_408347_, int p_409175_) {
            int i = 0;
            if ((p_407006_ & 1) != 0) {
                i |= 1;
            }

            if ((p_407006_ & 2) != 0) {
                i |= 18;
            }

            ByteBuffer bytebuffer;
            if (i != 0) {
                GlStateManager.clearGlErrors();
                bytebuffer = p_409012_.mapBufferRange(p_408347_, 0, p_409175_, i | 64);
                if (bytebuffer == null) {
                    throw new IllegalStateException("Can't persistently map buffer, opengl error " + GlStateManager._getError());
                }
            } else {
                bytebuffer = null;
            }

            return bytebuffer;
        }

        @Override
        public GlBuffer.GlMappedView mapBuffer(DirectStateAccess p_407274_, GlBuffer p_406624_, int p_409835_, int p_406240_, int p_409116_) {
            if (p_406624_.persistentBuffer == null) {
                throw new IllegalStateException("Somehow trying to map an unmappable buffer");
            } else {
                return new GlBuffer.GlMappedView(() -> {
                    if ((p_409116_ & 2) != 0) {
                        p_407274_.flushMappedBufferRange(p_406624_.handle, p_409835_, p_406240_);
                    }
                }, p_406624_, MemoryUtil.memSlice(p_406624_.persistentBuffer, p_409835_, p_406240_));
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Mutable extends BufferStorage {
        @Override
        public GlBuffer createBuffer(DirectStateAccess p_408573_, @Nullable Supplier<String> p_408145_, int p_406482_, int p_407764_) {
            int i = p_408573_.createBuffer();
            p_408573_.bufferData(i, p_407764_, GlConst.bufferUsageToGlEnum(p_406482_));
            return new GlBuffer(p_408145_, p_408573_, p_406482_, p_407764_, i, null);
        }

        @Override
        public GlBuffer createBuffer(DirectStateAccess p_405969_, @Nullable Supplier<String> p_406227_, int p_409567_, ByteBuffer p_409249_) {
            int i = p_405969_.createBuffer();
            int j = p_409249_.remaining();
            p_405969_.bufferData(i, p_409249_, GlConst.bufferUsageToGlEnum(p_409567_));
            return new GlBuffer(p_406227_, p_405969_, p_409567_, j, i, null);
        }

        @Override
        public GlBuffer.GlMappedView mapBuffer(DirectStateAccess p_406544_, GlBuffer p_409331_, int p_409732_, int p_406114_, int p_408209_) {
            GlStateManager.clearGlErrors();
            ByteBuffer bytebuffer = p_406544_.mapBufferRange(p_409331_.handle, p_409732_, p_406114_, p_408209_);
            if (bytebuffer == null) {
                throw new IllegalStateException("Can't map buffer, opengl error " + GlStateManager._getError());
            } else {
                return new GlBuffer.GlMappedView(() -> p_406544_.unmapBuffer(p_409331_.handle), p_409331_, bytebuffer);
            }
        }
    }
}
