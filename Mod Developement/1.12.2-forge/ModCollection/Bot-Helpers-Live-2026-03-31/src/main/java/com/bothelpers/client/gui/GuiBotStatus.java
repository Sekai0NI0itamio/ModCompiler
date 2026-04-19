package com.bothelpers.client.gui;

import com.bothelpers.entity.EntityBotHelper;
import com.bothelpers.inventory.ContainerBotHelper;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;

public class GuiBotStatus extends GuiContainer {

    private final EntityBotHelper bot;

    public GuiBotStatus(EntityPlayer player, EntityBotHelper botHelper) {
        super(new ContainerBotHelper(player.inventory, botHelper));
        this.bot = botHelper;
        this.xSize = 176;
        this.ySize = 250;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.bot.getDisplayName().getUnformattedText(), 8, 6, 4210752);
        this.fontRenderer.drawString("Bot armor", 8, 20, 4210752);
        this.fontRenderer.drawString("Bot offhand", 98, 20, 4210752);
        this.fontRenderer.drawString("Bot inventory", 8, 54, 4210752);
        this.fontRenderer.drawString("Bot hotbar", 8, 122, 4210752);
        this.fontRenderer.drawString("Player inventory", 8, 154, 4210752);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int i = (this.width - this.xSize) / 2;
        int j = (this.height - this.ySize) / 2;

        this.drawRect(i, j, i + this.xSize, j + this.ySize, 0xCC202020);
        this.drawRect(i + 6, j + 16, i + this.xSize - 6, j + 152, 0xAA2A2A2A);
        this.drawRect(i + 6, j + 162, i + this.xSize - 6, j + 244, 0xAA2A2A2A);

        drawSlotOutline(i + 8, j + 30);
        drawSlotOutline(i + 26, j + 30);
        drawSlotOutline(i + 44, j + 30);
        drawSlotOutline(i + 62, j + 30);
        drawSlotOutline(i + 98, j + 30);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotOutline(i + 8 + col * 18, j + 64 + row * 18);
            }
        }

        for (int col = 0; col < 9; col++) {
            drawSlotOutline(i + 8 + col * 18, j + 134);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotOutline(i + 8 + col * 18, j + 166 + row * 18);
            }
        }

        for (int col = 0; col < 9; col++) {
            drawSlotOutline(i + 8 + col * 18, j + 224);
        }
    }

    private void drawSlotOutline(int left, int top) {
        this.drawRect(left, top, left + 18, top + 18, 0x553A3A3A);
        this.drawHorizontalLine(left, left + 17, top, 0xAA909090);
        this.drawVerticalLine(left, top, top + 17, 0xAA909090);
        this.drawHorizontalLine(left, left + 17, top + 17, 0xAA141414);
        this.drawVerticalLine(left + 17, top, top + 17, 0xAA141414);
    }
}
