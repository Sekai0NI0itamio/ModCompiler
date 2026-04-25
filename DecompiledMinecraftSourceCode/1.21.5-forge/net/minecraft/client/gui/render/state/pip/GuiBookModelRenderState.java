package net.minecraft.client.gui.render.state.pip;

import javax.annotation.Nullable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.BookModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record GuiBookModelRenderState(
    BookModel bookModel,
    ResourceLocation texture,
    float open,
    float flip,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public GuiBookModelRenderState(
        BookModel p_407999_,
        ResourceLocation p_408549_,
        float p_409891_,
        float p_409320_,
        int p_406907_,
        int p_410705_,
        int p_409155_,
        int p_407487_,
        float p_409371_,
        @Nullable ScreenRectangle p_405906_
    ) {
        this(
            p_407999_,
            p_408549_,
            p_409891_,
            p_409320_,
            p_406907_,
            p_410705_,
            p_409155_,
            p_407487_,
            p_409371_,
            p_405906_,
            PictureInPictureRenderState.getBounds(p_406907_, p_410705_, p_409155_, p_407487_, p_405906_)
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
