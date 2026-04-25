package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface MultiLineLabel {
    MultiLineLabel EMPTY = new MultiLineLabel() {
        @Override
        public void renderCentered(GuiGraphics p_283384_, int p_94395_, int p_94396_) {
        }

        @Override
        public void renderCentered(GuiGraphics p_283208_, int p_210825_, int p_210826_, int p_210827_, int p_210828_) {
        }

        @Override
        public void renderLeftAligned(GuiGraphics p_283077_, int p_94379_, int p_94380_, int p_282157_, int p_282742_) {
        }

        @Override
        public int renderLeftAlignedNoShadow(GuiGraphics p_283645_, int p_94389_, int p_94390_, int p_94391_, int p_94392_) {
            return p_94390_;
        }

        @Nullable
        @Override
        public Style getStyleAtCentered(int p_407151_, int p_410110_, int p_407485_, double p_409589_, double p_410501_) {
            return null;
        }

        @Nullable
        @Override
        public Style getStyleAtLeftAligned(int p_407941_, int p_408109_, int p_409128_, double p_409339_, double p_407180_) {
            return null;
        }

        @Override
        public int getLineCount() {
            return 0;
        }

        @Override
        public int getWidth() {
            return 0;
        }
    };

    static MultiLineLabel create(Font p_94351_, Component... p_94352_) {
        return create(p_94351_, Integer.MAX_VALUE, Integer.MAX_VALUE, p_94352_);
    }

    static MultiLineLabel create(Font p_94342_, int p_94344_, Component... p_345312_) {
        return create(p_94342_, p_94344_, Integer.MAX_VALUE, p_345312_);
    }

    static MultiLineLabel create(Font p_94346_, Component p_344884_, int p_94348_) {
        return create(p_94346_, p_94348_, Integer.MAX_VALUE, p_344884_);
    }

    static MultiLineLabel create(final Font p_169037_, final int p_342954_, final int p_342610_, final Component... p_345091_) {
        return p_345091_.length == 0
            ? EMPTY
            : new MultiLineLabel() {
                @Nullable
                private List<MultiLineLabel.TextAndWidth> cachedTextAndWidth;
                @Nullable
                private Language splitWithLanguage;

                @Override
                public void renderCentered(GuiGraphics p_283492_, int p_283184_, int p_282078_) {
                    this.renderCentered(p_283492_, p_283184_, p_282078_, 9, -1);
                }

                @Override
                public void renderCentered(GuiGraphics p_281603_, int p_281267_, int p_281819_, int p_281545_, int p_282780_) {
                    int i = p_281819_;

                    for (MultiLineLabel.TextAndWidth multilinelabel$textandwidth : this.getSplitMessage()) {
                        p_281603_.drawString(
                            p_169037_, multilinelabel$textandwidth.text, p_281267_ - multilinelabel$textandwidth.width / 2, i, p_282780_
                        );
                        i += p_281545_;
                    }
                }

                @Override
                public void renderLeftAligned(GuiGraphics p_282318_, int p_283665_, int p_283416_, int p_281919_, int p_281686_) {
                    int i = p_283416_;

                    for (MultiLineLabel.TextAndWidth multilinelabel$textandwidth : this.getSplitMessage()) {
                        p_282318_.drawString(p_169037_, multilinelabel$textandwidth.text, p_283665_, i, p_281686_);
                        i += p_281919_;
                    }
                }

                @Override
                public int renderLeftAlignedNoShadow(GuiGraphics p_281782_, int p_282841_, int p_283554_, int p_282768_, int p_283499_) {
                    int i = p_283554_;

                    for (MultiLineLabel.TextAndWidth multilinelabel$textandwidth : this.getSplitMessage()) {
                        p_281782_.drawString(p_169037_, multilinelabel$textandwidth.text, p_282841_, i, p_283499_, false);
                        i += p_282768_;
                    }

                    return i;
                }

                @Nullable
                @Override
                public Style getStyleAtCentered(int p_407426_, int p_409697_, int p_410519_, double p_406056_, double p_406328_) {
                    List<MultiLineLabel.TextAndWidth> list = this.getSplitMessage();
                    int i = Mth.floor((p_406328_ - p_409697_) / p_410519_);
                    if (i >= 0 && i < list.size()) {
                        MultiLineLabel.TextAndWidth multilinelabel$textandwidth = list.get(i);
                        int j = p_407426_ - multilinelabel$textandwidth.width / 2;
                        if (p_406056_ < j) {
                            return null;
                        } else {
                            int k = Mth.floor(p_406056_ - j);
                            return p_169037_.getSplitter().componentStyleAtWidth(multilinelabel$textandwidth.text, k);
                        }
                    } else {
                        return null;
                    }
                }

                @Nullable
                @Override
                public Style getStyleAtLeftAligned(int p_407554_, int p_407233_, int p_405911_, double p_410696_, double p_407790_) {
                    if (p_410696_ < p_407554_) {
                        return null;
                    } else {
                        List<MultiLineLabel.TextAndWidth> list = this.getSplitMessage();
                        int i = Mth.floor((p_407790_ - p_407233_) / p_405911_);
                        if (i >= 0 && i < list.size()) {
                            MultiLineLabel.TextAndWidth multilinelabel$textandwidth = list.get(i);
                            int j = Mth.floor(p_410696_ - p_407554_);
                            return p_169037_.getSplitter().componentStyleAtWidth(multilinelabel$textandwidth.text, j);
                        } else {
                            return null;
                        }
                    }
                }

                private List<MultiLineLabel.TextAndWidth> getSplitMessage() {
                    Language language = Language.getInstance();
                    if (this.cachedTextAndWidth != null && language == this.splitWithLanguage) {
                        return this.cachedTextAndWidth;
                    } else {
                        this.splitWithLanguage = language;
                        List<FormattedText> list = new ArrayList<>();

                        for (Component component : p_345091_) {
                            list.addAll(p_169037_.splitIgnoringLanguage(component, p_342954_));
                        }

                        this.cachedTextAndWidth = new ArrayList<>();
                        int i = Math.min(list.size(), p_342610_);
                        List<FormattedText> list1 = list.subList(0, i);

                        for (int j = 0; j < list1.size(); j++) {
                            FormattedText formattedtext2 = list1.get(j);
                            FormattedCharSequence formattedcharsequence = Language.getInstance().getVisualOrder(formattedtext2);
                            if (j == list1.size() - 1 && i == p_342610_ && i != list.size()) {
                                FormattedText formattedtext = p_169037_.substrByWidth(
                                    formattedtext2, p_169037_.width(formattedtext2) - p_169037_.width(CommonComponents.ELLIPSIS)
                                );
                                FormattedText formattedtext1 = FormattedText.composite(formattedtext, CommonComponents.ELLIPSIS);
                                this.cachedTextAndWidth
                                    .add(new MultiLineLabel.TextAndWidth(Language.getInstance().getVisualOrder(formattedtext1), p_169037_.width(formattedtext1)));
                            } else {
                                this.cachedTextAndWidth.add(new MultiLineLabel.TextAndWidth(formattedcharsequence, p_169037_.width(formattedcharsequence)));
                            }
                        }

                        return this.cachedTextAndWidth;
                    }
                }

                @Override
                public int getLineCount() {
                    return this.getSplitMessage().size();
                }

                @Override
                public int getWidth() {
                    return Math.min(p_342954_, this.getSplitMessage().stream().mapToInt(MultiLineLabel.TextAndWidth::width).max().orElse(0));
                }
            };
    }

    void renderCentered(GuiGraphics p_281749_, int p_94334_, int p_94335_);

    void renderCentered(GuiGraphics p_281785_, int p_94337_, int p_94338_, int p_94339_, int p_94340_);

    void renderLeftAligned(GuiGraphics p_282655_, int p_94365_, int p_94366_, int p_94367_, int p_94368_);

    int renderLeftAlignedNoShadow(GuiGraphics p_281982_, int p_94354_, int p_94355_, int p_94356_, int p_94357_);

    @Nullable
    Style getStyleAtCentered(int p_407201_, int p_410038_, int p_407694_, double p_409508_, double p_406597_);

    @Nullable
    Style getStyleAtLeftAligned(int p_405819_, int p_407389_, int p_408426_, double p_407593_, double p_409211_);

    int getLineCount();

    int getWidth();

    @OnlyIn(Dist.CLIENT)
    public record TextAndWidth(FormattedCharSequence text, int width) {
    }
}
