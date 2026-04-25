package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockEntityWithBoundingBoxRenderer<T extends BlockEntity & BoundingBoxRenderable> implements BlockEntityRenderer<T> {
    public BlockEntityWithBoundingBoxRenderer(BlockEntityRendererProvider.Context p_392389_) {
    }

    @Override
    public void render(T p_394143_, float p_396821_, PoseStack p_394113_, MultiBufferSource p_392296_, int p_396878_, int p_393623_, Vec3 p_391199_) {
        if (Minecraft.getInstance().player.canUseGameMasterBlocks() || Minecraft.getInstance().player.isSpectator()) {
            BoundingBoxRenderable.Mode boundingboxrenderable$mode = p_394143_.renderMode();
            if (boundingboxrenderable$mode != BoundingBoxRenderable.Mode.NONE) {
                BoundingBoxRenderable.RenderableBox boundingboxrenderable$renderablebox = p_394143_.getRenderableBox();
                BlockPos blockpos = boundingboxrenderable$renderablebox.localPos();
                Vec3i vec3i = boundingboxrenderable$renderablebox.size();
                if (vec3i.getX() >= 1 && vec3i.getY() >= 1 && vec3i.getZ() >= 1) {
                    float f = 1.0F;
                    float f1 = 0.9F;
                    float f2 = 0.5F;
                    VertexConsumer vertexconsumer = p_392296_.getBuffer(RenderType.lines());
                    BlockPos blockpos1 = blockpos.offset(vec3i);
                    ShapeRenderer.renderLineBox(
                        p_394113_,
                        vertexconsumer,
                        blockpos.getX(),
                        blockpos.getY(),
                        blockpos.getZ(),
                        blockpos1.getX(),
                        blockpos1.getY(),
                        blockpos1.getZ(),
                        0.9F,
                        0.9F,
                        0.9F,
                        1.0F,
                        0.5F,
                        0.5F,
                        0.5F
                    );
                    if (boundingboxrenderable$mode == BoundingBoxRenderable.Mode.BOX_AND_INVISIBLE_BLOCKS && p_394143_.getLevel() != null) {
                        this.renderInvisibleBlocks(p_394143_, p_394143_.getLevel(), blockpos, vec3i, p_392296_, p_394113_);
                    }
                }
            }
        }
    }

    private void renderInvisibleBlocks(T p_395388_, BlockGetter p_391826_, BlockPos p_391396_, Vec3i p_397882_, MultiBufferSource p_395384_, PoseStack p_392976_) {
        VertexConsumer vertexconsumer = p_395384_.getBuffer(RenderType.lines());
        BlockPos blockpos = p_395388_.getBlockPos();
        BlockPos blockpos1 = blockpos.offset(p_391396_);

        for (BlockPos blockpos2 : BlockPos.betweenClosed(blockpos1, blockpos1.offset(p_397882_).offset(-1, -1, -1))) {
            BlockState blockstate = p_391826_.getBlockState(blockpos2);
            boolean flag = blockstate.isAir();
            boolean flag1 = blockstate.is(Blocks.STRUCTURE_VOID);
            boolean flag2 = blockstate.is(Blocks.BARRIER);
            boolean flag3 = blockstate.is(Blocks.LIGHT);
            boolean flag4 = flag1 || flag2 || flag3;
            if (flag || flag4) {
                float f = flag ? 0.05F : 0.0F;
                double d0 = blockpos2.getX() - blockpos.getX() + 0.45F - f;
                double d1 = blockpos2.getY() - blockpos.getY() + 0.45F - f;
                double d2 = blockpos2.getZ() - blockpos.getZ() + 0.45F - f;
                double d3 = blockpos2.getX() - blockpos.getX() + 0.55F + f;
                double d4 = blockpos2.getY() - blockpos.getY() + 0.55F + f;
                double d5 = blockpos2.getZ() - blockpos.getZ() + 0.55F + f;
                if (flag) {
                    ShapeRenderer.renderLineBox(p_392976_, vertexconsumer, d0, d1, d2, d3, d4, d5, 0.5F, 0.5F, 1.0F, 1.0F, 0.5F, 0.5F, 1.0F);
                } else if (flag1) {
                    ShapeRenderer.renderLineBox(p_392976_, vertexconsumer, d0, d1, d2, d3, d4, d5, 1.0F, 0.75F, 0.75F, 1.0F, 1.0F, 0.75F, 0.75F);
                } else if (flag2) {
                    ShapeRenderer.renderLineBox(p_392976_, vertexconsumer, d0, d1, d2, d3, d4, d5, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
                } else if (flag3) {
                    ShapeRenderer.renderLineBox(p_392976_, vertexconsumer, d0, d1, d2, d3, d4, d5, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F);
                }
            }
        }
    }

    private void renderStructureVoids(T p_395236_, BlockPos p_397752_, Vec3i p_395592_, VertexConsumer p_395461_, PoseStack p_395074_) {
        BlockGetter blockgetter = p_395236_.getLevel();
        if (blockgetter != null) {
            BlockPos blockpos = p_395236_.getBlockPos();
            DiscreteVoxelShape discretevoxelshape = new BitSetDiscreteVoxelShape(p_395592_.getX(), p_395592_.getY(), p_395592_.getZ());

            for (BlockPos blockpos1 : BlockPos.betweenClosed(p_397752_, p_397752_.offset(p_395592_).offset(-1, -1, -1))) {
                if (blockgetter.getBlockState(blockpos1).is(Blocks.STRUCTURE_VOID)) {
                    discretevoxelshape.fill(
                        blockpos1.getX() - p_397752_.getX(),
                        blockpos1.getY() - p_397752_.getY(),
                        blockpos1.getZ() - p_397752_.getZ()
                    );
                }
            }

            discretevoxelshape.forAllFaces((p_392316_, p_394067_, p_391919_, p_397211_) -> {
                float f = 0.48F;
                float f1 = p_394067_ + p_397752_.getX() - blockpos.getX() + 0.5F - 0.48F;
                float f2 = p_391919_ + p_397752_.getY() - blockpos.getY() + 0.5F - 0.48F;
                float f3 = p_397211_ + p_397752_.getZ() - blockpos.getZ() + 0.5F - 0.48F;
                float f4 = p_394067_ + p_397752_.getX() - blockpos.getX() + 0.5F + 0.48F;
                float f5 = p_391919_ + p_397752_.getY() - blockpos.getY() + 0.5F + 0.48F;
                float f6 = p_397211_ + p_397752_.getZ() - blockpos.getZ() + 0.5F + 0.48F;
                ShapeRenderer.renderFace(p_395074_, p_395461_, p_392316_, f1, f2, f3, f4, f5, f6, 0.75F, 0.75F, 1.0F, 0.2F);
            });
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
