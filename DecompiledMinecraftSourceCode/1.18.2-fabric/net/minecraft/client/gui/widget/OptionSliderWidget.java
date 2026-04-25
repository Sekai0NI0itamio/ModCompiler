/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.LiteralText;

@Environment(value=EnvType.CLIENT)
public abstract class OptionSliderWidget
extends SliderWidget {
    protected final GameOptions options;

    protected OptionSliderWidget(GameOptions options, int x, int y, int width, int height, double value) {
        super(x, y, width, height, LiteralText.EMPTY, value);
        this.options = options;
    }
}

