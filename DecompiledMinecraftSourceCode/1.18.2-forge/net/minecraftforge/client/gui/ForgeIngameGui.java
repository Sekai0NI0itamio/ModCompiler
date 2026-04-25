/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.gui;

import static net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.*;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.food.FoodData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.RenderProperties;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import javax.annotation.Nullable;

public class ForgeIngameGui extends Gui
{
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int WHITE = 0xFFFFFF;

    /*
     * If the Euclidian distance to the moused-over block in meters is less than this value, the "Looking at" text will appear on the debug overlay.
     */
    public static double rayTraceDistance = 20.0D;

    public int left_height = 39;
    public int right_height = 39;

    private Font font = null;
    private RenderGameOverlayEvent eventParent;

    private ForgeDebugScreenOverlay debugOverlay;

    public void setupOverlayRenderState(boolean blend, boolean depthText)
    {
        setupOverlayRenderState(blend, depthText, Gui.f_93098_);
    }

    public void setupOverlayRenderState(boolean blend, boolean depthTest, @Nullable ResourceLocation texture)
    {
        if (blend)
        {
            RenderSystem.m_69478_();
            RenderSystem.m_69453_();
        }
        else
        {
            RenderSystem.m_69461_();
        }

        if (depthTest)
        {
            RenderSystem.m_69482_();
        }
        else
        {
            RenderSystem.m_69465_();
        }

        if (texture != null)
        {
            RenderSystem.m_69493_();
            bind(texture);
        }
        else
        {
            RenderSystem.m_69472_();
        }

        RenderSystem.m_157429_(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.m_157427_(GameRenderer::m_172817_);
    }

    public static final IIngameOverlay VIGNETTE_ELEMENT = OverlayRegistry.registerOverlayTop("Vignette", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (Minecraft.m_91405_())
        {
            gui.setupOverlayRenderState(true, false);
            gui.m_93067_(gui.f_92986_.m_91288_());
        }
    });

    public static final IIngameOverlay SPYGLASS_ELEMENT = OverlayRegistry.registerOverlayTop("Spyglass", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.setupOverlayRenderState(true, false);
        gui.renderSpyglassOverlay();
    });

    public static final IIngameOverlay HELMET_ELEMENT = OverlayRegistry.registerOverlayTop("Helmet", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.setupOverlayRenderState(true, false);
        gui.renderHelmet(partialTick, poseStack);
    });

    public static final IIngameOverlay FROSTBITE_ELEMENT = OverlayRegistry.registerOverlayTop("Frostbite", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.setupOverlayRenderState(true, false);
        gui.renderFrostbite(poseStack);
    });

    public static final IIngameOverlay PORTAL_ELEMENT = OverlayRegistry.registerOverlayTop("Portal", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {

        if (!gui.f_92986_.f_91074_.m_21023_(MobEffects.f_19604_))
        {
            gui.setupOverlayRenderState(true, false);
            gui.m_93007_(partialTick);
        }

    });

    public static final IIngameOverlay HOTBAR_ELEMENT = OverlayRegistry.registerOverlayTop("Hotbar", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            if (gui.f_92986_.f_91072_.m_105295_() == GameType.SPECTATOR)
            {
                gui.f_92997_.m_193837_(poseStack);
            }
            else
            {
                gui.m_93009_(partialTick, poseStack);
            }
        }
    });

    public static final IIngameOverlay CROSSHAIR_ELEMENT = OverlayRegistry.registerOverlayTop("Crosshair", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            gui.m_93250_(-90);

            gui.m_93080_(poseStack);
        }
    });

    public static final IIngameOverlay BOSS_HEALTH_ELEMENT = OverlayRegistry.registerOverlayTop("Boss Health", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            gui.m_93250_(-90);

            gui.renderBossHealth(poseStack);
        }
    });

    public static final IIngameOverlay PLAYER_HEALTH_ELEMENT = OverlayRegistry.registerOverlayTop("Player Health", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderHealth(screenWidth, screenHeight, poseStack);
        }
    });

    public static final IIngameOverlay ARMOR_LEVEL_ELEMENT = OverlayRegistry.registerOverlayTop("Armor Level",(gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderArmor(poseStack, screenWidth, screenHeight);
        }
    });

    public static final IIngameOverlay FOOD_LEVEL_ELEMENT = OverlayRegistry.registerOverlayTop("Food Level", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        boolean isMounted = gui.f_92986_.f_91074_.m_20202_() instanceof LivingEntity;
        if (!isMounted && !gui.f_92986_.f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderFood(screenWidth, screenHeight, poseStack);
        }
    });

    public static final IIngameOverlay MOUNT_HEALTH_ELEMENT = OverlayRegistry.registerOverlayTop("Mount Health", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderHealthMount(screenWidth, screenHeight, poseStack);
        }
    });

    public static final IIngameOverlay AIR_LEVEL_ELEMENT = OverlayRegistry.registerOverlayTop("Air Level", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_ && gui.shouldDrawSurvivalElements())
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderAir(screenWidth, screenHeight, poseStack);
        }
    });

    public static final IIngameOverlay JUMP_BAR_ELEMENT = OverlayRegistry.registerOverlayTop("Jump Bar", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (gui.f_92986_.f_91074_.m_108633_() && !gui.f_92986_.f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            gui.m_93033_(poseStack, screenWidth / 2 - 91);
        }
    });

    public static final IIngameOverlay EXPERIENCE_BAR_ELEMENT = OverlayRegistry.registerOverlayTop("Experience Bar", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91074_.m_108633_() && !gui.f_92986_.f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            gui.renderExperience(screenWidth / 2 - 91, poseStack);
        }
    });

    public static final IIngameOverlay ITEM_NAME_ELEMENT = OverlayRegistry.registerOverlayTop("Item Name", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_)
        {
            gui.setupOverlayRenderState(true, false);
            if (gui.f_92986_.f_91066_.f_92130_ && gui.f_92986_.f_91072_.m_105295_() != GameType.SPECTATOR) {
                gui.m_93069_(poseStack);
            } else if (gui.f_92986_.f_91074_.m_5833_()) {
                gui.f_92997_.m_94773_(poseStack);
            }
        }
    });

    public static final IIngameOverlay SLEEP_FADE_ELEMENT = OverlayRegistry.registerOverlayTop("Sleep Fade", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.renderSleepFade(screenWidth, screenHeight, poseStack);
    });

    public static final IIngameOverlay HUD_TEXT_ELEMENT = OverlayRegistry.registerOverlayTop("Text Columns", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.renderHUDText(screenWidth, screenHeight, poseStack);
    });

    public static final IIngameOverlay FPS_GRAPH_ELEMENT = OverlayRegistry.registerOverlayTop("FPS Graph", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.renderFPSGraph(poseStack);
    });

    public static final IIngameOverlay POTION_ICONS_ELEMENT = OverlayRegistry.registerOverlayTop("Potion Icons", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        gui.m_93028_(poseStack);
    });

    public static final IIngameOverlay RECORD_OVERLAY_ELEMENT = OverlayRegistry.registerOverlayTop("Record", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_)
        {
            gui.renderRecordOverlay(screenWidth, screenHeight, partialTick, poseStack);
        }
    });

    public static final IIngameOverlay SUBTITLES_ELEMENT = OverlayRegistry.registerOverlayTop("Subtitles", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_)
        {
            gui.renderSubtitles(poseStack);
        }
    });

    public static final IIngameOverlay TITLE_TEXT_ELEMENT = OverlayRegistry.registerOverlayTop("Title Text", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        if (!gui.f_92986_.f_91066_.f_92062_)
        {
            gui.renderTitle(screenWidth, screenHeight, partialTick, poseStack);
        }
    });

    public static final IIngameOverlay SCOREBOARD_ELEMENT = OverlayRegistry.registerOverlayTop("Scoreboard", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {

        Scoreboard scoreboard = gui.f_92986_.f_91073_.m_6188_();
        Objective  objective = null;
        PlayerTeam  scoreplayerteam = scoreboard.m_83500_(gui.f_92986_.f_91074_.m_6302_());
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
    });

    public static final IIngameOverlay CHAT_PANEL_ELEMENT = OverlayRegistry.registerOverlayTop("Chat History", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {

        RenderSystem.m_69478_();
        RenderSystem.m_69411_(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        gui.renderChat(screenWidth, screenHeight, poseStack);
    });

    public static final IIngameOverlay PLAYER_LIST_ELEMENT = OverlayRegistry.registerOverlayTop("Player List", (gui, poseStack, partialTick, screenWidth, screenHeight) -> {

        RenderSystem.m_69478_();
        RenderSystem.m_69411_(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        gui.renderPlayerList(screenWidth, screenHeight, poseStack);
    });

    public ForgeIngameGui(Minecraft mc)
    {
        super(mc);
        debugOverlay = new ForgeDebugScreenOverlay(mc);
    }

    @Override
    public void m_93030_(PoseStack poseStack, float partialTick)
    {
        this.f_92977_ = this.f_92986_.m_91268_().m_85445_();
        this.f_92978_ = this.f_92986_.m_91268_().m_85446_();
        eventParent = new RenderGameOverlayEvent(poseStack, partialTick, this.f_92986_.m_91268_());

        right_height = 39;
        left_height = 39;

        if (pre(ALL, poseStack)) return;

        font = f_92986_.f_91062_;

        this.f_92985_.setSeed(f_92989_ * 312871L);

        OverlayRegistry.orderedEntries().forEach(entry -> {
            try
            {
                if (!entry.isEnabled()) return;
                IIngameOverlay overlay = entry.getOverlay();
                if (pre(overlay, poseStack)) return;
                overlay.render(this, poseStack, partialTick, f_92977_, f_92978_);
                post(overlay, poseStack);
            }
            catch(Exception e)
            {
                LOGGER.error("Error rendering overlay '{}'", entry.getDisplayName(), e);
            }
        });

        RenderSystem.m_157429_(1.0F, 1.0F, 1.0F, 1.0F);

        post(ALL, poseStack);
    }

    public boolean shouldDrawSurvivalElements()
    {
        return f_92986_.f_91072_.m_105205_() && f_92986_.m_91288_() instanceof Player;
    }

    protected void renderSubtitles(PoseStack poseStack)
    {
        this.f_92996_.m_94642_(poseStack);
    }

    protected void renderBossHealth(PoseStack poseStack)
    {
        bind(GuiComponent.f_93098_);
        RenderSystem.m_69453_();
        f_92986_.m_91307_().m_6180_("bossHealth");
        this.f_92999_.m_93704_(poseStack);
        f_92986_.m_91307_().m_7238_();
    }

    private void renderSpyglassOverlay()
    {
        float deltaFrame = this.f_92986_.m_91297_();
        this.f_168664_ = Mth.m_14179_(0.5F * deltaFrame, this.f_168664_, 1.125F);
        if (this.f_92986_.f_91066_.m_92176_().m_90612_())
        {
            if (this.f_92986_.f_91074_.m_150108_())
            {
                this.m_168675_(this.f_168664_);
            } else
            {
                this.f_168664_ = 0.5F;
            }
        }
    }

    private void renderHelmet(float partialTick, PoseStack poseStack)
    {
        ItemStack itemstack = this.f_92986_.f_91074_.m_150109_().m_36052_(3);

        if (this.f_92986_.f_91066_.m_92176_().m_90612_() && !itemstack.m_41619_())
        {
            Item item = itemstack.m_41720_();
            if (item == Blocks.f_50143_.m_5456_())
            {
                m_168708_(f_92983_, 1.0F);
            }
            else
            {
                RenderProperties.get(item).renderHelmetOverlay(itemstack, f_92986_.f_91074_, this.f_92977_, this.f_92978_, partialTick);
            }
        }
    }

    private void renderFrostbite(PoseStack pStack)
    {
        if (this.f_92986_.f_91074_.m_146888_() > 0) {
            this.m_168708_(f_168666_, this.f_92986_.f_91074_.m_146889_());
        }
    }

    protected void renderArmor(PoseStack poseStack, int width, int height)
    {
        f_92986_.m_91307_().m_6180_("armor");

        RenderSystem.m_69478_();
        int left = width / 2 - 91;
        int top = height - left_height;

        int level = f_92986_.f_91074_.m_21230_();
        for (int i = 1; level > 0 && i < 20; i += 2)
        {
            if (i < level)
            {
                m_93228_(poseStack, left, top, 34, 9, 9, 9);
            }
            else if (i == level)
            {
                m_93228_(poseStack, left, top, 25, 9, 9, 9);
            }
            else if (i > level)
            {
                m_93228_(poseStack, left, top, 16, 9, 9, 9);
            }
            left += 8;
        }
        left_height += 10;

        RenderSystem.m_69461_();
        f_92986_.m_91307_().m_7238_();
    }

    @Override
    protected void m_93007_(float partialTick)
    {
        float f1 = Mth.m_14179_(partialTick, this.f_92986_.f_91074_.f_108590_, this.f_92986_.f_91074_.f_108589_);

        if (f1 > 0.0F)
        {
            super.m_93007_(f1);
        }
    }

    protected void renderAir(int width, int height, PoseStack poseStack)
    {
        f_92986_.m_91307_().m_6180_("air");
        Player player = (Player)this.f_92986_.m_91288_();
        RenderSystem.m_69478_();
        int left = width / 2 + 91;
        int top = height - right_height;

        int air = player.m_20146_();
        if (player.m_204029_(FluidTags.f_13131_) || air < 300)
        {
            int full = Mth.m_14165_((double)(air - 2) * 10.0D / 300.0D);
            int partial = Mth.m_14165_((double)air * 10.0D / 300.0D) - full;

            for (int i = 0; i < full + partial; ++i)
            {
                m_93228_(poseStack, left - i * 8 - 9, top, (i < full ? 16 : 25), 18, 9, 9);
            }
            right_height += 10;
        }

        RenderSystem.m_69461_();
        f_92986_.m_91307_().m_7238_();
    }

    public void renderHealth(int width, int height, PoseStack pStack)
    {
        bind(f_93098_);
        f_92986_.m_91307_().m_6180_("health");
        RenderSystem.m_69478_();

        Player player = (Player)this.f_92986_.m_91288_();
        int health = Mth.m_14167_(player.m_21223_());
        boolean highlight = f_92976_ > (long)f_92989_ && (f_92976_ - (long)f_92989_) / 3L %2L == 1L;

        if (health < this.f_92973_ && player.f_19802_ > 0)
        {
            this.f_92975_ = Util.m_137550_();
            this.f_92976_ = (long)(this.f_92989_ + 20);
        }
        else if (health > this.f_92973_ && player.f_19802_ > 0)
        {
            this.f_92975_ = Util.m_137550_();
            this.f_92976_ = (long)(this.f_92989_ + 10);
        }

        if (Util.m_137550_() - this.f_92975_ > 1000L)
        {
            this.f_92973_ = health;
            this.f_92974_ = health;
            this.f_92975_ = Util.m_137550_();
        }

        this.f_92973_ = health;
        int healthLast = this.f_92974_;

        AttributeInstance attrMaxHealth = player.m_21051_(Attributes.f_22276_);
        float healthMax = Math.max((float)attrMaxHealth.m_22135_(), Math.max(healthLast, health));
        int absorb = Mth.m_14167_(player.m_6103_());

        int healthRows = Mth.m_14167_((healthMax + absorb) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);

        this.f_92985_.setSeed((long)(f_92989_ * 312871));

        int left = width / 2 - 91;
        int top = height - left_height;
        left_height += (healthRows * rowHeight);
        if (rowHeight != 10) left_height += 10 - rowHeight;

        int regen = -1;
        if (player.m_21023_(MobEffects.f_19605_))
        {
            regen = this.f_92989_ % Mth.m_14167_(healthMax + 5.0F);
        }

        this.m_168688_(pStack, player, left, top, rowHeight, regen, healthMax, health, healthLast, absorb, highlight);

        RenderSystem.m_69461_();
        f_92986_.m_91307_().m_7238_();
    }

    public void renderFood(int width, int height, PoseStack poseStack)
    {
        f_92986_.m_91307_().m_6180_("food");

        Player player = (Player)this.f_92986_.m_91288_();
        RenderSystem.m_69478_();
        int left = width / 2 + 91;
        int top = height - right_height;
        right_height += 10;
        boolean unused = false;// Unused flag in vanilla, seems to be part of a 'fade out' mechanic

        FoodData stats = f_92986_.f_91074_.m_36324_();
        int level = stats.m_38702_();

        for (int i = 0; i < 10; ++i)
        {
            int idx = i * 2 + 1;
            int x = left - i * 8 - 9;
            int y = top;
            int icon = 16;
            byte background = 0;

            if (f_92986_.f_91074_.m_21023_(MobEffects.f_19612_))
            {
                icon += 36;
                background = 13;
            }
            if (unused) background = 1; //Probably should be a += 1 but vanilla never uses this

            if (player.m_36324_().m_38722_() <= 0.0F && f_92989_ % (level * 3 + 1) == 0)
            {
                y = top + (f_92985_.nextInt(3) - 1);
            }

            m_93228_(poseStack, x, y, 16 + background * 9, 27, 9, 9);

            if (idx < level)
                m_93228_(poseStack, x, y, icon + 36, 27, 9, 9);
            else if (idx == level)
                m_93228_(poseStack, x, y, icon + 45, 27, 9, 9);
        }
        RenderSystem.m_69461_();
        f_92986_.m_91307_().m_7238_();
    }

    protected void renderSleepFade(int width, int height, PoseStack poseStack)
    {
        if (f_92986_.f_91074_.m_36318_() > 0)
        {
            f_92986_.m_91307_().m_6180_("sleep");
            RenderSystem.m_69465_();
            // RenderSystem.disableAlphaTest();
            int sleepTime = f_92986_.f_91074_.m_36318_();
            float opacity = (float)sleepTime / 100.0F;

            if (opacity > 1.0F)
            {
                opacity = 1.0F - (float)(sleepTime - 100) / 10.0F;
            }

            int color = (int)(220.0F * opacity) << 24 | 1052704;
            m_93172_(poseStack, 0, 0, width, height, color);
            // RenderSystem.enableAlphaTest();
            RenderSystem.m_69482_();
            f_92986_.m_91307_().m_7238_();
        }
    }

    protected void renderExperience(int x, PoseStack poseStack)
    {
        bind(f_93098_);
        RenderSystem.m_157429_(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.m_69461_();

        if (f_92986_.f_91072_.m_105288_())
        {
            super.m_93071_(poseStack, x);
        }
        RenderSystem.m_69478_();
        RenderSystem.m_157429_(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void m_93033_(PoseStack poseStack, int x)
    {
        bind(f_93098_);
        RenderSystem.m_157429_(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.m_69461_();

        super.m_93033_(poseStack, x);

        RenderSystem.m_69478_();
        f_92986_.m_91307_().m_7238_();
        RenderSystem.m_157429_(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected void renderHUDText(int width, int height, PoseStack poseStack)
    {
        f_92986_.m_91307_().m_6180_("forgeHudText");
        RenderSystem.m_69453_();
        ArrayList<String> listL = new ArrayList<String>();
        ArrayList<String> listR = new ArrayList<String>();

        if (f_92986_.m_91402_())
        {
            long time = f_92986_.f_91073_.m_46467_();
            if (time >= 120500L)
            {
                listR.add(I18n.m_118938_("demo.demoExpired"));
            }
            else
            {
                listR.add(I18n.m_118938_("demo.remainingTime", StringUtil.m_14404_((int)(120500L - time))));
            }
        }

        if (this.f_92986_.f_91066_.f_92063_ && !pre(DEBUG, poseStack))
        {
            debugOverlay.update();
            listL.addAll(debugOverlay.getLeft());
            listR.addAll(debugOverlay.getRight());
            post(DEBUG, poseStack);
        }

        RenderGameOverlayEvent.Text event = new RenderGameOverlayEvent.Text(poseStack, eventParent, listL, listR);
        if (!MinecraftForge.EVENT_BUS.post(event))
        {
            int top = 2;
            for (String msg : listL)
            {
                if (msg != null && !msg.isEmpty())
                {
                    m_93172_(poseStack, 1, top - 1, 2 + font.m_92895_(msg) + 1, top + font.f_92710_ - 1, -1873784752);
                    font.m_92883_(poseStack, msg, 2, top, 14737632);
                }
                top += font.f_92710_;
            }

            top = 2;
            for (String msg : listR)
            {
                if (msg != null && !msg.isEmpty())
                {
                    int w = font.m_92895_(msg);
                    int left = width - 2 - w;
                    m_93172_(poseStack, left - 1, top - 1, left + w + 1, top + font.f_92710_ - 1, -1873784752);
                    font.m_92883_(poseStack, msg, left, top, 14737632);
                }
                top += font.f_92710_;
            }
        }

        f_92986_.m_91307_().m_7238_();
        post(TEXT, poseStack);
    }

    protected void renderFPSGraph(PoseStack poseStack)
    {
        if (this.f_92986_.f_91066_.f_92063_ && this.f_92986_.f_91066_.f_92065_)
        {
            this.debugOverlay.m_94056_(poseStack);
        }
    }

    protected void renderRecordOverlay(int width, int height, float partialTick, PoseStack pStack)
    {
        if (f_92991_ > 0)
        {
            f_92986_.m_91307_().m_6180_("overlayMessage");
            float hue = (float)f_92991_ - partialTick;
            int opacity = (int)(hue * 255.0F / 20.0F);
            if (opacity > 255) opacity = 255;

            if (opacity > 8)
            {
                pStack.m_85836_();
                pStack.m_85837_(width / 2D, height - 68, 0.0D);
                RenderSystem.m_69478_();
                RenderSystem.m_69453_();
                int color = (f_92992_ ? Mth.m_14169_(hue / 50.0F, 0.7F, 0.6F) & WHITE : WHITE);
                m_93039_(pStack, font, -4, font.m_92852_(f_92990_), 16777215 | (opacity << 24));
                font.m_92877_(pStack, f_92990_.m_7532_(), -font.m_92852_(f_92990_) / 2, -4, color | (opacity << 24));
                RenderSystem.m_69461_();
                pStack.m_85849_();
            }

            f_92986_.m_91307_().m_7238_();
        }
    }

    protected void renderTitle(int width, int height, float partialTick, PoseStack pStack)
    {
        if (f_93001_ != null && f_93000_ > 0)
        {
            f_92986_.m_91307_().m_6180_("titleAndSubtitle");
            float age = (float)this.f_93000_ - partialTick;
            int opacity = 255;

            if (f_93000_ > f_92972_ + f_92971_)
            {
                float f3 = (float)(f_92970_ + f_92971_ + f_92972_) - age;
                opacity = (int)(f3 * 255.0F / (float)f_92970_);
            }
            if (f_93000_ <= f_92972_) opacity = (int)(age * 255.0F / (float)this.f_92972_);

            opacity = Mth.m_14045_(opacity, 0, 255);

            if (opacity > 8)
            {
                pStack.m_85836_();
                pStack.m_85837_(width / 2D, height / 2D, 0.0D);
                RenderSystem.m_69478_();
                RenderSystem.m_69453_();
                pStack.m_85836_();
                pStack.m_85841_(4.0F, 4.0F, 4.0F);
                int l = opacity << 24 & -16777216;
                this.m_93082_().m_92744_(pStack, this.f_93001_.m_7532_(), (float)(-this.m_93082_().m_92852_(this.f_93001_) / 2), -10.0F, 16777215 | l);
                pStack.m_85849_();
                if (this.f_93002_ != null)
                {
                    pStack.m_85836_();
                    pStack.m_85841_(2.0F, 2.0F, 2.0F);
                    this.m_93082_().m_92744_(pStack, this.f_93002_.m_7532_(), (float)(-this.m_93082_().m_92852_(this.f_93002_) / 2), 5.0F, 16777215 | l);
                    pStack.m_85849_();
                }
                RenderSystem.m_69461_();
                pStack.m_85849_();
            }

            this.f_92986_.m_91307_().m_7238_();
        }
    }

    protected void renderChat(int width, int height, PoseStack pStack)
    {
        f_92986_.m_91307_().m_6180_("chat");

        RenderGameOverlayEvent.Chat event = new RenderGameOverlayEvent.Chat(pStack, eventParent, 0, height - 48);
        if (MinecraftForge.EVENT_BUS.post(event)) return;

        pStack.m_85836_();
        pStack.m_85837_(event.getPosX(), event.getPosY(), 0.0D);
        f_92988_.m_93780_(pStack, f_92989_);
        pStack.m_85849_();

        post(CHAT, pStack);

        f_92986_.m_91307_().m_7238_();
    }

    protected void renderPlayerList(int width, int height, PoseStack poseStack)
    {
        Objective scoreobjective = this.f_92986_.f_91073_.m_6188_().m_83416_(0);
        ClientPacketListener handler = f_92986_.f_91074_.f_108617_;

        if (f_92986_.f_91066_.f_92099_.m_90857_() && (!f_92986_.m_91090_() || handler.m_105142_().size() > 1 || scoreobjective != null))
        {
            this.f_92998_.m_94556_(true);
            if (pre(PLAYER_LIST, poseStack)) return;
            this.f_92998_.m_94544_(poseStack, width, this.f_92986_.f_91073_.m_6188_(), scoreobjective);
            post(PLAYER_LIST, poseStack);
        }
        else
        {
            this.f_92998_.m_94556_(false);
        }
    }

    protected void renderHealthMount(int width, int height, PoseStack poseStack)
    {
        Player player = (Player)f_92986_.m_91288_();
        Entity tmp = player.m_20202_();
        if (!(tmp instanceof LivingEntity)) return;

        bind(f_93098_);

        boolean unused = false;
        int left_align = width / 2 + 91;

        f_92986_.m_91307_().m_6182_("mountHealth");
        RenderSystem.m_69478_();
        LivingEntity mount = (LivingEntity)tmp;
        int health = (int)Math.ceil((double)mount.m_21223_());
        float healthMax = mount.m_21233_();
        int hearts = (int)(healthMax + 0.5F) / 2;

        if (hearts > 30) hearts = 30;

        final int MARGIN = 52;
        final int BACKGROUND = MARGIN + (unused ? 1 : 0);
        final int HALF = MARGIN + 45;
        final int FULL = MARGIN + 36;

        for (int heart = 0; hearts > 0; heart += 20)
        {
            int top = height - right_height;

            int rowCount = Math.min(hearts, 10);
            hearts -= rowCount;

            for (int i = 0; i < rowCount; ++i)
            {
                int x = left_align - i * 8 - 9;
                m_93228_(poseStack, x, top, BACKGROUND, 9, 9, 9);

                if (i * 2 + 1 + heart < health)
                    m_93228_(poseStack, x, top, FULL, 9, 9, 9);
                else if (i * 2 + 1 + heart == health)
                    m_93228_(poseStack, x, top, HALF, 9, 9, 9);
            }

            right_height += 10;
        }
        RenderSystem.m_69461_();
    }

    //Helper macros
    private boolean pre(ElementType type, PoseStack poseStack)
    {
        return MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Pre(poseStack, eventParent, type));
    }
    private void post(ElementType type, PoseStack poseStack)
    {
        MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(poseStack, eventParent, type));
    }
    private boolean pre(IIngameOverlay overlay, PoseStack poseStack)
    {
        return MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.PreLayer(poseStack, eventParent, overlay));
    }
    private void post(IIngameOverlay overlay, PoseStack poseStack)
    {
        MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.PostLayer(poseStack, eventParent, overlay));
    }
    private void bind(ResourceLocation res)
    {
        RenderSystem.m_157456_(0, res);
    }

    private class ForgeDebugScreenOverlay extends DebugScreenOverlay
    {
        private Minecraft mc;
        private ForgeDebugScreenOverlay(Minecraft mc)
        {
            super(mc);
            this.mc = mc;
        }
        public void update()
        {
            Entity entity = this.mc.m_91288_();
            this.f_94032_ = entity.m_19907_(rayTraceDistance, 0.0F, false);
            this.f_94033_ = entity.m_19907_(rayTraceDistance, 0.0F, true);
        }
        @Override protected void m_94076_(PoseStack poseStack){}
        @Override protected void m_94079_(PoseStack poseStack){}
        private List<String> getLeft()
        {
            List<String> ret = this.m_94075_();
            ret.add("");
            boolean flag = this.mc.m_91092_() != null;
            ret.add("Debug: Pie [shift]: " + (this.mc.f_91066_.f_92064_ ? "visible" : "hidden") + (flag ? " FPS + TPS" : " FPS") + " [alt]: " + (this.mc.f_91066_.f_92065_ ? "visible" : "hidden"));
            ret.add("For help: press F3 + Q");
            return ret;
        }
        private List<String> getRight(){ return this.m_94078_(); }
    }
}
