package net.minecraft.client.gui.render.state.pip;

import javax.annotation.Nullable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record GuiSkinRenderState(
    PlayerModel playerModel,
    ResourceLocation texture,
    float rotationX,
    float rotationY,
    float pivotY,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public GuiSkinRenderState(
        PlayerModel p_410449_,
        ResourceLocation p_407551_,
        float p_407583_,
        float p_407404_,
        float p_407776_,
        int p_410339_,
        int p_407942_,
        int p_406963_,
        int p_410743_,
        float p_408299_,
        @Nullable ScreenRectangle p_406864_
    ) {
        this(
            p_410449_,
            p_407551_,
            p_407583_,
            p_407404_,
            p_407776_,
            p_410339_,
            p_407942_,
            p_406963_,
            p_410743_,
            p_408299_,
            p_406864_,
            PictureInPictureRenderState.getBounds(p_410339_, p_407942_, p_406963_, p_410743_, p_406864_)
        );
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

    @Override
    public float scale() {
        return this.scale;
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
