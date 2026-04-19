package com.bothelpers;

import com.bothelpers.entity.EntityBotHelper;
import com.bothelpers.inventory.ContainerBotHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {
    
    public static final int BOT_STATUS_GUI_ID = 0;
    public static final int BOT_INVENTORY_GUI_ID = 1;
    public static final int BOT_SCRATCH_GUI_ID = 2;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == BOT_INVENTORY_GUI_ID) {
            Entity entity = world.getEntityByID(x);
            if (entity instanceof EntityBotHelper) {
                return new ContainerBotHelper(player.inventory, (EntityBotHelper) entity);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        Entity entity = world.getEntityByID(x);
        if (entity instanceof EntityBotHelper) {
            if (ID == BOT_STATUS_GUI_ID) {
                return new com.bothelpers.client.gui.GuiBotStatusMenu(player, (EntityBotHelper) entity);
            } else if (ID == BOT_INVENTORY_GUI_ID) {
                return new com.bothelpers.client.gui.GuiBotStatus(player, (EntityBotHelper) entity);
            } else if (ID == BOT_SCRATCH_GUI_ID) {
                return null; // GuiScratchPopup was removed for BotJobEditorFrame 
            }
        }
        return null;
    }
}