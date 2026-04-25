package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class TextureAtlas extends AbstractTexture implements Dumpable, Tickable {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Deprecated
    public static final ResourceLocation LOCATION_BLOCKS = ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    @Deprecated
    public static final ResourceLocation LOCATION_PARTICLES = ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");
    private List<SpriteContents> sprites = List.of();
    private List<TextureAtlasSprite.Ticker> animatedTextures = List.of();
    private Map<ResourceLocation, TextureAtlasSprite> texturesByName = Map.of();
    @Nullable
    private TextureAtlasSprite missingSprite;
    private final ResourceLocation location;
    private final int maxSupportedTextureSize;
    private int width;
    private int height;
    private int mipLevel;

    public TextureAtlas(ResourceLocation p_118269_) {
        this.location = p_118269_;
        this.maxSupportedTextureSize = RenderSystem.getDevice().getMaxTextureSize();
    }

    private void createTexture(int p_410800_, int p_410805_, int p_410791_) {
        LOGGER.info("Created: {}x{}x{} {}-atlas", p_410800_, p_410805_, p_410791_, this.location);
        GpuDevice gpudevice = RenderSystem.getDevice();
        this.close();
        this.texture = gpudevice.createTexture(this.location::toString, 7, TextureFormat.RGBA8, p_410800_, p_410805_, 1, p_410791_ + 1);
        this.textureView = gpudevice.createTextureView(this.texture);
        this.width = p_410800_;
        this.height = p_410805_;
        this.mipLevel = p_410791_;
    }

    public void upload(SpriteLoader.Preparations p_250662_) {
        this.createTexture(p_250662_.width(), p_250662_.height(), p_250662_.mipLevel());
        this.clearTextureData();
        this.setFilter(false, this.mipLevel > 1);
        this.texturesByName = Map.copyOf(p_250662_.regions());
        this.missingSprite = this.texturesByName.get(MissingTextureAtlasSprite.getLocation());
        if (this.missingSprite == null) {
            throw new IllegalStateException("Atlas '" + this.location + "' (" + this.texturesByName.size() + " sprites) has no missing texture sprite");
        } else {
            List<SpriteContents> list = new ArrayList<>();
            List<TextureAtlasSprite.Ticker> list1 = new ArrayList<>();

            for (TextureAtlasSprite textureatlassprite : p_250662_.regions().values()) {
                list.add(textureatlassprite.contents());

                try {
                    textureatlassprite.uploadFirstFrame(this.texture);
                } catch (Throwable throwable) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable, "Stitching texture atlas");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Texture being stitched together");
                    crashreportcategory.setDetail("Atlas path", this.location);
                    crashreportcategory.setDetail("Sprite", textureatlassprite);
                    throw new ReportedException(crashreport);
                }

                TextureAtlasSprite.Ticker textureatlassprite$ticker = textureatlassprite.createTicker();
                if (textureatlassprite$ticker != null) {
                    list1.add(textureatlassprite$ticker);
                }
            }

            this.sprites = List.copyOf(list);
            this.animatedTextures = List.copyOf(list1);
        }
        net.minecraftforge.client.ForgeHooksClient.onTextureStitchedPost(this);
    }

    @Override
    public void dumpContents(ResourceLocation p_276106_, Path p_276127_) throws IOException {
        String s = p_276106_.toDebugFileName();
        TextureUtil.writeAsPNG(p_276127_, s, this.getTexture(), this.mipLevel, p_395843_ -> p_395843_);
        dumpSpriteNames(p_276127_, s, this.texturesByName);
    }

    private static void dumpSpriteNames(Path p_261769_, String p_262102_, Map<ResourceLocation, TextureAtlasSprite> p_261722_) {
        Path path = p_261769_.resolve(p_262102_ + ".txt");

        try (Writer writer = Files.newBufferedWriter(path)) {
            for (Entry<ResourceLocation, TextureAtlasSprite> entry : p_261722_.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
                TextureAtlasSprite textureatlassprite = entry.getValue();
                writer.write(
                    String.format(
                        Locale.ROOT,
                        "%s\tx=%d\ty=%d\tw=%d\th=%d%n",
                        entry.getKey(),
                        textureatlassprite.getX(),
                        textureatlassprite.getY(),
                        textureatlassprite.contents().width(),
                        textureatlassprite.contents().height()
                    )
                );
            }
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to write file {}", path, ioexception);
        }
    }

    public void cycleAnimationFrames() {
        if (this.texture != null) {
            for (TextureAtlasSprite.Ticker textureatlassprite$ticker : this.animatedTextures) {
                textureatlassprite$ticker.tickAndUpload(this.texture);
            }
        }
    }

    @Override
    public void tick() {
        this.cycleAnimationFrames();
    }

    public TextureAtlasSprite getSprite(ResourceLocation p_118317_) {
        TextureAtlasSprite textureatlassprite = this.texturesByName.getOrDefault(p_118317_, this.missingSprite);
        if (textureatlassprite == null) {
            throw new IllegalStateException("Tried to lookup sprite, but atlas is not initialized");
        } else {
            return textureatlassprite;
        }
    }

    public void clearTextureData() {
        this.sprites.forEach(SpriteContents::close);
        this.animatedTextures.forEach(TextureAtlasSprite.Ticker::close);
        this.sprites = List.of();
        this.animatedTextures = List.of();
        this.texturesByName = Map.of();
        this.missingSprite = null;
    }

    public ResourceLocation location() {
        return this.location;
    }

    public int maxSupportedTextureSize() {
        return this.maxSupportedTextureSize;
    }

    int getWidth() {
        return this.width;
    }

    int getHeight() {
        return this.height;
    }

    /** {@return the set of sprites in this atlas} */
    public java.util.Set<ResourceLocation> getTextureLocations() {
        return java.util.Collections.unmodifiableSet(texturesByName.keySet());
    }
}
