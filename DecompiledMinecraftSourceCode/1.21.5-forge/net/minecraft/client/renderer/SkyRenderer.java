package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
public class SkyRenderer implements AutoCloseable {
    private static final ResourceLocation SUN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    public static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final float SKY_DISC_RADIUS = 512.0F;
    private static final int SKY_VERTICES = 10;
    private static final int STAR_COUNT = 1500;
    private static final int END_SKY_QUAD_COUNT = 6;
    private final GpuBuffer starBuffer;
    private final RenderSystem.AutoStorageIndexBuffer starIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
    private final GpuBuffer topSkyBuffer;
    private final GpuBuffer bottomSkyBuffer;
    private final GpuBuffer endSkyBuffer;
    private int starIndexCount;

    public SkyRenderer() {
        this.starBuffer = this.buildStars();
        this.endSkyBuffer = buildEndSky();

        try (ByteBufferBuilder bytebufferbuilder = ByteBufferBuilder.exactlySized(10 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder bufferbuilder = new BufferBuilder(bytebufferbuilder, VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            this.buildSkyDisc(bufferbuilder, 16.0F);

            try (MeshData meshdata = bufferbuilder.buildOrThrow()) {
                this.topSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Top sky vertex buffer", 32, meshdata.vertexBuffer());
            }

            bufferbuilder = new BufferBuilder(bytebufferbuilder, VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            this.buildSkyDisc(bufferbuilder, -16.0F);

            try (MeshData meshdata1 = bufferbuilder.buildOrThrow()) {
                this.bottomSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Bottom sky vertex buffer", 32, meshdata1.vertexBuffer());
            }
        }
    }

    private GpuBuffer buildStars() {
        RandomSource randomsource = RandomSource.create(10842L);
        float f = 100.0F;

        GpuBuffer gpubuffer;
        try (ByteBufferBuilder bytebufferbuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 1500 * 4)) {
            BufferBuilder bufferbuilder = new BufferBuilder(bytebufferbuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

            for (int i = 0; i < 1500; i++) {
                float f1 = randomsource.nextFloat() * 2.0F - 1.0F;
                float f2 = randomsource.nextFloat() * 2.0F - 1.0F;
                float f3 = randomsource.nextFloat() * 2.0F - 1.0F;
                float f4 = 0.15F + randomsource.nextFloat() * 0.1F;
                float f5 = Mth.lengthSquared(f1, f2, f3);
                if (!(f5 <= 0.010000001F) && !(f5 >= 1.0F)) {
                    Vector3f vector3f = new Vector3f(f1, f2, f3).normalize(100.0F);
                    float f6 = (float)(randomsource.nextDouble() * (float) Math.PI * 2.0);
                    Matrix3f matrix3f = new Matrix3f().rotateTowards(new Vector3f(vector3f).negate(), new Vector3f(0.0F, 1.0F, 0.0F)).rotateZ(-f6);
                    bufferbuilder.addVertex(new Vector3f(f4, -f4, 0.0F).mul(matrix3f).add(vector3f));
                    bufferbuilder.addVertex(new Vector3f(f4, f4, 0.0F).mul(matrix3f).add(vector3f));
                    bufferbuilder.addVertex(new Vector3f(-f4, f4, 0.0F).mul(matrix3f).add(vector3f));
                    bufferbuilder.addVertex(new Vector3f(-f4, -f4, 0.0F).mul(matrix3f).add(vector3f));
                }
            }

            try (MeshData meshdata = bufferbuilder.buildOrThrow()) {
                this.starIndexCount = meshdata.drawState().indexCount();
                gpubuffer = RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer", 40, meshdata.vertexBuffer());
            }
        }

        return gpubuffer;
    }

    private void buildSkyDisc(VertexConsumer p_375466_, float p_363584_) {
        float f = Math.signum(p_363584_) * 512.0F;
        p_375466_.addVertex(0.0F, p_363584_, 0.0F);

        for (int i = -180; i <= 180; i += 45) {
            p_375466_.addVertex(f * Mth.cos(i * (float) (Math.PI / 180.0)), p_363584_, 512.0F * Mth.sin(i * (float) (Math.PI / 180.0)));
        }
    }

    public void renderSkyDisc(float p_369198_, float p_369913_, float p_362432_) {
        GpuBufferSlice gpubufferslice = RenderSystem.getDynamicUniforms()
            .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(p_369198_, p_369913_, p_362432_, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
        GpuTextureView gputextureview = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView gputextureview1 = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

        try (RenderPass renderpass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky disc", gputextureview, OptionalInt.empty(), gputextureview1, OptionalDouble.empty())) {
            renderpass.setPipeline(RenderPipelines.SKY);
            RenderSystem.bindDefaultUniforms(renderpass);
            renderpass.setUniform("DynamicTransforms", gpubufferslice);
            renderpass.setVertexBuffer(0, this.topSkyBuffer);
            renderpass.draw(0, 10);
        }
    }

    public void renderDarkDisc() {
        Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
        matrix4fstack.pushMatrix();
        matrix4fstack.translate(0.0F, 12.0F, 0.0F);
        GpuBufferSlice gpubufferslice = RenderSystem.getDynamicUniforms()
            .writeTransform(matrix4fstack, new Vector4f(0.0F, 0.0F, 0.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
        GpuTextureView gputextureview = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView gputextureview1 = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

        try (RenderPass renderpass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky dark", gputextureview, OptionalInt.empty(), gputextureview1, OptionalDouble.empty())) {
            renderpass.setPipeline(RenderPipelines.SKY);
            RenderSystem.bindDefaultUniforms(renderpass);
            renderpass.setUniform("DynamicTransforms", gpubufferslice);
            renderpass.setVertexBuffer(0, this.bottomSkyBuffer);
            renderpass.draw(0, 10);
        }

        matrix4fstack.popMatrix();
    }

    public void renderSunMoonAndStars(PoseStack p_362673_, MultiBufferSource.BufferSource p_376689_, float p_369057_, int p_364932_, float p_366540_, float p_368016_) {
        p_362673_.pushPose();
        p_362673_.mulPose(Axis.YP.rotationDegrees(-90.0F));
        p_362673_.mulPose(Axis.XP.rotationDegrees(p_369057_ * 360.0F));
        this.renderSun(p_366540_, p_376689_, p_362673_);
        this.renderMoon(p_364932_, p_366540_, p_376689_, p_362673_);
        p_376689_.endBatch();
        if (p_368016_ > 0.0F) {
            this.renderStars(p_368016_, p_362673_);
        }

        p_362673_.popPose();
    }

    private void renderSun(float p_363755_, MultiBufferSource p_376565_, PoseStack p_369287_) {
        float f = 30.0F;
        float f1 = 100.0F;
        VertexConsumer vertexconsumer = p_376565_.getBuffer(RenderType.celestial(SUN_LOCATION));
        int i = ARGB.white(p_363755_);
        Matrix4f matrix4f = p_369287_.last().pose();
        vertexconsumer.addVertex(matrix4f, -30.0F, 100.0F, -30.0F).setUv(0.0F, 0.0F).setColor(i);
        vertexconsumer.addVertex(matrix4f, 30.0F, 100.0F, -30.0F).setUv(1.0F, 0.0F).setColor(i);
        vertexconsumer.addVertex(matrix4f, 30.0F, 100.0F, 30.0F).setUv(1.0F, 1.0F).setColor(i);
        vertexconsumer.addVertex(matrix4f, -30.0F, 100.0F, 30.0F).setUv(0.0F, 1.0F).setColor(i);
    }

    private void renderMoon(int p_367893_, float p_364034_, MultiBufferSource p_377520_, PoseStack p_369177_) {
        float f = 20.0F;
        int i = p_367893_ % 4;
        int j = p_367893_ / 4 % 2;
        float f1 = (i + 0) / 4.0F;
        float f2 = (j + 0) / 2.0F;
        float f3 = (i + 1) / 4.0F;
        float f4 = (j + 1) / 2.0F;
        float f5 = 100.0F;
        VertexConsumer vertexconsumer = p_377520_.getBuffer(RenderType.celestial(MOON_LOCATION));
        int k = ARGB.white(p_364034_);
        Matrix4f matrix4f = p_369177_.last().pose();
        vertexconsumer.addVertex(matrix4f, -20.0F, -100.0F, 20.0F).setUv(f3, f4).setColor(k);
        vertexconsumer.addVertex(matrix4f, 20.0F, -100.0F, 20.0F).setUv(f1, f4).setColor(k);
        vertexconsumer.addVertex(matrix4f, 20.0F, -100.0F, -20.0F).setUv(f1, f2).setColor(k);
        vertexconsumer.addVertex(matrix4f, -20.0F, -100.0F, -20.0F).setUv(f3, f2).setColor(k);
    }

    private void renderStars(float p_361462_, PoseStack p_364130_) {
        Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
        matrix4fstack.pushMatrix();
        matrix4fstack.mul(p_364130_.last().pose());
        RenderPipeline renderpipeline = RenderPipelines.STARS;
        GpuTextureView gputextureview = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView gputextureview1 = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer gpubuffer = this.starIndices.getBuffer(this.starIndexCount);
        GpuBufferSlice gpubufferslice = RenderSystem.getDynamicUniforms()
            .writeTransform(matrix4fstack, new Vector4f(p_361462_, p_361462_, p_361462_, p_361462_), new Vector3f(), new Matrix4f(), 0.0F);

        try (RenderPass renderpass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Stars", gputextureview, OptionalInt.empty(), gputextureview1, OptionalDouble.empty())) {
            renderpass.setPipeline(renderpipeline);
            RenderSystem.bindDefaultUniforms(renderpass);
            renderpass.setUniform("DynamicTransforms", gpubufferslice);
            renderpass.setVertexBuffer(0, this.starBuffer);
            renderpass.setIndexBuffer(gpubuffer, this.starIndices.type());
            renderpass.drawIndexed(0, 0, this.starIndexCount, 1);
        }

        matrix4fstack.popMatrix();
    }

    public void renderSunriseAndSunset(PoseStack p_365939_, MultiBufferSource.BufferSource p_377149_, float p_368996_, int p_365467_) {
        p_365939_.pushPose();
        p_365939_.mulPose(Axis.XP.rotationDegrees(90.0F));
        float f = Mth.sin(p_368996_) < 0.0F ? 180.0F : 0.0F;
        p_365939_.mulPose(Axis.ZP.rotationDegrees(f));
        p_365939_.mulPose(Axis.ZP.rotationDegrees(90.0F));
        Matrix4f matrix4f = p_365939_.last().pose();
        VertexConsumer vertexconsumer = p_377149_.getBuffer(RenderType.sunriseSunset());
        float f1 = ARGB.alphaFloat(p_365467_);
        vertexconsumer.addVertex(matrix4f, 0.0F, 100.0F, 0.0F).setColor(p_365467_);
        int i = ARGB.transparent(p_365467_);
        int j = 16;

        for (int k = 0; k <= 16; k++) {
            float f2 = k * (float) (Math.PI * 2) / 16.0F;
            float f3 = Mth.sin(f2);
            float f4 = Mth.cos(f2);
            vertexconsumer.addVertex(matrix4f, f3 * 120.0F, f4 * 120.0F, -f4 * 40.0F * f1).setColor(i);
        }

        p_365939_.popPose();
    }

    private static GpuBuffer buildEndSky() {
        GpuBuffer gpubuffer;
        try (ByteBufferBuilder bytebufferbuilder = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
            BufferBuilder bufferbuilder = new BufferBuilder(bytebufferbuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            for (int i = 0; i < 6; i++) {
                Matrix4f matrix4f = new Matrix4f();
                switch (i) {
                    case 1:
                        matrix4f.rotationX((float) (Math.PI / 2));
                        break;
                    case 2:
                        matrix4f.rotationX((float) (-Math.PI / 2));
                        break;
                    case 3:
                        matrix4f.rotationX((float) Math.PI);
                        break;
                    case 4:
                        matrix4f.rotationZ((float) (Math.PI / 2));
                        break;
                    case 5:
                        matrix4f.rotationZ((float) (-Math.PI / 2));
                }

                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(-14145496);
                bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(-14145496);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(-14145496);
                bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(-14145496);
            }

            try (MeshData meshdata = bufferbuilder.buildOrThrow()) {
                gpubuffer = RenderSystem.getDevice().createBuffer(() -> "End sky vertex buffer", 40, meshdata.vertexBuffer());
            }
        }

        return gpubuffer;
    }

    public void renderEndSky() {
        TextureManager texturemanager = Minecraft.getInstance().getTextureManager();
        AbstractTexture abstracttexture = texturemanager.getTexture(END_SKY_LOCATION);
        abstracttexture.setUseMipmaps(false);
        RenderSystem.AutoStorageIndexBuffer rendersystem$autostorageindexbuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer gpubuffer = rendersystem$autostorageindexbuffer.getBuffer(36);
        GpuTextureView gputextureview = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView gputextureview1 = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBufferSlice gpubufferslice = RenderSystem.getDynamicUniforms()
            .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);

        try (RenderPass renderpass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "End sky", gputextureview, OptionalInt.empty(), gputextureview1, OptionalDouble.empty())) {
            renderpass.setPipeline(RenderPipelines.END_SKY);
            RenderSystem.bindDefaultUniforms(renderpass);
            renderpass.setUniform("DynamicTransforms", gpubufferslice);
            renderpass.bindSampler("Sampler0", abstracttexture.getTextureView());
            renderpass.setVertexBuffer(0, this.endSkyBuffer);
            renderpass.setIndexBuffer(gpubuffer, rendersystem$autostorageindexbuffer.type());
            renderpass.drawIndexed(0, 0, 36, 1);
        }
    }

    @Override
    public void close() {
        this.starBuffer.close();
        this.topSkyBuffer.close();
        this.bottomSkyBuffer.close();
        this.endSkyBuffer.close();
    }
}
