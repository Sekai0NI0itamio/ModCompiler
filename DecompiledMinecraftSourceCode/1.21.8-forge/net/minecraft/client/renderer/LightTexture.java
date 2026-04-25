package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class LightTexture implements AutoCloseable {
    public static final int FULL_BRIGHT = 15728880;
    public static final int FULL_SKY = 15728640;
    public static final int FULL_BLOCK = 240;
    private static final int TEXTURE_SIZE = 16;
    private static final int LIGHTMAP_UBO_SIZE = new Std140SizeCalculator()
        .putFloat()
        .putFloat()
        .putFloat()
        .putInt()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putVec3()
        .get();
    private final GpuTexture texture;
    private final GpuTextureView textureView;
    private boolean updateLightTexture;
    private float blockLightRedFlicker;
    private final GameRenderer renderer;
    private final Minecraft minecraft;
    private final MappableRingBuffer ubo;

    public LightTexture(GameRenderer p_109878_, Minecraft p_109879_) {
        this.renderer = p_109878_;
        this.minecraft = p_109879_;
        GpuDevice gpudevice = RenderSystem.getDevice();
        this.texture = gpudevice.createTexture("Light Texture", 12, TextureFormat.RGBA8, 16, 16, 1, 1);
        this.texture.setTextureFilter(FilterMode.LINEAR, false);
        this.textureView = gpudevice.createTextureView(this.texture);
        gpudevice.createCommandEncoder().clearColorTexture(this.texture, -1);
        this.ubo = new MappableRingBuffer(() -> "Lightmap UBO", 130, LIGHTMAP_UBO_SIZE);
    }

    public GpuTextureView getTextureView() {
        return this.textureView;
    }

    @Override
    public void close() {
        this.texture.close();
        this.textureView.close();
        this.ubo.close();
    }

    public void tick() {
        this.blockLightRedFlicker = this.blockLightRedFlicker + (float)((Math.random() - Math.random()) * Math.random() * Math.random() * 0.1);
        this.blockLightRedFlicker *= 0.9F;
        this.updateLightTexture = true;
    }

    public void turnOffLightLayer() {
        RenderSystem.setShaderTexture(2, null);
    }

    public void turnOnLightLayer() {
        RenderSystem.setShaderTexture(2, this.textureView);
    }

    private float calculateDarknessScale(LivingEntity p_234313_, float p_234314_, float p_234315_) {
        float f = 0.45F * p_234314_;
        return Math.max(0.0F, Mth.cos((p_234313_.tickCount - p_234315_) * (float) Math.PI * 0.025F) * f);
    }

    public void updateLightTexture(float p_109882_) {
        if (this.updateLightTexture) {
            this.updateLightTexture = false;
            ProfilerFiller profilerfiller = Profiler.get();
            profilerfiller.push("lightTex");
            ClientLevel clientlevel = this.minecraft.level;
            if (clientlevel != null) {
                float f = clientlevel.getSkyDarken(1.0F);
                float f1;
                if (clientlevel.getSkyFlashTime() > 0) {
                    f1 = 1.0F;
                } else {
                    f1 = f * 0.95F + 0.05F;
                }

                float f2 = this.minecraft.options.darknessEffectScale().get().floatValue();
                float f3 = this.minecraft.player.getEffectBlendFactor(MobEffects.DARKNESS, p_109882_) * f2;
                float f4 = this.calculateDarknessScale(this.minecraft.player, f3, p_109882_) * f2;
                float f6 = this.minecraft.player.getWaterVision();
                float f5;
                if (this.minecraft.player.hasEffect(MobEffects.NIGHT_VISION)) {
                    f5 = GameRenderer.getNightVisionScale(this.minecraft.player, p_109882_);
                } else if (f6 > 0.0F && this.minecraft.player.hasEffect(MobEffects.CONDUIT_POWER)) {
                    f5 = f6;
                } else {
                    f5 = 0.0F;
                }

                Vector3f vector3f = new Vector3f(f, f, 1.0F).lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
                float f7 = this.blockLightRedFlicker + 1.5F;
                float f8 = clientlevel.dimensionType().ambientLight();
                boolean flag = clientlevel.effects().forceBrightLightmap();
                float f9 = this.minecraft.options.gamma().get().floatValue();
                RenderSystem.AutoStorageIndexBuffer rendersystem$autostorageindexbuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                GpuBuffer gpubuffer = rendersystem$autostorageindexbuffer.getBuffer(6);
                CommandEncoder commandencoder = RenderSystem.getDevice().createCommandEncoder();

                try (GpuBuffer.MappedView gpubuffer$mappedview = commandencoder.mapBuffer(this.ubo.currentBuffer(), false, true)) {
                    Std140Builder.intoBuffer(gpubuffer$mappedview.data())
                        .putFloat(f8)
                        .putFloat(f1)
                        .putFloat(f7)
                        .putInt(flag ? 1 : 0)
                        .putFloat(f5)
                        .putFloat(f4)
                        .putFloat(this.renderer.getDarkenWorldAmount(p_109882_))
                        .putFloat(Math.max(0.0F, f9 - f3))
                        .putVec3(vector3f);
                }

                try (RenderPass renderpass = commandencoder.createRenderPass(() -> "Update light", this.textureView, OptionalInt.empty())) {
                    renderpass.setPipeline(RenderPipelines.LIGHTMAP);
                    RenderSystem.bindDefaultUniforms(renderpass);
                    renderpass.setUniform("LightmapInfo", this.ubo.currentBuffer());
                    renderpass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer());
                    renderpass.setIndexBuffer(gpubuffer, rendersystem$autostorageindexbuffer.type());
                    renderpass.drawIndexed(0, 0, 6, 1);
                }

                this.ubo.rotate();
                profilerfiller.pop();
            }
        }
    }

    public static float getBrightness(DimensionType p_234317_, int p_234318_) {
        return getBrightness(p_234317_.ambientLight(), p_234318_);
    }

    public static float getBrightness(float p_362774_, int p_368270_) {
        float f = p_368270_ / 15.0F;
        float f1 = f / (4.0F - 3.0F * f);
        return Mth.lerp(p_362774_, f1, 1.0F);
    }

    public static int pack(int p_109886_, int p_109887_) {
        return p_109886_ << 4 | p_109887_ << 20;
    }

    public static int block(int p_109884_) {
        return (p_109884_ & 0xFFFF) >> 4; // Forge: Fix fullbright quads showing dark artifacts. Reported as MC-169806
    }

    public static int sky(int p_109895_) {
        return p_109895_ >>> 20 & 15;
    }

    public static int lightCoordsWithEmission(int p_363075_, int p_361575_) {
        if (p_361575_ == 0) {
            return p_363075_;
        } else {
            int i = Math.max(sky(p_363075_), p_361575_);
            int j = Math.max(block(p_363075_), p_361575_);
            return pack(j, i);
        }
    }
}
