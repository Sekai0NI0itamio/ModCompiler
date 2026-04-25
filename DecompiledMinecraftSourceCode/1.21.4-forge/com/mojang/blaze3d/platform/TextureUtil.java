package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
@DontObfuscate
public class TextureUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MIN_MIPMAP_LEVEL = 0;
    private static final int DEFAULT_IMAGE_BUFFER_SIZE = 8192;

    public static ByteBuffer readResource(InputStream p_85304_) throws IOException {
        ReadableByteChannel readablebytechannel = Channels.newChannel(p_85304_);
        return readablebytechannel instanceof SeekableByteChannel seekablebytechannel
            ? readResource(readablebytechannel, (int)seekablebytechannel.size() + 1)
            : readResource(readablebytechannel, 8192);
    }

    private static ByteBuffer readResource(ReadableByteChannel p_273208_, int p_273297_) throws IOException {
        ByteBuffer bytebuffer = MemoryUtil.memAlloc(p_273297_);

        try {
            while (p_273208_.read(bytebuffer) != -1) {
                if (!bytebuffer.hasRemaining()) {
                    bytebuffer = MemoryUtil.memRealloc(bytebuffer, bytebuffer.capacity() * 2);
                }
            }

            return bytebuffer;
        } catch (IOException ioexception) {
            MemoryUtil.memFree(bytebuffer);
            throw ioexception;
        }
    }

    public static void writeAsPNG(Path p_285286_, String p_285408_, GpuTexture p_396086_, int p_285400_, IntUnaryOperator p_284988_) {
        RenderSystem.assertOnRenderThread();
        int i = 0;

        for (int j = 0; j <= p_285400_; j++) {
            i += p_396086_.getFormat().pixelSize() * p_396086_.getWidth(j) * p_396086_.getHeight(j);
        }

        GpuBuffer gpubuffer = RenderSystem.getDevice().createBuffer(() -> "Texture output buffer", 9, i);
        CommandEncoder commandencoder = RenderSystem.getDevice().createCommandEncoder();
        Runnable runnable = () -> {
            try (GpuBuffer.MappedView gpubuffer$mappedview = commandencoder.mapBuffer(gpubuffer, true, false)) {
                int i1 = 0;

                for (int j1 = 0; j1 <= p_285400_; j1++) {
                    int k1 = p_396086_.getWidth(j1);
                    int l1 = p_396086_.getHeight(j1);

                    try (NativeImage nativeimage = new NativeImage(k1, l1, false)) {
                        for (int i2 = 0; i2 < l1; i2++) {
                            for (int j2 = 0; j2 < k1; j2++) {
                                int k2 = gpubuffer$mappedview.data().getInt(i1 + (j2 + i2 * k1) * p_396086_.getFormat().pixelSize());
                                nativeimage.setPixelABGR(j2, i2, p_284988_.applyAsInt(k2));
                            }
                        }

                        Path path = p_285286_.resolve(p_285408_ + "_" + j1 + ".png");
                        nativeimage.writeToFile(path);
                        LOGGER.debug("Exported png to: {}", path.toAbsolutePath());
                    } catch (IOException ioexception) {
                        LOGGER.debug("Unable to write: ", (Throwable)ioexception);
                    }

                    i1 += p_396086_.getFormat().pixelSize() * k1 * l1;
                }
            }

            gpubuffer.close();
        };
        AtomicInteger atomicinteger = new AtomicInteger();
        int k = 0;

        for (int l = 0; l <= p_285400_; l++) {
            commandencoder.copyTextureToBuffer(p_396086_, gpubuffer, k, () -> {
                if (atomicinteger.getAndIncrement() == p_285400_) {
                    runnable.run();
                }
            }, l);
            k += p_396086_.getFormat().pixelSize() * p_396086_.getWidth(l) * p_396086_.getHeight(l);
        }
    }

    public static Path getDebugTexturePath(Path p_262015_) {
        return p_262015_.resolve("screenshots").resolve("debug");
    }

    public static Path getDebugTexturePath() {
        return getDebugTexturePath(Path.of("."));
    }
}
