/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

/**
 * All the vanilla {@linkplain IGuiOverlay HUD overlays} in the order that they render.
 */
public enum VanillaGuiOverlay
{
    VIGNETTE("vignette", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (Minecraft.m_91405_())
        {
            gui.setupOverlayRenderState(true, false);
            gui.m_264148_(poseStack, gui.getMinecraft().m_91288_());
        }
    }),
    SPYGLASS("spyglass", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.setupOverlayRenderState(true, false);
        gui.renderSpyglassOverlay(poseStack);
    }),
    HELMET("helmet", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.setupOverlayRenderState(true, false);
        gui.renderHelmet(partialTick, poseStack);
    }),
    FROSTBITE("frostbite", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.setupOverlayRenderState(true, false);
        gui.renderFrostbite(poseStack);
    }),
    PORTAL("portal", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {

        if (!gui.getMinecraft().f_91074_.m_21023_(MobEffects.f_19604_))
        {
            gui.setupOverlayRenderState(true, false);
            gui.m_264464_(poseStack, partialTick);
        }

    }),
    HOTBAR("hotbar", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            if (gui.getMinecraft().f_91072_.m_105295_() == GameType.SPECTATOR)
            {
                gui.m_93085_().m_193837_(poseStack);
            }
            else
            {
                gui.m_93009_(partialTick, poseStack);
            }
        }
    }),
    CROSSHAIR("crosshair", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);

            poseStack.m_85836_();
            poseStack.m_252880_(0, 0, -90);
            gui.m_93080_(poseStack);
            poseStack.m_85849_();
        }
    }),
    BOSS_EVENT_PROGRESS("boss_event_progress", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);

            poseStack.m_85836_();
            poseStack.m_252880_(0, 0, -90);
            gui.renderBossHealth(poseStack);
            poseStack.m_85849_();
        }
    }),
    PLAYER_HEALTH("player_health", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderHealth(screenWidth, screenHeight, poseStack);
        }
    }),
    ARMOR_LEVEL("armor_level", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderArmor(poseStack, screenWidth, screenHeight);
        }
    }),
    FOOD_LEVEL("food_level", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        boolean isMounted = gui.getMinecraft().f_91074_.m_20202_() instanceof LivingEntity;
        if (!isMounted && !gui.getMinecraft().f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderFood(screenWidth, screenHeight, poseStack);
        }
    }),
    MOUNT_HEALTH("mount_health", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderHealthMount(screenWidth, screenHeight, poseStack);
        }
    }),
    AIR_LEVEL("air_level", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderAir(screenWidth, screenHeight, poseStack);
        }
    }),
    JUMP_BAR("jump_bar", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        PlayerRideableJumping playerRideableJumping = gui.getMinecraft().f_91074_.m_245714_();
        if (playerRideableJumping != null && !gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            gui.m_247734_(playerRideableJumping, poseStack, screenWidth / 2 - 91);
        }
    }),
    EXPERIENCE_BAR("experience_bar", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (gui.getMinecraft().f_91074_.m_245714_() == null && !gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderExperience(screenWidth / 2 - 91, poseStack);
        }
    }),
    ITEM_NAME("item_name", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            if (gui.getMinecraft().f_91072_.m_105295_() != GameType.SPECTATOR)
            {
                gui.m_93069_(poseStack);
            }
            else if (gui.getMinecraft().f_91074_.m_5833_())
            {
                gui.m_93085_().m_94773_(poseStack);
            }
        }
    }),
    SLEEP_FADE("sleep_fade", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.renderSleepFade(screenWidth, screenHeight, poseStack);
    }),
    DEBUG_TEXT("debug_text", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.renderHUDText(screenWidth, screenHeight, poseStack);
    }),
    FPS_GRAPH("fps_graph", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.renderFPSGraph(poseStack);
    }),
    POTION_ICONS("potion_icons", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.m_93028_(poseStack);
    }),
    RECORD_OVERLAY("record_overlay", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.renderRecordOverlay(screenWidth, screenHeight, partialTick, poseStack);
        }
    }),
    SUBTITLES("subtitles", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.renderSubtitles(poseStack);
        }
    }),
    TITLE_TEXT("title_text", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.getMinecraft().f_91066_.f_92062_)
        {
            gui.renderTitle(screenWidth, screenHeight, partialTick, poseStack);
        }
    }),
    SCOREBOARD("scoreboard", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {

        Scoreboard scoreboard = gui.getMinecraft().f_91073_.m_6188_();
        Objective objective = null;
        PlayerTeam scoreplayerteam = scoreboard.m_83500_(gui.getMinecraft().f_91074_.m_6302_());
        if (scoreplayerteam != null)
        {
            int slot = scoreplayerteam.m_7414_().m_126656_();
            if (slot >= 0) objective = scoreboard.m_83416_(3 + slot);
        }
        Objective scoreobjective1 = objective != null ? objective : scoreboard.m_83416_(1);
        if (scoreobjective1 != null)
        {
            gui.m_93036_(poseStack, scoreobjective1);
        }
    }),
    CHAT_PANEL("chat_panel", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {

        RenderSystem.m_69478_();
        RenderSystem.m_69411_(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        gui.renderChat(screenWidth, screenHeight, poseStack);
    }),
    PLAYER_LIST("player_list", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {

        RenderSystem.m_69478_();
        RenderSystem.m_69411_(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        gui.renderPlayerList(screenWidth, screenHeight, poseStack);
    });

    private final ResourceLocation id;
    final IGuiOverlay overlay;
    NamedGuiOverlay type;

    VanillaGuiOverlay(String id, IGuiOverlay overlay)
    {
        this.id = new ResourceLocation("minecraft", id);
        this.overlay = overlay;
    }

    @NotNull
    public ResourceLocation id()
    {
        return id;
    }

    public NamedGuiOverlay type()
    {
        return type;
    }
}
