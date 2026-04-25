package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TestInstanceRenderer implements BlockEntityRenderer<TestInstanceBlockEntity> {
    private final BeaconRenderer<TestInstanceBlockEntity> beacon;
    private final BlockEntityWithBoundingBoxRenderer<TestInstanceBlockEntity> box;

    public TestInstanceRenderer(BlockEntityRendererProvider.Context p_392307_) {
        this.beacon = new BeaconRenderer<>(p_392307_);
        this.box = new BlockEntityWithBoundingBoxRenderer<>(p_392307_);
    }

    public void render(
        TestInstanceBlockEntity p_391740_, float p_391241_, PoseStack p_396531_, MultiBufferSource p_394305_, int p_396995_, int p_392653_, Vec3 p_396957_
    ) {
        this.beacon.render(p_391740_, p_391241_, p_396531_, p_394305_, p_396995_, p_392653_, p_396957_);
        this.box.render(p_391740_, p_391241_, p_396531_, p_394305_, p_396995_, p_392653_, p_396957_);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return this.beacon.shouldRenderOffScreen() || this.box.shouldRenderOffScreen();
    }

    @Override
    public int getViewDistance() {
        return Math.max(this.beacon.getViewDistance(), this.box.getViewDistance());
    }

    public boolean shouldRender(TestInstanceBlockEntity p_393815_, Vec3 p_394435_) {
        return this.beacon.shouldRender(p_393815_, p_394435_) || this.box.shouldRender(p_393815_, p_394435_);
    }
}
