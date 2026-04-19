package com.loadmyworldproperly.client;

import com.loadmyworldproperly.LoadMyWorldProperlyMod;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class SingleplayerWorldLoadFixer {

    private static final int WORLD_READY_TICKS_BEFORE_FIX = 10;
    private static final int STUCK_TICKS_BEFORE_FIX = 40;
    private static final Field SKIP_RENDER_WORLD_FIELD = ReflectionHelper.findField(
        Minecraft.class,
        "skipRenderWorld",
        "field_71454_w"
    );

    private boolean sessionActive = false;
    private int worldReadyTicks = 0;
    private int stuckTicks = 0;
    private boolean fixApplied = false;

    public void onClientTick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            reset();
            return;
        }

        boolean singleplayerSession = minecraft.isSingleplayer() || minecraft.getIntegratedServer() != null;
        if (!singleplayerSession) {
            reset();
            return;
        }

        if (!sessionActive) {
            sessionActive = true;
            worldReadyTicks = 0;
            stuckTicks = 0;
            fixApplied = false;
        }

        boolean worldReady = minecraft.world != null && minecraft.player != null;
        boolean loadingScreenVisible = isLoadingScreen(minecraft.currentScreen);
        boolean skipRenderWorld = getSkipRenderWorld(minecraft);

        if (!worldReady) {
            worldReadyTicks = 0;
            stuckTicks = 0;
            fixApplied = false;
            return;
        }

        worldReadyTicks++;
        if (loadingScreenVisible || skipRenderWorld) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            if (fixApplied) {
                fixApplied = false;
            }
        }

        if (!fixApplied && worldReadyTicks >= WORLD_READY_TICKS_BEFORE_FIX && stuckTicks >= STUCK_TICKS_BEFORE_FIX) {
            forceFinishWorldEntry(minecraft);
            fixApplied = true;
        }
    }

    private void reset() {
        sessionActive = false;
        worldReadyTicks = 0;
        stuckTicks = 0;
        fixApplied = false;
    }

    private boolean isLoadingScreen(GuiScreen screen) {
        return screen instanceof GuiDownloadTerrain || screen instanceof GuiScreenWorking;
    }

    private boolean getSkipRenderWorld(Minecraft minecraft) {
        try {
            return SKIP_RENDER_WORLD_FIELD.getBoolean(minecraft);
        } catch (IllegalAccessException exception) {
            return false;
        }
    }

    private void setSkipRenderWorld(Minecraft minecraft, boolean value) {
        try {
            SKIP_RENDER_WORLD_FIELD.setBoolean(minecraft, value);
        } catch (IllegalAccessException exception) {
            LoadMyWorldProperlyMod.LOGGER.warn("Load My World PROPERLY could not update skipRenderWorld.", exception);
        }
    }

    private void forceFinishWorldEntry(Minecraft minecraft) {
        setSkipRenderWorld(minecraft, false);
        if (minecraft.renderGlobal != null) {
            minecraft.renderGlobal.loadRenderers();
        }
        minecraft.displayGuiScreen(null);
        if (minecraft.player != null) {
            minecraft.setRenderViewEntity(minecraft.player);
        }
        minecraft.setIngameFocus();
        LoadMyWorldProperlyMod.LOGGER.warn(
            "Load My World PROPERLY forced the client out of the stuck post-loading state for a singleplayer world."
        );
    }
}
