package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
public class WorldBorderRenderer {
    public static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
    private boolean needsRebuild = true;
    private double lastMinX;
    private double lastMinZ;
    private double lastBorderMinX;
    private double lastBorderMaxX;
    private double lastBorderMinZ;
    private double lastBorderMaxZ;
    private final GpuBuffer worldBorderBuffer = RenderSystem.getDevice()
        .createBuffer(() -> "World border vertex buffer", 40, 16 * DefaultVertexFormat.POSITION_TEX.getVertexSize());
    private final RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);

    private void rebuildWorldBorderBuffer(WorldBorder p_393835_, double p_393396_, double p_397561_, double p_391860_, float p_396982_, float p_396911_, float p_392846_) {
        try (ByteBufferBuilder bytebufferbuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4 * 4)) {
            double d0 = p_393835_.getMinX();
            double d1 = p_393835_.getMaxX();
            double d2 = p_393835_.getMinZ();
            double d3 = p_393835_.getMaxZ();
            double d4 = Math.max((double)Mth.floor(p_397561_ - p_393396_), d2);
            double d5 = Math.min((double)Mth.ceil(p_397561_ + p_393396_), d3);
            float f = (Mth.floor(d4) & 1) * 0.5F;
            float f1 = (float)(d5 - d4) / 2.0F;
            double d6 = Math.max((double)Mth.floor(p_391860_ - p_393396_), d0);
            double d7 = Math.min((double)Mth.ceil(p_391860_ + p_393396_), d1);
            float f2 = (Mth.floor(d6) & 1) * 0.5F;
            float f3 = (float)(d7 - d6) / 2.0F;
            BufferBuilder bufferbuilder = new BufferBuilder(bytebufferbuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.addVertex(0.0F, -p_396982_, (float)(d3 - d4)).setUv(f2, p_396911_);
            bufferbuilder.addVertex((float)(d7 - d6), -p_396982_, (float)(d3 - d4)).setUv(f3 + f2, p_396911_);
            bufferbuilder.addVertex((float)(d7 - d6), p_396982_, (float)(d3 - d4)).setUv(f3 + f2, p_392846_);
            bufferbuilder.addVertex(0.0F, p_396982_, (float)(d3 - d4)).setUv(f2, p_392846_);
            bufferbuilder.addVertex(0.0F, -p_396982_, 0.0F).setUv(f, p_396911_);
            bufferbuilder.addVertex(0.0F, -p_396982_, (float)(d5 - d4)).setUv(f1 + f, p_396911_);
            bufferbuilder.addVertex(0.0F, p_396982_, (float)(d5 - d4)).setUv(f1 + f, p_392846_);
            bufferbuilder.addVertex(0.0F, p_396982_, 0.0F).setUv(f, p_392846_);
            bufferbuilder.addVertex((float)(d7 - d6), -p_396982_, 0.0F).setUv(f2, p_396911_);
            bufferbuilder.addVertex(0.0F, -p_396982_, 0.0F).setUv(f3 + f2, p_396911_);
            bufferbuilder.addVertex(0.0F, p_396982_, 0.0F).setUv(f3 + f2, p_392846_);
            bufferbuilder.addVertex((float)(d7 - d6), p_396982_, 0.0F).setUv(f2, p_392846_);
            bufferbuilder.addVertex((float)(d1 - d6), -p_396982_, (float)(d5 - d4)).setUv(f, p_396911_);
            bufferbuilder.addVertex((float)(d1 - d6), -p_396982_, 0.0F).setUv(f1 + f, p_396911_);
            bufferbuilder.addVertex((float)(d1 - d6), p_396982_, 0.0F).setUv(f1 + f, p_392846_);
            bufferbuilder.addVertex((float)(d1 - d6), p_396982_, (float)(d5 - d4)).setUv(f, p_392846_);

            try (MeshData meshdata = bufferbuilder.buildOrThrow()) {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.worldBorderBuffer.slice(), meshdata.vertexBuffer());
            }

            this.lastBorderMinX = d0;
            this.lastBorderMaxX = d1;
            this.lastBorderMinZ = d2;
            this.lastBorderMaxZ = d3;
            this.lastMinX = d6;
            this.lastMinZ = d4;
            this.needsRebuild = false;
        }
    }

    public void render(WorldBorder p_366746_, Vec3 p_368400_, double p_360813_, double p_369225_) {
        double d0 = p_366746_.getMinX();
        double d1 = p_366746_.getMaxX();
        double d2 = p_366746_.getMinZ();
        double d3 = p_366746_.getMaxZ();
        if ((
                !(p_368400_.x < d1 - p_360813_)
                    || !(p_368400_.x > d0 + p_360813_)
                    || !(p_368400_.z < d3 - p_360813_)
                    || !(p_368400_.z > d2 + p_360813_)
            )
            && !(p_368400_.x < d0 - p_360813_)
            && !(p_368400_.x > d1 + p_360813_)
            && !(p_368400_.z < d2 - p_360813_)
            && !(p_368400_.z > d3 + p_360813_)) {
            double d4 = 1.0 - p_366746_.getDistanceToBorder(p_368400_.x, p_368400_.z) / p_360813_;
            d4 = Math.pow(d4, 4.0);
            d4 = Mth.clamp(d4, 0.0, 1.0);
            double d5 = p_368400_.x;
            double d6 = p_368400_.z;
            float f = (float)p_369225_;
            int i = p_366746_.getStatus().getColor();
            float f1 = ARGB.red(i) / 255.0F;
            float f2 = ARGB.green(i) / 255.0F;
            float f3 = ARGB.blue(i) / 255.0F;
            float f4 = (float)(Util.getMillis() % 3000L) / 3000.0F;
            float f5 = (float)(-Mth.frac(p_368400_.y * 0.5));
            float f6 = f5 + f;
            if (this.shouldRebuildWorldBorderBuffer(p_366746_)) {
                this.rebuildWorldBorderBuffer(p_366746_, p_360813_, d6, d5, f, f6, f5);
            }

            TextureManager texturemanager = Minecraft.getInstance().getTextureManager();
            AbstractTexture abstracttexture = texturemanager.getTexture(FORCEFIELD_LOCATION);
            abstracttexture.setUseMipmaps(false);
            RenderPipeline renderpipeline = RenderPipelines.WORLD_BORDER;
            RenderTarget rendertarget = Minecraft.getInstance().getMainRenderTarget();
            RenderTarget rendertarget1 = Minecraft.getInstance().levelRenderer.getWeatherTarget();
            GpuTextureView gputextureview;
            GpuTextureView gputextureview1;
            if (rendertarget1 != null) {
                gputextureview = rendertarget1.getColorTextureView();
                gputextureview1 = rendertarget1.getDepthTextureView();
            } else {
                gputextureview = rendertarget.getColorTextureView();
                gputextureview1 = rendertarget.getDepthTextureView();
            }

            GpuBuffer gpubuffer = this.indices.getBuffer(6);
            GpuBufferSlice gpubufferslice = RenderSystem.getDynamicUniforms()
                .writeTransform(
                    RenderSystem.getModelViewMatrix(),
                    new Vector4f(f1, f2, f3, (float)d4),
                    new Vector3f((float)(this.lastMinX - d5), (float)(-p_368400_.y), (float)(this.lastMinZ - d6)),
                    new Matrix4f().translation(f4, f4, 0.0F),
                    0.0F
                );

            try (RenderPass renderpass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "World border", gputextureview, OptionalInt.empty(), gputextureview1, OptionalDouble.empty())) {
                renderpass.setPipeline(renderpipeline);
                RenderSystem.bindDefaultUniforms(renderpass);
                renderpass.setUniform("DynamicTransforms", gpubufferslice);
                renderpass.setIndexBuffer(gpubuffer, this.indices.type());
                renderpass.bindSampler("Sampler0", abstracttexture.getTextureView());
                renderpass.setVertexBuffer(0, this.worldBorderBuffer);
                ArrayList<RenderPass.Draw<WorldBorderRenderer>> arraylist = new ArrayList<>();

                for (WorldBorder.DistancePerDirection worldborder$distanceperdirection : p_366746_.closestBorder(d5, d6)) {
                    if (worldborder$distanceperdirection.distance() < p_360813_) {
                        int j = worldborder$distanceperdirection.direction().get2DDataValue();
                        arraylist.add(new RenderPass.Draw<>(0, this.worldBorderBuffer, gpubuffer, this.indices.type(), 6 * j, 6));
                    }
                }

                renderpass.drawMultipleIndexed(arraylist, null, null, Collections.emptyList(), this);
            }
        }
    }

    public void invalidate() {
        this.needsRebuild = true;
    }

    private boolean shouldRebuildWorldBorderBuffer(WorldBorder p_391592_) {
        return this.needsRebuild
            || p_391592_.getMinX() != this.lastBorderMinX
            || p_391592_.getMinZ() != this.lastBorderMinZ
            || p_391592_.getMaxX() != this.lastBorderMaxX
            || p_391592_.getMaxZ() != this.lastBorderMaxZ;
    }
}
