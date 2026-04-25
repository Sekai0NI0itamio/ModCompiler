package com.mojang.blaze3d.opengl;

import java.nio.ByteBuffer;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GLCapabilities;

@OnlyIn(Dist.CLIENT)
public abstract class DirectStateAccess {
    public static DirectStateAccess create(GLCapabilities p_396229_, Set<String> p_397048_) {
        if (p_396229_.GL_ARB_direct_state_access && GlDevice.USE_GL_ARB_direct_state_access) {
            p_397048_.add("GL_ARB_direct_state_access");
            return new DirectStateAccess.Core();
        } else {
            return new DirectStateAccess.Emulated();
        }
    }

    abstract int createBuffer();

    abstract void bufferData(int p_407279_, long p_407889_, int p_410237_);

    abstract void bufferData(int p_407797_, ByteBuffer p_408629_, int p_409542_);

    abstract void bufferSubData(int p_409429_, int p_409685_, ByteBuffer p_406707_);

    abstract void bufferStorage(int p_407401_, long p_410229_, int p_409477_);

    abstract void bufferStorage(int p_409843_, ByteBuffer p_410502_, int p_406395_);

    @Nullable
    abstract ByteBuffer mapBufferRange(int p_407884_, int p_409299_, int p_406257_, int p_406844_);

    abstract void unmapBuffer(int p_410226_);

    abstract int createFrameBufferObject();

    abstract void bindFrameBufferTextures(int p_392888_, int p_393318_, int p_393704_, int p_397768_, int p_392908_);

    abstract void blitFrameBuffers(
        int p_393235_,
        int p_392879_,
        int p_397137_,
        int p_395305_,
        int p_394541_,
        int p_395046_,
        int p_396572_,
        int p_394726_,
        int p_394414_,
        int p_394374_,
        int p_394646_,
        int p_395114_
    );

    abstract void flushMappedBufferRange(int p_408463_, int p_409855_, int p_410443_);

    abstract void copyBufferSubData(int p_410795_, int p_410783_, int p_410812_, int p_410806_, int p_410789_);

    @OnlyIn(Dist.CLIENT)
    static class Core extends DirectStateAccess {
        @Override
        int createBuffer() {
            return ARBDirectStateAccess.glCreateBuffers();
        }

        @Override
        void bufferData(int p_406179_, long p_408610_, int p_406584_) {
            ARBDirectStateAccess.glNamedBufferData(p_406179_, p_408610_, p_406584_);
        }

        @Override
        void bufferData(int p_408499_, ByteBuffer p_408856_, int p_409210_) {
            ARBDirectStateAccess.glNamedBufferData(p_408499_, p_408856_, p_409210_);
        }

        @Override
        void bufferSubData(int p_410046_, int p_406234_, ByteBuffer p_405869_) {
            ARBDirectStateAccess.glNamedBufferSubData(p_410046_, (long)p_406234_, p_405869_);
        }

        @Override
        void bufferStorage(int p_406265_, long p_409759_, int p_409653_) {
            ARBDirectStateAccess.glNamedBufferStorage(p_406265_, p_409759_, p_409653_);
        }

        @Override
        void bufferStorage(int p_410353_, ByteBuffer p_407466_, int p_405931_) {
            ARBDirectStateAccess.glNamedBufferStorage(p_410353_, p_407466_, p_405931_);
        }

        @Nullable
        @Override
        ByteBuffer mapBufferRange(int p_406865_, int p_408097_, int p_405964_, int p_406272_) {
            return ARBDirectStateAccess.glMapNamedBufferRange(p_406865_, p_408097_, p_405964_, p_406272_);
        }

        @Override
        void unmapBuffer(int p_406826_) {
            ARBDirectStateAccess.glUnmapNamedBuffer(p_406826_);
        }

        @Override
        public int createFrameBufferObject() {
            return ARBDirectStateAccess.glCreateFramebuffers();
        }

        @Override
        public void bindFrameBufferTextures(int p_396835_, int p_394736_, int p_395996_, int p_397932_, int p_396105_) {
            ARBDirectStateAccess.glNamedFramebufferTexture(p_396835_, 36064, p_394736_, p_397932_);
            ARBDirectStateAccess.glNamedFramebufferTexture(p_396835_, 36096, p_395996_, p_397932_);
            if (p_396105_ != 0) {
                GlStateManager._glBindFramebuffer(p_396105_, p_396835_);
            }
        }

        @Override
        public void blitFrameBuffers(
            int p_395353_,
            int p_395149_,
            int p_393964_,
            int p_395294_,
            int p_395276_,
            int p_391710_,
            int p_393525_,
            int p_396971_,
            int p_392279_,
            int p_396123_,
            int p_397974_,
            int p_391707_
        ) {
            ARBDirectStateAccess.glBlitNamedFramebuffer(
                p_395353_, p_395149_, p_393964_, p_395294_, p_395276_, p_391710_, p_393525_, p_396971_, p_392279_, p_396123_, p_397974_, p_391707_
            );
        }

        @Override
        void flushMappedBufferRange(int p_406350_, int p_410098_, int p_408561_) {
            ARBDirectStateAccess.glFlushMappedNamedBufferRange(p_406350_, p_410098_, p_408561_);
        }

        @Override
        void copyBufferSubData(int p_410798_, int p_410787_, int p_410782_, int p_410793_, int p_410802_) {
            ARBDirectStateAccess.glCopyNamedBufferSubData(p_410798_, p_410787_, p_410782_, p_410793_, p_410802_);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Emulated extends DirectStateAccess {
        @Override
        int createBuffer() {
            return GlStateManager._glGenBuffers();
        }

        @Override
        void bufferData(int p_405846_, long p_409202_, int p_407040_) {
            GlStateManager._glBindBuffer(36663, p_405846_);
            GlStateManager._glBufferData(36663, p_409202_, GlConst.bufferUsageToGlEnum(p_407040_));
            GlStateManager._glBindBuffer(36663, 0);
        }

        @Override
        void bufferData(int p_409142_, ByteBuffer p_410122_, int p_407720_) {
            GlStateManager._glBindBuffer(36663, p_409142_);
            GlStateManager._glBufferData(36663, p_410122_, GlConst.bufferUsageToGlEnum(p_407720_));
            GlStateManager._glBindBuffer(36663, 0);
        }

        @Override
        void bufferSubData(int p_409504_, int p_410589_, ByteBuffer p_406903_) {
            GlStateManager._glBindBuffer(36663, p_409504_);
            GlStateManager._glBufferSubData(36663, p_410589_, p_406903_);
            GlStateManager._glBindBuffer(36663, 0);
        }

        @Override
        void bufferStorage(int p_407779_, long p_407640_, int p_407863_) {
            GlStateManager._glBindBuffer(36663, p_407779_);
            ARBBufferStorage.glBufferStorage(36663, p_407640_, p_407863_);
            GlStateManager._glBindBuffer(36663, 0);
        }

        @Override
        void bufferStorage(int p_406307_, ByteBuffer p_407965_, int p_407922_) {
            GlStateManager._glBindBuffer(36663, p_406307_);
            ARBBufferStorage.glBufferStorage(36663, p_407965_, p_407922_);
            GlStateManager._glBindBuffer(36663, 0);
        }

        @Nullable
        @Override
        ByteBuffer mapBufferRange(int p_408912_, int p_407523_, int p_406540_, int p_409031_) {
            GlStateManager._glBindBuffer(36663, p_408912_);
            ByteBuffer bytebuffer = GlStateManager._glMapBufferRange(36663, p_407523_, p_406540_, p_409031_);
            GlStateManager._glBindBuffer(36663, 0);
            return bytebuffer;
        }

        @Override
        void unmapBuffer(int p_409724_) {
            GlStateManager._glBindBuffer(36663, p_409724_);
            GlStateManager._glUnmapBuffer(36663);
            GlStateManager._glBindBuffer(36663, 0);
        }

        @Override
        void flushMappedBufferRange(int p_407800_, int p_406734_, int p_407077_) {
            GlStateManager._glBindBuffer(36663, p_407800_);
            GL30.glFlushMappedBufferRange(36663, p_406734_, p_407077_);
            GlStateManager._glBindBuffer(36663, 0);
        }

        @Override
        void copyBufferSubData(int p_410808_, int p_410801_, int p_410784_, int p_410780_, int p_410803_) {
            GlStateManager._glBindBuffer(36662, p_410808_);
            GlStateManager._glBindBuffer(36663, p_410801_);
            GL31.glCopyBufferSubData(36662, 36663, p_410784_, p_410780_, p_410803_);
            GlStateManager._glBindBuffer(36662, 0);
            GlStateManager._glBindBuffer(36663, 0);
        }

        @Override
        public int createFrameBufferObject() {
            return GlStateManager.glGenFramebuffers();
        }

        @Override
        public void bindFrameBufferTextures(int p_397405_, int p_395460_, int p_393875_, int p_393114_, int p_397703_) {
            int i = p_397703_ == 0 ? '\u8ca9' : p_397703_;
            int j = GlStateManager.getFrameBuffer(i);
            GlStateManager._glBindFramebuffer(i, p_397405_);
            GlStateManager._glFramebufferTexture2D(i, 36064, 3553, p_395460_, p_393114_);
            GlStateManager._glFramebufferTexture2D(i, 36096, 3553, p_393875_, p_393114_);
            if (p_397703_ == 0) {
                GlStateManager._glBindFramebuffer(i, j);
            }
        }

        @Override
        public void blitFrameBuffers(
            int p_396366_,
            int p_393343_,
            int p_397226_,
            int p_396156_,
            int p_397178_,
            int p_396414_,
            int p_397943_,
            int p_396165_,
            int p_394958_,
            int p_393756_,
            int p_393868_,
            int p_394611_
        ) {
            int i = GlStateManager.getFrameBuffer(36008);
            int j = GlStateManager.getFrameBuffer(36009);
            GlStateManager._glBindFramebuffer(36008, p_396366_);
            GlStateManager._glBindFramebuffer(36009, p_393343_);
            GlStateManager._glBlitFrameBuffer(p_397226_, p_396156_, p_397178_, p_396414_, p_397943_, p_396165_, p_394958_, p_393756_, p_393868_, p_394611_);
            GlStateManager._glBindFramebuffer(36008, i);
            GlStateManager._glBindFramebuffer(36009, j);
        }
    }
}
