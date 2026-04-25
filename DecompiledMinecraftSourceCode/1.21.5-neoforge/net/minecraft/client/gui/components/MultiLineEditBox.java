package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MultiLineEditBox extends AbstractTextAreaWidget {
    private static final int CURSOR_INSERT_WIDTH = 1;
    private static final int CURSOR_COLOR = -3092272;
    private static final String CURSOR_APPEND_CHARACTER = "_";
    private static final int TEXT_COLOR = -2039584;
    private static final int PLACEHOLDER_TEXT_COLOR = -857677600;
    private static final int CURSOR_BLINK_INTERVAL_MS = 300;
    private final Font font;
    private final Component placeholder;
    private final MultilineTextField textField;
    private final int textColor;
    private final boolean textShadow;
    private final int cursorColor;
    private long focusedTime = Util.getMillis();

    MultiLineEditBox(
        Font p_239008_,
        int p_239009_,
        int p_239010_,
        int p_239011_,
        int p_239012_,
        Component p_239013_,
        Component p_239014_,
        int p_406765_,
        boolean p_406024_,
        int p_406804_,
        boolean p_407802_,
        boolean p_408829_
    ) {
        super(p_239009_, p_239010_, p_239011_, p_239012_, p_239014_, p_407802_, p_408829_);
        this.font = p_239008_;
        this.textShadow = p_406024_;
        this.textColor = p_406765_;
        this.cursorColor = p_406804_;
        this.placeholder = p_239013_;
        this.textField = new MultilineTextField(p_239008_, p_239011_ - this.totalInnerPadding());
        this.textField.setCursorListener(this::scrollToCursor);
    }

    public void setCharacterLimit(int p_239314_) {
        this.textField.setCharacterLimit(p_239314_);
    }

    public void setLineLimit(int p_408916_) {
        this.textField.setLineLimit(p_408916_);
    }

    public void setValueListener(Consumer<String> p_239274_) {
        this.textField.setValueListener(p_239274_);
    }

    public void setValue(String p_240160_) {
        this.setValue(p_240160_, false);
    }

    public void setValue(String p_409398_, boolean p_410622_) {
        this.textField.setValue(p_409398_, p_410622_);
    }

    public String getValue() {
        return this.textField.value();
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput p_259393_) {
        p_259393_.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", this.getMessage(), this.getValue()));
    }

    @Override
    public void onClick(double p_375608_, double p_378470_) {
        this.textField.setSelecting(Screen.hasShiftDown());
        this.seekCursorScreen(p_375608_, p_378470_);
    }

    @Override
    protected void onDrag(double p_377778_, double p_378213_, double p_376785_, double p_377559_) {
        this.textField.setSelecting(true);
        this.seekCursorScreen(p_377778_, p_378213_);
        this.textField.setSelecting(Screen.hasShiftDown());
    }

    @Override
    public boolean keyPressed(int p_239433_, int p_239434_, int p_239435_) {
        return this.textField.keyPressed(p_239433_);
    }

    @Override
    public boolean charTyped(char p_239387_, int p_239388_) {
        if (this.visible && this.isFocused() && StringUtil.isAllowedChatCharacter(p_239387_)) {
            this.textField.insertText(Character.toString(p_239387_));
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void renderContents(GuiGraphics p_283676_, int p_281538_, int p_283033_, float p_281767_) {
        String s = this.textField.value();
        if (s.isEmpty() && !this.isFocused()) {
            p_283676_.drawWordWrap(this.font, this.placeholder, this.getInnerLeft(), this.getInnerTop(), this.width - this.totalInnerPadding(), -857677600);
        } else {
            int i = this.textField.cursor();
            boolean flag = this.isFocused() && (Util.getMillis() - this.focusedTime) / 300L % 2L == 0L;
            boolean flag1 = i < s.length();
            int j = 0;
            int k = 0;
            int l = this.getInnerTop();

            for (MultilineTextField.StringView multilinetextfield$stringview : this.textField.iterateLines()) {
                boolean flag2 = this.withinContentAreaTopBottom(l, l + 9);
                int i1 = this.getInnerLeft();
                if (flag && flag1 && i >= multilinetextfield$stringview.beginIndex() && i < multilinetextfield$stringview.endIndex()) {
                    if (flag2) {
                        String s2 = s.substring(multilinetextfield$stringview.beginIndex(), i);
                        p_283676_.drawString(this.font, s2, i1, l, this.textColor, this.textShadow);
                        j = i1 + this.font.width(s2);
                        p_283676_.fill(j, l - 1, j + 1, l + 1 + 9, this.cursorColor);
                        p_283676_.drawString(this.font, s.substring(i, multilinetextfield$stringview.endIndex()), j, l, this.textColor, this.textShadow);
                    }
                } else {
                    if (flag2) {
                        String s1 = s.substring(multilinetextfield$stringview.beginIndex(), multilinetextfield$stringview.endIndex());
                        p_283676_.drawString(this.font, s1, i1, l, this.textColor, this.textShadow);
                        j = i1 + this.font.width(s1) - 1;
                    }

                    k = l;
                }

                l += 9;
            }

            if (flag && !flag1 && this.withinContentAreaTopBottom(k, k + 9)) {
                p_283676_.drawString(this.font, "_", j, k, this.cursorColor, this.textShadow);
            }

            if (this.textField.hasSelection()) {
                MultilineTextField.StringView multilinetextfield$stringview1 = this.textField.getSelected();
                int k1 = this.getInnerLeft();
                l = this.getInnerTop();

                for (MultilineTextField.StringView multilinetextfield$stringview2 : this.textField.iterateLines()) {
                    if (multilinetextfield$stringview1.beginIndex() > multilinetextfield$stringview2.endIndex()) {
                        l += 9;
                    } else {
                        if (multilinetextfield$stringview2.beginIndex() > multilinetextfield$stringview1.endIndex()) {
                            break;
                        }

                        if (this.withinContentAreaTopBottom(l, l + 9)) {
                            int l1 = this.font
                                .width(
                                    s.substring(
                                        multilinetextfield$stringview2.beginIndex(),
                                        Math.max(multilinetextfield$stringview1.beginIndex(), multilinetextfield$stringview2.beginIndex())
                                    )
                                );
                            int j1;
                            if (multilinetextfield$stringview1.endIndex() > multilinetextfield$stringview2.endIndex()) {
                                j1 = this.width - this.innerPadding();
                            } else {
                                j1 = this.font
                                    .width(s.substring(multilinetextfield$stringview2.beginIndex(), multilinetextfield$stringview1.endIndex()));
                            }

                            p_283676_.textHighlight(k1 + l1, l, k1 + j1, l + 9);
                        }

                        l += 9;
                    }
                }
            }
        }
    }

    @Override
    protected void renderDecorations(GuiGraphics p_282551_) {
        super.renderDecorations(p_282551_);
        if (this.textField.hasCharacterLimit()) {
            int i = this.textField.characterLimit();
            Component component = Component.translatable("gui.multiLineEditBox.character_limit", this.textField.value().length(), i);
            p_282551_.drawString(
                this.font,
                component,
                this.getX() + this.width - this.font.width(component),
                this.getY() + this.height + 4,
                -6250336
            );
        }
    }

    @Override
    public int getInnerHeight() {
        return 9 * this.textField.getLineCount();
    }

    @Override
    protected double scrollRate() {
        return 9.0 / 2.0;
    }

    private void scrollToCursor() {
        double d0 = this.scrollAmount();
        MultilineTextField.StringView multilinetextfield$stringview = this.textField.getLineView((int)(d0 / 9.0));
        if (this.textField.cursor() <= multilinetextfield$stringview.beginIndex()) {
            d0 = this.textField.getLineAtCursor() * 9;
        } else {
            MultilineTextField.StringView multilinetextfield$stringview1 = this.textField.getLineView((int)((d0 + this.height) / 9.0) - 1);
            if (this.textField.cursor() > multilinetextfield$stringview1.endIndex()) {
                d0 = this.textField.getLineAtCursor() * 9 - this.height + 9 + this.totalInnerPadding();
            }
        }

        this.setScrollAmount(d0);
    }

    private void seekCursorScreen(double p_239276_, double p_239277_) {
        double d0 = p_239276_ - this.getX() - this.innerPadding();
        double d1 = p_239277_ - this.getY() - this.innerPadding() + this.scrollAmount();
        this.textField.seekCursorToPoint(d0, d1);
    }

    @Override
    public void setFocused(boolean p_299784_) {
        super.setFocused(p_299784_);
        if (p_299784_) {
            this.focusedTime = Util.getMillis();
        }
    }

    public static MultiLineEditBox.Builder builder() {
        return new MultiLineEditBox.Builder();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Builder {
        private int x;
        private int y;
        private Component placeholder = CommonComponents.EMPTY;
        private int textColor = -2039584;
        private boolean textShadow = true;
        private int cursorColor = -3092272;
        private boolean showBackground = true;
        private boolean showDecorations = true;

        public MultiLineEditBox.Builder setX(int p_409056_) {
            this.x = p_409056_;
            return this;
        }

        public MultiLineEditBox.Builder setY(int p_406915_) {
            this.y = p_406915_;
            return this;
        }

        public MultiLineEditBox.Builder setPlaceholder(Component p_410512_) {
            this.placeholder = p_410512_;
            return this;
        }

        public MultiLineEditBox.Builder setTextColor(int p_406591_) {
            this.textColor = p_406591_;
            return this;
        }

        public MultiLineEditBox.Builder setTextShadow(boolean p_409308_) {
            this.textShadow = p_409308_;
            return this;
        }

        public MultiLineEditBox.Builder setCursorColor(int p_408684_) {
            this.cursorColor = p_408684_;
            return this;
        }

        public MultiLineEditBox.Builder setShowBackground(boolean p_409629_) {
            this.showBackground = p_409629_;
            return this;
        }

        public MultiLineEditBox.Builder setShowDecorations(boolean p_406917_) {
            this.showDecorations = p_406917_;
            return this;
        }

        public MultiLineEditBox build(Font p_405832_, int p_408493_, int p_408376_, Component p_407600_) {
            return new MultiLineEditBox(
                p_405832_,
                this.x,
                this.y,
                p_408493_,
                p_408376_,
                this.placeholder,
                p_407600_,
                this.textColor,
                this.textShadow,
                this.cursorColor,
                this.showBackground,
                this.showDecorations
            );
        }
    }
}
