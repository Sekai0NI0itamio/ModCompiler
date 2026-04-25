/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.gui.widget;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.gui.GuiUtils;

import net.minecraft.client.gui.components.Button.OnPress;

/**
 * This class provides a button that shows a string glyph at the beginning. The glyph can be scaled using the glyphScale parameter.
 *
 * @author bspkrs
 */
public class UnicodeGlyphButton extends ExtendedButton
{
    public String glyph;
    public float  glyphScale;

    public UnicodeGlyphButton(int xPos, int yPos, int width, int height, Component displayString, String glyph, float glyphScale, OnPress handler)
    {
        super(xPos, yPos, width, height, displayString, handler);
        this.glyph = glyph;
        this.glyphScale = glyphScale;
    }

    @Override
    public void m_6305_(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        if (this.f_93624_)
        {
            Minecraft mc = Minecraft.m_91087_();
            this.f_93622_ = mouseX >= this.f_93620_ && mouseY >= this.f_93621_ && mouseX < this.f_93620_ + this.f_93618_ && mouseY < this.f_93621_ + this.f_93619_;
            int k = this.m_7202_(this.f_93622_);
            GuiUtils.drawContinuousTexturedBox(poseStack, f_93617_, this.f_93620_, this.f_93621_, 0, 46 + k * 20, this.f_93618_, this.f_93619_, 200, 20, 2, 3, 2, 2, this.m_93252_());
            this.m_7906_(poseStack, mc, mouseX, mouseY);

            Component buttonText = this.m_5646_();
            int glyphWidth = (int) (mc.f_91062_.m_92895_(glyph) * glyphScale);
            int strWidth = mc.f_91062_.m_92852_(buttonText);
            int ellipsisWidth = mc.f_91062_.m_92895_("...");
            int totalWidth = strWidth + glyphWidth;

            if (totalWidth > f_93618_ - 6 && totalWidth > ellipsisWidth)
                buttonText = new TextComponent(mc.f_91062_.m_92854_(buttonText, f_93618_ - 6 - ellipsisWidth).getString().trim() + "...") ;

            strWidth = mc.f_91062_.m_92852_(buttonText);
            totalWidth = glyphWidth + strWidth;

            poseStack.m_85836_();
            poseStack.m_85841_(glyphScale, glyphScale, 1.0F);
            this.m_93215_(poseStack, mc.f_91062_, new TextComponent(glyph),
                    (int) (((this.f_93620_ + (this.f_93618_ / 2) - (strWidth / 2)) / glyphScale) - (glyphWidth / (2 * glyphScale)) + 2),
                    (int) (((this.f_93621_ + ((this.f_93619_ - 8) / glyphScale) / 2) - 1) / glyphScale), getFGColor());
            poseStack.m_85849_();

            this.m_93215_(poseStack, mc.f_91062_, buttonText, (int) (this.f_93620_ + (this.f_93618_ / 2) + (glyphWidth / glyphScale)),
                    this.f_93621_ + (this.f_93619_ - 8) / 2, getFGColor());

        }
    }
}
