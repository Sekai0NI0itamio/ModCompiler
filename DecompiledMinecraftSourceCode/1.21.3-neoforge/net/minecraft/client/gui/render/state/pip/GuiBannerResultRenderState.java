package net.minecraft.client.gui.render.state.pip;

import javax.annotation.Nullable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record GuiBannerResultRenderState(
    ModelPart flag,
    DyeColor baseColor,
    BannerPatternLayers resultBannerPatterns,
    int x0,
    int y0,
    int x1,
    int y1,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public GuiBannerResultRenderState(
        ModelPart p_410253_,
        DyeColor p_409113_,
        BannerPatternLayers p_408036_,
        int p_406681_,
        int p_406815_,
        int p_407642_,
        int p_406288_,
        @Nullable ScreenRectangle p_407038_
    ) {
        this(
            p_410253_,
            p_409113_,
            p_408036_,
            p_406681_,
            p_406815_,
            p_407642_,
            p_406288_,
            p_407038_,
            PictureInPictureRenderState.getBounds(p_406681_, p_406815_, p_407642_, p_406288_, p_407038_)
        );
    }

    @Override
    public float scale() {
        return 16.0F;
    }

    @Override
    public int x0() {
        return this.x0;
    }

    @Override
    public int y0() {
        return this.y0;
    }

    @Override
    public int x1() {
        return this.x1;
    }

    @Override
    public int y1() {
        return this.y1;
    }

    @Nullable
    @Override
    public ScreenRectangle scissorArea() {
        return this.scissorArea;
    }

    @Nullable
    @Override
    public ScreenRectangle bounds() {
        return this.bounds;
    }
}
