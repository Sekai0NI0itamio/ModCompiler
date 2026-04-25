/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.versions.forge.ForgeVersion;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.client.loading.ClientModLoader;
import net.minecraftforge.api.distmarker.Dist;

@OnlyIn(Dist.CLIENT)
public class NotificationModUpdateScreen extends Screen
{

    private static final ResourceLocation VERSION_CHECK_ICONS = new ResourceLocation(ForgeVersion.MOD_ID, "textures/gui/version_check_icons.png");

    private final Button modButton;
    private VersionChecker.Status showNotification = null;
    private boolean hasCheckedForUpdates = false;

    public NotificationModUpdateScreen(Button modButton)
    {
        super(new TranslatableComponent("forge.menu.updatescreen.title"));
        this.modButton = modButton;
    }

    @Override
    public void m_7856_()
    {
        if (!hasCheckedForUpdates)
        {
            if (modButton != null)
            {
                showNotification = ClientModLoader.checkForUpdates();
            }
            hasCheckedForUpdates = true;
        }
    }

    @Override
    public void m_6305_(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        if (showNotification == null || !showNotification.shouldDraw() || !FMLConfig.runVersionCheck())
        {
            return;
        }

        RenderSystem.m_157456_(0, VERSION_CHECK_ICONS);

        int x = modButton.f_93620_;
        int y = modButton.f_93621_;
        int w = modButton.m_5711_();
        int h = modButton.m_93694_();

        m_93133_(poseStack, x + w - (h / 2 + 4), y + (h / 2 - 4), showNotification.getSheetOffset() * 8, (showNotification.isAnimated() && ((System.currentTimeMillis() / 800 & 1) == 1)) ? 8 : 0, 8, 8, 64, 16);
    }

    public static NotificationModUpdateScreen init(TitleScreen guiMainMenu, Button modButton)
    {
        NotificationModUpdateScreen notificationModUpdateScreen = new NotificationModUpdateScreen(modButton);
        notificationModUpdateScreen.m_6574_(guiMainMenu.getMinecraft(), guiMainMenu.f_96543_, guiMainMenu.f_96544_);
        notificationModUpdateScreen.m_7856_();
        return notificationModUpdateScreen;
    }

}
