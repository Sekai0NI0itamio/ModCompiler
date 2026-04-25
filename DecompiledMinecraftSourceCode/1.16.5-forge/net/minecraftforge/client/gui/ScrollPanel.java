/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.gui;

import java.util.Collections;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FocusableGui;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.IRenderable;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.client.gui.GuiUtils;

public abstract class ScrollPanel extends FocusableGui implements IRenderable
{
    private final Minecraft client;
    protected final int width;
    protected final int height;
    protected final int top;
    protected final int bottom;
    protected final int right;
    protected final int left;
    private boolean scrolling;
    protected float scrollDistance;
    protected boolean captureMouse = true;
    protected final int border = 4;

    private final int barWidth = 6;
    private final int barLeft;

    public ScrollPanel(Minecraft client, int width, int height, int top, int left)
    {
        this.client = client;
        this.width = width;
        this.height = height;
        this.top = top;
        this.left = left;
        this.bottom = height + this.top;
        this.right = width + this.left;
        this.barLeft = this.left + this.width - barWidth;
    }

    protected abstract int getContentHeight();

    protected void drawBackground() {}

    /**
     * Draw anything special on the screen. GL_SCISSOR is enabled for anything that
     * is rendered outside of the view box. Do not mess with SCISSOR unless you support this.
     * @param mouseY
     * @param mouseX
     */
    protected abstract void drawPanel(MatrixStack mStack, int entryRight, int relativeY, Tessellator tess, int mouseX, int mouseY);

    protected boolean clickPanel(double mouseX, double mouseY, int button) { return false; }

    private int getMaxScroll()
    {
        return this.getContentHeight() - (this.height - this.border);
    }

    private void applyScrollLimits()
    {
        int max = getMaxScroll();

        if (max < 0)
        {
            max /= 2;
        }

        if (this.scrollDistance < 0.0F)
        {
            this.scrollDistance = 0.0F;
        }

        if (this.scrollDistance > max)
        {
            this.scrollDistance = max;
        }
    }

    @Override
    public boolean func_231043_a_(double mouseX, double mouseY, double scroll)
    {
        if (scroll != 0)
        {
            this.scrollDistance += -scroll * getScrollAmount();
            applyScrollLimits();
            return true;
        }
        return false;
    }

    protected int getScrollAmount()
    {
        return 20;
    }

    @Override
    public boolean func_231047_b_(double mouseX, double mouseY)
    {
        return mouseX >= this.left && mouseX <= this.left + this.width &&
                mouseY >= this.top && mouseY <= this.bottom;
    }

    @Override
    public boolean func_231044_a_(double mouseX, double mouseY, int button) {
        if (super.func_231044_a_(mouseX, mouseY, button))
            return true;

        this.scrolling = button == 0 && mouseX >= barLeft && mouseX < barLeft + barWidth;
        if (this.scrolling)
        {
            return true;
        }
        int mouseListY = ((int)mouseY) - this.top - this.getContentHeight() + (int)this.scrollDistance - border;
        if (mouseX >= left && mouseX <= right && mouseListY < 0)
        {
            return this.clickPanel(mouseX - left, mouseY - this.top + (int)this.scrollDistance - border, button);
        }
        return false;
    }

    @Override
    public boolean func_231048_c_(double p_mouseReleased_1_, double p_mouseReleased_3_, int p_mouseReleased_5_) {
        if (super.func_231048_c_(p_mouseReleased_1_, p_mouseReleased_3_, p_mouseReleased_5_))
            return true;
        boolean ret = this.scrolling;
        this.scrolling = false;
        return ret;
    }

    private int getBarHeight()
    {
        int barHeight = (height * height) / this.getContentHeight();

        if (barHeight < 32) barHeight = 32;

        if (barHeight > height - border*2)
            barHeight = height - border*2;

        return barHeight;
    }

    @Override
    public boolean func_231045_a_(double mouseX, double mouseY, int button, double deltaX, double deltaY)
    {
        if (this.scrolling)
        {
            int maxScroll = height - getBarHeight();
            double moved = deltaY / maxScroll;
            this.scrollDistance += getMaxScroll() * moved;
            applyScrollLimits();
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void func_230430_a_(MatrixStack matrix, int mouseX, int mouseY, float partialTicks)
    {
        this.drawBackground();

        Tessellator tess = Tessellator.func_178181_a();
        BufferBuilder worldr = tess.func_178180_c();

        double scale = client.func_228018_at_().func_198100_s();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int)(left  * scale), (int)(client.func_228018_at_().func_198091_l() - (bottom * scale)),
                       (int)(width * scale), (int)(height * scale));

        if (this.client.field_71441_e != null)
        {
            this.drawGradientRect(matrix, this.left, this.top, this.right, this.bottom, 0xC0101010, 0xD0101010);
        }
        else // Draw dark dirt background
        {
            RenderSystem.disableLighting();
            RenderSystem.disableFog();
            this.client.func_110434_K().func_110577_a(AbstractGui.field_230663_f_);
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            final float texScale = 32.0F;
            worldr.func_181668_a(GL11.GL_QUADS, DefaultVertexFormats.field_181709_i);
            worldr.func_225582_a_(this.left,  this.bottom, 0.0D).func_225583_a_(this.left  / texScale, (this.bottom + (int)this.scrollDistance) / texScale).func_225586_a_(0x20, 0x20, 0x20, 0xFF).func_181675_d();
            worldr.func_225582_a_(this.right, this.bottom, 0.0D).func_225583_a_(this.right / texScale, (this.bottom + (int)this.scrollDistance) / texScale).func_225586_a_(0x20, 0x20, 0x20, 0xFF).func_181675_d();
            worldr.func_225582_a_(this.right, this.top,    0.0D).func_225583_a_(this.right / texScale, (this.top    + (int)this.scrollDistance) / texScale).func_225586_a_(0x20, 0x20, 0x20, 0xFF).func_181675_d();
            worldr.func_225582_a_(this.left,  this.top,    0.0D).func_225583_a_(this.left  / texScale, (this.top    + (int)this.scrollDistance) / texScale).func_225586_a_(0x20, 0x20, 0x20, 0xFF).func_181675_d();
            tess.func_78381_a();
        }

        int baseY = this.top + border - (int)this.scrollDistance;
        this.drawPanel(matrix, right, baseY, tess, mouseX, mouseY);

        RenderSystem.disableDepthTest();

        int extraHeight = (this.getContentHeight() + border) - height;
        if (extraHeight > 0)
        {
            int barHeight = getBarHeight();

            int barTop = (int)this.scrollDistance * (height - barHeight) / extraHeight + this.top;
            if (barTop < this.top)
            {
                barTop = this.top;
            }

            RenderSystem.disableTexture();
            worldr.func_181668_a(GL11.GL_QUADS, DefaultVertexFormats.field_181709_i);
            worldr.func_225582_a_(barLeft,            this.bottom, 0.0D).func_225583_a_(0.0F, 1.0F).func_225586_a_(0x00, 0x00, 0x00, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft + barWidth, this.bottom, 0.0D).func_225583_a_(1.0F, 1.0F).func_225586_a_(0x00, 0x00, 0x00, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft + barWidth, this.top,    0.0D).func_225583_a_(1.0F, 0.0F).func_225586_a_(0x00, 0x00, 0x00, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft,            this.top,    0.0D).func_225583_a_(0.0F, 0.0F).func_225586_a_(0x00, 0x00, 0x00, 0xFF).func_181675_d();
            tess.func_78381_a();
            worldr.func_181668_a(GL11.GL_QUADS, DefaultVertexFormats.field_181709_i);
            worldr.func_225582_a_(barLeft,            barTop + barHeight, 0.0D).func_225583_a_(0.0F, 1.0F).func_225586_a_(0x80, 0x80, 0x80, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft + barWidth, barTop + barHeight, 0.0D).func_225583_a_(1.0F, 1.0F).func_225586_a_(0x80, 0x80, 0x80, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft + barWidth, barTop,             0.0D).func_225583_a_(1.0F, 0.0F).func_225586_a_(0x80, 0x80, 0x80, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft,            barTop,             0.0D).func_225583_a_(0.0F, 0.0F).func_225586_a_(0x80, 0x80, 0x80, 0xFF).func_181675_d();
            tess.func_78381_a();
            worldr.func_181668_a(GL11.GL_QUADS, DefaultVertexFormats.field_181709_i);
            worldr.func_225582_a_(barLeft,                barTop + barHeight - 1, 0.0D).func_225583_a_(0.0F, 1.0F).func_225586_a_(0xC0, 0xC0, 0xC0, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft + barWidth - 1, barTop + barHeight - 1, 0.0D).func_225583_a_(1.0F, 1.0F).func_225586_a_(0xC0, 0xC0, 0xC0, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft + barWidth - 1, barTop,                 0.0D).func_225583_a_(1.0F, 0.0F).func_225586_a_(0xC0, 0xC0, 0xC0, 0xFF).func_181675_d();
            worldr.func_225582_a_(barLeft,                barTop,                 0.0D).func_225583_a_(0.0F, 0.0F).func_225586_a_(0xC0, 0xC0, 0xC0, 0xFF).func_181675_d();
            tess.func_78381_a();
        }

        RenderSystem.enableTexture();
        RenderSystem.shadeModel(GL11.GL_FLAT);
        RenderSystem.enableAlphaTest();
        RenderSystem.disableBlend();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    protected void drawGradientRect(MatrixStack mStack, int left, int top, int right, int bottom, int color1, int color2)
    {
        GuiUtils.drawGradientRect(mStack.func_227866_c_().func_227870_a_(), 0, left, top, right, bottom, color1, color2);
    }

    @Override
    public List<? extends IGuiEventListener> func_231039_at__()
    {
        return Collections.emptyList();
    }
}
