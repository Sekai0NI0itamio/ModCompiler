package net.minecraft.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@OnlyIn(Dist.CLIENT)
public class ItemStackRenderState {
    ItemDisplayContext displayContext = ItemDisplayContext.NONE;
    private int activeLayerCount;
    private boolean animated;
    private boolean oversizedInGui;
    @Nullable
    private AABB cachedModelBoundingBox;
    private ItemStackRenderState.LayerRenderState[] layers = new ItemStackRenderState.LayerRenderState[]{new ItemStackRenderState.LayerRenderState()};

    public void ensureCapacity(int p_378622_) {
        int i = this.layers.length;
        int j = this.activeLayerCount + p_378622_;
        if (j > i) {
            this.layers = Arrays.copyOf(this.layers, j);

            for (int k = i; k < j; k++) {
                this.layers[k] = new ItemStackRenderState.LayerRenderState();
            }
        }
    }

    public ItemStackRenderState.LayerRenderState newLayer() {
        this.ensureCapacity(1);
        return this.layers[this.activeLayerCount++];
    }

    public void clear() {
        this.displayContext = ItemDisplayContext.NONE;

        for (int i = 0; i < this.activeLayerCount; i++) {
            this.layers[i].clear();
        }

        this.activeLayerCount = 0;
        this.animated = false;
        this.oversizedInGui = false;
        this.cachedModelBoundingBox = null;
    }

    public void setAnimated() {
        this.animated = true;
    }

    public boolean isAnimated() {
        return this.animated;
    }

    public void appendModelIdentityElement(Object p_405951_) {
    }

    private ItemStackRenderState.LayerRenderState firstLayer() {
        return this.layers[0];
    }

    public boolean isEmpty() {
        return this.activeLayerCount == 0;
    }

    public boolean usesBlockLight() {
        return this.firstLayer().usesBlockLight;
    }

    @Nullable
    public TextureAtlasSprite pickParticleIcon(RandomSource p_376964_) {
        return this.activeLayerCount == 0 ? null : this.layers[p_376964_.nextInt(this.activeLayerCount)].particleIcon;
    }

    public void visitExtents(Consumer<Vector3fc> p_395514_) {
        Vector3f vector3f = new Vector3f();
        PoseStack.Pose posestack$pose = new PoseStack.Pose();

        for (int i = 0; i < this.activeLayerCount; i++) {
            ItemStackRenderState.LayerRenderState itemstackrenderstate$layerrenderstate = this.layers[i];
            itemstackrenderstate$layerrenderstate.transform.apply(this.displayContext.leftHand(), posestack$pose);
            Matrix4f matrix4f = posestack$pose.pose();
            Vector3f[] avector3f = itemstackrenderstate$layerrenderstate.extents.get();

            for (Vector3f vector3f1 : avector3f) {
                p_395514_.accept(vector3f.set(vector3f1).mulPosition(matrix4f));
            }

            posestack$pose.setIdentity();
        }
    }

    public void render(PoseStack p_375639_, MultiBufferSource p_377308_, int p_376259_, int p_376823_) {
        for (int i = 0; i < this.activeLayerCount; i++) {
            this.layers[i].render(p_375639_, p_377308_, p_376259_, p_376823_);
        }
    }

    public AABB getModelBoundingBox() {
        if (this.cachedModelBoundingBox != null) {
            return this.cachedModelBoundingBox;
        } else {
            AABB.Builder aabb$builder = new AABB.Builder();
            this.visitExtents(aabb$builder::include);
            AABB aabb = aabb$builder.build();
            this.cachedModelBoundingBox = aabb;
            return aabb;
        }
    }

    public void setOversizedInGui(boolean p_406641_) {
        this.oversizedInGui = p_406641_;
    }

    public boolean isOversizedInGui() {
        return this.oversizedInGui;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum FoilType {
        NONE,
        STANDARD,
        SPECIAL;
    }

    @OnlyIn(Dist.CLIENT)
    public class LayerRenderState {
        private static final Vector3f[] NO_EXTENTS = new Vector3f[0];
        public static final Supplier<Vector3f[]> NO_EXTENTS_SUPPLIER = () -> NO_EXTENTS;
        private final List<BakedQuad> quads = new ArrayList<>();
        boolean usesBlockLight;
        @Nullable
        TextureAtlasSprite particleIcon;
        ItemTransform transform = ItemTransform.NO_TRANSFORM;
        @Nullable
        private RenderType renderType;
        private ItemStackRenderState.FoilType foilType = ItemStackRenderState.FoilType.NONE;
        private int[] tintLayers = new int[0];
        @Nullable
        private SpecialModelRenderer<Object> specialRenderer;
        @Nullable
        private Object argumentForSpecialRendering;
        Supplier<Vector3f[]> extents = NO_EXTENTS_SUPPLIER;

        public void clear() {
            this.quads.clear();
            this.renderType = null;
            this.foilType = ItemStackRenderState.FoilType.NONE;
            this.specialRenderer = null;
            this.argumentForSpecialRendering = null;
            Arrays.fill(this.tintLayers, -1);
            this.usesBlockLight = false;
            this.particleIcon = null;
            this.transform = ItemTransform.NO_TRANSFORM;
            this.extents = NO_EXTENTS_SUPPLIER;
        }

        public List<BakedQuad> prepareQuadList() {
            return this.quads;
        }

        public void setRenderType(RenderType p_394031_) {
            this.renderType = p_394031_;
        }

        public void setUsesBlockLight(boolean p_395823_) {
            this.usesBlockLight = p_395823_;
        }

        public void setExtents(Supplier<Vector3f[]> p_392781_) {
            this.extents = p_392781_;
        }

        public void setParticleIcon(TextureAtlasSprite p_392776_) {
            this.particleIcon = p_392776_;
        }

        public void setTransform(ItemTransform p_395712_) {
            this.transform = p_395712_;
        }

        public <T> void setupSpecialModel(SpecialModelRenderer<T> p_375891_, @Nullable T p_375474_) {
            this.specialRenderer = eraseSpecialRenderer(p_375891_);
            this.argumentForSpecialRendering = p_375474_;
        }

        private static SpecialModelRenderer<Object> eraseSpecialRenderer(SpecialModelRenderer<?> p_377056_) {
            return (SpecialModelRenderer<Object>)p_377056_;
        }

        public void setFoilType(ItemStackRenderState.FoilType p_377629_) {
            this.foilType = p_377629_;
        }

        public int[] prepareTintLayers(int p_375742_) {
            if (p_375742_ > this.tintLayers.length) {
                this.tintLayers = new int[p_375742_];
                Arrays.fill(this.tintLayers, -1);
            }

            return this.tintLayers;
        }

        void render(PoseStack p_377989_, MultiBufferSource p_377594_, int p_375616_, int p_376132_) {
            p_377989_.pushPose();
            this.transform.apply(ItemStackRenderState.this.displayContext.leftHand(), p_377989_.last());
            if (this.specialRenderer != null) {
                this.specialRenderer
                    .render(
                        this.argumentForSpecialRendering,
                        ItemStackRenderState.this.displayContext,
                        p_377989_,
                        p_377594_,
                        p_375616_,
                        p_376132_,
                        this.foilType != ItemStackRenderState.FoilType.NONE
                    );
            } else if (this.renderType != null) {
                ItemRenderer.renderItem(
                    ItemStackRenderState.this.displayContext,
                    p_377989_,
                    p_377594_,
                    p_375616_,
                    p_376132_,
                    this.tintLayers,
                    this.quads,
                    this.renderType,
                    this.foilType
                );
            }

            p_377989_.popPose();
        }
    }
}
