package com.bothelpers.client.gui;

import com.bothelpers.GuiHandler;
import com.bothelpers.PacketHandler;
import com.bothelpers.entity.EntityBotHelper;
import com.bothelpers.network.PacketOpenBotGui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import java.io.IOException;

public class GuiBotStatusMenu extends GuiScreen {

    private final EntityBotHelper bot;
    private final EntityPlayer player;

    public GuiBotStatusMenu(EntityPlayer player, EntityBotHelper bot) {
        this.player = player;
        this.bot = bot;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.buttonList.add(new GuiButton(0, cx - 75, cy - 10, 150, 20, "View Inventory"));
        this.buttonList.add(new GuiButton(1, cx - 75, cy + 15, 150, 20, "Job"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            PacketHandler.INSTANCE.sendToServer(new PacketOpenBotGui(bot.getEntityId(), GuiHandler.BOT_INVENTORY_GUI_ID));
        } else if (button.id == 1) {
            // Open external window
            javax.swing.SwingUtilities.invokeLater(() -> {
                new BotJobEditorFrame(this.bot).setVisible(true);
            });
            this.mc.displayGuiScreen(null); // close in-game screen
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        int cx = this.width / 2;
        int cy = this.height / 2;
        
        this.drawCenteredString(this.fontRenderer, bot.getName(), cx, cy - 78, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "Happiness: " + bot.getHappiness() + "/5", cx, cy - 60, 0x00FF00);
        this.drawCenteredString(this.fontRenderer, "Food: " + (int) bot.botFoodLevel + "/20", cx, cy - 50, 0x00FF00);
        this.drawCenteredString(this.fontRenderer, "Job: " + (bot.hasJob ? "Assigned" : "Idle"), cx, cy - 40, 0x00FF00);
        this.drawCenteredString(this.fontRenderer, "Days Since Sleep: " + bot.daysSinceLastSleep, cx, cy - 30, 0x00FF00);
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}