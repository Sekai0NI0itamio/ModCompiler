package net.minecraft.client.gui.components;

import java.util.OptionalInt;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.SingleKeyCache;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MultiLineTextWidget extends AbstractStringWidget {
    private OptionalInt maxWidth = OptionalInt.empty();
    private OptionalInt maxRows = OptionalInt.empty();
    private final SingleKeyCache<MultiLineTextWidget.CacheKey, MultiLineLabel> cache;
    private boolean centered = false;
    private boolean allowHoverComponents = false;
    @Nullable
    private Consumer<Style> componentClickHandler = null;

    public MultiLineTextWidget(Component p_270532_, Font p_270639_) {
        this(0, 0, p_270532_, p_270639_);
    }

    public MultiLineTextWidget(int p_270325_, int p_270355_, Component p_270069_, Font p_270673_) {
        super(p_270325_, p_270355_, 0, 0, p_270069_, p_270673_);
        this.cache = Util.singleKeyCache(
            p_340776_ -> p_340776_.maxRows.isPresent()
                ? MultiLineLabel.create(p_270673_, p_340776_.maxWidth, p_340776_.maxRows.getAsInt(), p_340776_.message)
                : MultiLineLabel.create(p_270673_, p_340776_.message, p_340776_.maxWidth)
        );
        this.active = false;
    }

    public MultiLineTextWidget setColor(int p_270378_) {
        super.setColor(p_270378_);
        return this;
    }

    public MultiLineTextWidget setMaxWidth(int p_270776_) {
        this.maxWidth = OptionalInt.of(p_270776_);
        return this;
    }

    public MultiLineTextWidget setMaxRows(int p_270085_) {
        this.maxRows = OptionalInt.of(p_270085_);
        return this;
    }

    public MultiLineTextWidget setCentered(boolean p_270493_) {
        this.centered = p_270493_;
        return this;
    }

    public MultiLineTextWidget configureStyleHandling(boolean p_406320_, @Nullable Consumer<Style> p_409704_) {
        this.allowHoverComponents = p_406320_;
        this.componentClickHandler = p_409704_;
        return this;
    }

    @Override
    public int getWidth() {
        return this.cache.getValue(this.getFreshCacheKey()).getWidth();
    }

    @Override
    public int getHeight() {
        return this.cache.getValue(this.getFreshCacheKey()).getLineCount() * 9;
    }

    @Override
    public void renderWidget(GuiGraphics p_282535_, int p_261774_, int p_261640_, float p_261514_) {
        MultiLineLabel multilinelabel = this.cache.getValue(this.getFreshCacheKey());
        int i = this.getX();
        int j = this.getY();
        int k = 9;
        int l = this.getColor();
        if (this.centered) {
            multilinelabel.renderCentered(p_282535_, i + this.getWidth() / 2, j, k, l);
        } else {
            multilinelabel.renderLeftAligned(p_282535_, i, j, k, l);
        }

        if (this.allowHoverComponents) {
            Style style = this.getComponentStyleAt(p_261774_, p_261640_);
            if (this.isHovered()) {
                p_282535_.renderComponentHoverEffect(this.getFont(), style, p_261774_, p_261640_);
            }
        }
    }

    @Nullable
    private Style getComponentStyleAt(double p_406792_, double p_410354_) {
        MultiLineLabel multilinelabel = this.cache.getValue(this.getFreshCacheKey());
        int i = this.getX();
        int j = this.getY();
        int k = 9;
        return this.centered
            ? multilinelabel.getStyleAtCentered(i + this.getWidth() / 2, j, k, p_406792_, p_410354_)
            : multilinelabel.getStyleAtLeftAligned(i, j, k, p_406792_, p_410354_);
    }

    @Override
    public void onClick(double p_408715_, double p_407516_) {
        if (this.componentClickHandler != null) {
            Style style = this.getComponentStyleAt(p_408715_, p_407516_);
            if (style != null) {
                this.componentClickHandler.accept(style);
                return;
            }
        }

        super.onClick(p_408715_, p_407516_);
    }

    private MultiLineTextWidget.CacheKey getFreshCacheKey() {
        return new MultiLineTextWidget.CacheKey(this.getMessage(), this.maxWidth.orElse(Integer.MAX_VALUE), this.maxRows);
    }

    @OnlyIn(Dist.CLIENT)
    record CacheKey(Component message, int maxWidth, OptionalInt maxRows) {
    }
}
