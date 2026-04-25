package net.minecraft.client.gui.render.state;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3x2f;

@OnlyIn(Dist.CLIENT)
public final class GuiTextRenderState implements ScreenArea {
    public final Font font;
    public final FormattedCharSequence text;
    public final Matrix3x2f pose;
    public final int x;
    public final int y;
    public final int color;
    public final int backgroundColor;
    public final boolean dropShadow;
    @Nullable
    public final ScreenRectangle scissor;
    @Nullable
    private Font.PreparedText preparedText;
    @Nullable
    private ScreenRectangle bounds;

    public GuiTextRenderState(
        Font p_409382_,
        FormattedCharSequence p_408459_,
        Matrix3x2f p_408952_,
        int p_410071_,
        int p_408989_,
        int p_407923_,
        int p_409347_,
        boolean p_409006_,
        @Nullable ScreenRectangle p_405949_
    ) {
        this.font = p_409382_;
        this.text = p_408459_;
        this.pose = p_408952_;
        this.x = p_410071_;
        this.y = p_408989_;
        this.color = p_407923_;
        this.backgroundColor = p_409347_;
        this.dropShadow = p_409006_;
        this.scissor = p_405949_;
    }

    public Font.PreparedText ensurePrepared() {
        if (this.preparedText == null) {
            this.preparedText = this.font.prepareText(this.text, this.x, this.y, this.color, this.dropShadow, this.backgroundColor);
            ScreenRectangle screenrectangle = this.preparedText.bounds();
            if (screenrectangle != null) {
                screenrectangle = screenrectangle.transformMaxBounds(this.pose);
                this.bounds = this.scissor != null ? this.scissor.intersection(screenrectangle) : screenrectangle;
            }
        }

        return this.preparedText;
    }

    @Nullable
    @Override
    public ScreenRectangle bounds() {
        this.ensurePrepared();
        return this.bounds;
    }
}
