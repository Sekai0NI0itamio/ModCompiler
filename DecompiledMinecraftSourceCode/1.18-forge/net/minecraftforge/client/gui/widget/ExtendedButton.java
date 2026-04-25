/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.gui.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.gui.GuiUtils;

import net.minecraft.client.gui.components.Button.OnPress;

/**
 * This class provides a button that fixes several bugs present in the vanilla GuiButton drawing code.
 * The gist of it is that it allows buttons of any size without gaps in the graphics and with the
 * borders drawn properly. It also prevents button text from extending out of the sides of the button by
 * trimming the end of the string and adding an ellipsis.<br/><br/>
 *
 * The code that handles drawing the button is in GuiUtils.
 *
 * @author bspkrs
 */
public class ExtendedButton extends Button
{
    public ExtendedButton(int xPos, int yPos, int width, int height, Component displayString, OnPress handler)
    {
        super(xPos, yPos, width, height, displayString, handler);
    }

    /**
     * Draws this button to the screen.
     */
    @Override
    public void m_6303_(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        Minecraft mc = Minecraft.m_91087_();
        int k = this.m_7202_(this.f_93622_);
        GuiUtils.drawContinuousTexturedBox(poseStack, f_93617_, this.f_93620_, this.f_93621_, 0, 46 + k * 20, this.f_93618_, this.f_93619_, 200, 20, 2, 3, 2, 2, this.m_93252_());
        this.m_7906_(poseStack, mc, mouseX, mouseY);

        Component buttonText = this.m_6035_();
        int strWidth = mc.f_91062_.m_92852_(buttonText);
        int ellipsisWidth = mc.f_91062_.m_92895_("...");

        if (strWidth > f_93618_ - 6 && strWidth > ellipsisWidth)
            //TODO, srg names make it hard to figure out how to append to an ITextProperties from this trim operation, wraping this in StringTextComponent is kinda dirty.
            buttonText = new TextComponent(mc.f_91062_.m_92854_(buttonText, f_93618_ - 6 - ellipsisWidth).getString() + "...");

        m_93215_(poseStack, mc.f_91062_, buttonText, this.f_93620_ + this.f_93618_ / 2, this.f_93621_ + (this.f_93619_ - 8) / 2, getFGColor());
    }
}
