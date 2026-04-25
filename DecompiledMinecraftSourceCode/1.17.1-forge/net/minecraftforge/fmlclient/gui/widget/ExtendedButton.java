/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fmlclient.gui.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.fmlclient.gui.GuiUtils;

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
    public void m_6303_(PoseStack mStack, int mouseX, int mouseY, float partial)
    {
        Minecraft mc = Minecraft.m_91087_();
        int k = this.m_7202_(this.m_5702_());
        GuiUtils.drawContinuousTexturedBox(mStack, f_93617_, this.f_93620_, this.f_93621_, 0, 46 + k * 20, this.f_93618_, this.f_93619_, 200, 20, 2, 3, 2, 2, this.m_93252_());
        this.m_7906_(mStack, mc, mouseX, mouseY);

        Component buttonText = this.m_6035_();
        int strWidth = mc.f_91062_.m_92852_(buttonText);
        int ellipsisWidth = mc.f_91062_.m_92895_("...");

        if (strWidth > f_93618_ - 6 && strWidth > ellipsisWidth)
            //TODO, srg names make it hard to figure out how to append to an ITextProperties from this trim operation, wraping this in StringTextComponent is kinda dirty.
            buttonText = new TextComponent(mc.f_91062_.m_92854_(buttonText, f_93618_ - 6 - ellipsisWidth).getString() + "...");

        m_93215_(mStack, mc.f_91062_, buttonText, this.f_93620_ + this.f_93618_ / 2, this.f_93621_ + (this.f_93619_ - 8) / 2, getFGColor());
    }
}
