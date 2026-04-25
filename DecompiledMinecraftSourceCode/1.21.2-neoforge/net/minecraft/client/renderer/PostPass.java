package net.minecraft.client.renderer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.datafixers.util.Pair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryStack;

@OnlyIn(Dist.CLIENT)
public class PostPass implements AutoCloseable {
    private static final int UBO_SIZE_PER_SAMPLER = new Std140SizeCalculator().putVec2().get();
    private final String name;
    private final RenderPipeline pipeline;
    private final ResourceLocation outputTargetId;
    private final Map<String, GpuBuffer> customUniforms = new HashMap<>();
    private final MappableRingBuffer infoUbo;
    private final List<PostPass.Input> inputs;

    public PostPass(RenderPipeline p_395322_, ResourceLocation p_369053_, Map<String, List<UniformValue>> p_410093_, List<PostPass.Input> p_361905_) {
        this.pipeline = p_395322_;
        this.name = p_395322_.getLocation().toString();
        this.outputTargetId = p_369053_;
        this.inputs = p_361905_;

        for (Entry<String, List<UniformValue>> entry : p_410093_.entrySet()) {
            List<UniformValue> list = entry.getValue();
            if (!list.isEmpty()) {
                Std140SizeCalculator std140sizecalculator = new Std140SizeCalculator();

                for (UniformValue uniformvalue : list) {
                    uniformvalue.addSize(std140sizecalculator);
                }

                int i = std140sizecalculator.get();

                try (MemoryStack memorystack = MemoryStack.stackPush()) {
                    Std140Builder std140builder = Std140Builder.onStack(memorystack, i);

                    for (UniformValue uniformvalue1 : list) {
                        uniformvalue1.writeTo(std140builder);
                    }

                    this.customUniforms
                        .put(entry.getKey(), RenderSystem.getDevice().createBuffer(() -> this.name + " / " + entry.getKey(), 128, std140builder.get()));
                }
            }
        }

        this.infoUbo = new MappableRingBuffer(() -> this.name + " SamplerInfo", 130, (p_361905_.size() + 1) * UBO_SIZE_PER_SAMPLER);
    }

    public void addToFrame(FrameGraphBuilder p_369714_, Map<ResourceLocation, ResourceHandle<RenderTarget>> p_365909_, GpuBufferSlice p_406688_) {
        FramePass framepass = p_369714_.addPass(this.name);

        for (PostPass.Input postpass$input : this.inputs) {
            postpass$input.addToPass(framepass, p_365909_);
        }

        ResourceHandle<RenderTarget> resourcehandle = p_365909_.computeIfPresent(
            this.outputTargetId, (p_366255_, p_363433_) -> framepass.readsAndWrites((ResourceHandle<RenderTarget>)p_363433_)
        );
        if (resourcehandle == null) {
            throw new IllegalStateException("Missing handle for target " + this.outputTargetId);
        } else {
            framepass.executes(
                () -> {
                    RenderTarget rendertarget = resourcehandle.get();
                    RenderSystem.backupProjectionMatrix();
                    RenderSystem.setProjectionMatrix(p_406688_, ProjectionType.ORTHOGRAPHIC);
                    CommandEncoder commandencoder = RenderSystem.getDevice().createCommandEncoder();
                    List<Pair<String, GpuTextureView>> list = this.inputs
                        .stream()
                        .map(p_404938_ -> Pair.of(p_404938_.samplerName(), p_404938_.texture(p_365909_)))
                        .toList();

                    try (GpuBuffer.MappedView gpubuffer$mappedview = commandencoder.mapBuffer(this.infoUbo.currentBuffer(), false, true)) {
                        Std140Builder std140builder = Std140Builder.intoBuffer(gpubuffer$mappedview.data());
                        std140builder.putVec2(rendertarget.width, rendertarget.height);

                        for (Pair<String, GpuTextureView> pair : list) {
                            std140builder.putVec2(pair.getSecond().getWidth(0), pair.getSecond().getHeight(0));
                        }
                    }

                    GpuBuffer gpubuffer = RenderSystem.getQuadVertexBuffer();
                    RenderSystem.AutoStorageIndexBuffer rendersystem$autostorageindexbuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                    GpuBuffer gpubuffer1 = rendersystem$autostorageindexbuffer.getBuffer(6);

                    try (RenderPass renderpass = commandencoder.createRenderPass(
                            () -> "Post pass " + this.name,
                            rendertarget.getColorTextureView(),
                            OptionalInt.empty(),
                            rendertarget.useDepth ? rendertarget.getDepthTextureView() : null,
                            OptionalDouble.empty()
                        )) {
                        renderpass.setPipeline(this.pipeline);
                        RenderSystem.bindDefaultUniforms(renderpass);
                        renderpass.setUniform("SamplerInfo", this.infoUbo.currentBuffer());

                        for (Entry<String, GpuBuffer> entry : this.customUniforms.entrySet()) {
                            renderpass.setUniform(entry.getKey(), entry.getValue());
                        }

                        renderpass.setVertexBuffer(0, gpubuffer);
                        renderpass.setIndexBuffer(gpubuffer1, rendersystem$autostorageindexbuffer.type());

                        for (Pair<String, GpuTextureView> pair1 : list) {
                            renderpass.bindSampler(pair1.getFirst() + "Sampler", pair1.getSecond());
                        }

                        renderpass.drawIndexed(0, 0, 6, 1);
                    }

                    this.infoUbo.rotate();
                    RenderSystem.restoreProjectionMatrix();

                    for (PostPass.Input postpass$input1 : this.inputs) {
                        postpass$input1.cleanup(p_365909_);
                    }
                }
            );
        }
    }

    @Override
    public void close() {
        for (GpuBuffer gpubuffer : this.customUniforms.values()) {
            gpubuffer.close();
        }

        this.infoUbo.close();
    }

    @OnlyIn(Dist.CLIENT)
    public interface Input {
        void addToPass(FramePass p_362856_, Map<ResourceLocation, ResourceHandle<RenderTarget>> p_367378_);

        default void cleanup(Map<ResourceLocation, ResourceHandle<RenderTarget>> p_366914_) {
        }

        GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> p_366076_);

        String samplerName();
    }

    @OnlyIn(Dist.CLIENT)
    public record TargetInput(String samplerName, ResourceLocation targetId, boolean depthBuffer, boolean bilinear) implements PostPass.Input {
        private ResourceHandle<RenderTarget> getHandle(Map<ResourceLocation, ResourceHandle<RenderTarget>> p_369908_) {
            ResourceHandle<RenderTarget> resourcehandle = p_369908_.get(this.targetId);
            if (resourcehandle == null) {
                throw new IllegalStateException("Missing handle for target " + this.targetId);
            } else {
                return resourcehandle;
            }
        }

        @Override
        public void addToPass(FramePass p_369983_, Map<ResourceLocation, ResourceHandle<RenderTarget>> p_369342_) {
            p_369983_.reads(this.getHandle(p_369342_));
        }

        @Override
        public void cleanup(Map<ResourceLocation, ResourceHandle<RenderTarget>> p_363639_) {
            if (this.bilinear) {
                this.getHandle(p_363639_).get().setFilterMode(FilterMode.NEAREST);
            }
        }

        @Override
        public GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> p_363476_) {
            ResourceHandle<RenderTarget> resourcehandle = this.getHandle(p_363476_);
            RenderTarget rendertarget = resourcehandle.get();
            rendertarget.setFilterMode(this.bilinear ? FilterMode.LINEAR : FilterMode.NEAREST);
            GpuTextureView gputextureview = this.depthBuffer ? rendertarget.getDepthTextureView() : rendertarget.getColorTextureView();
            if (gputextureview == null) {
                throw new IllegalStateException("Missing " + (this.depthBuffer ? "depth" : "color") + "texture for target " + this.targetId);
            } else {
                return gputextureview;
            }
        }

        @Override
        public String samplerName() {
            return this.samplerName;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public record TextureInput(String samplerName, AbstractTexture texture, int width, int height) implements PostPass.Input {
        @Override
        public void addToPass(FramePass p_364568_, Map<ResourceLocation, ResourceHandle<RenderTarget>> p_370060_) {
        }

        @Override
        public GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> p_408443_) {
            return this.texture.getTextureView();
        }

        @Override
        public String samplerName() {
            return this.samplerName;
        }
    }
}
