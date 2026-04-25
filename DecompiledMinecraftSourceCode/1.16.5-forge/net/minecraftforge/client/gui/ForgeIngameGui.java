/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.gui;

import static net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.*;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.IngameGui;
import net.minecraft.client.gui.overlay.DebugOverlayGui;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.potion.Effects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

@SuppressWarnings("deprecation")
public class ForgeIngameGui extends IngameGui
{
    //private static final ResourceLocation VIGNETTE     = new ResourceLocation("textures/misc/vignette.png");
    //private static final ResourceLocation WIDGITS      = new ResourceLocation("textures/gui/widgets.png");
    //private static final ResourceLocation PUMPKIN_BLUR = new ResourceLocation("textures/misc/pumpkinblur.png");

    private static final int WHITE = 0xFFFFFF;

    //Flags to toggle the rendering of certain aspects of the HUD, valid conditions
    //must be met for them to render normally. If those conditions are met, but this flag
    //is false, they will not be rendered.
    public static boolean renderVignette = true;
    public static boolean renderHelmet = true;
    public static boolean renderPortal = true;
    public static boolean renderSpectatorTooltip = true;
    public static boolean renderHotbar = true;
    public static boolean renderCrosshairs = true;
    public static boolean renderBossHealth = true;
    public static boolean renderHealth = true;
    public static boolean renderArmor = true;
    public static boolean renderFood = true;
    public static boolean renderHealthMount = true;
    public static boolean renderAir = true;
    public static boolean renderExperiance = true;
    public static boolean renderJumpBar = true;
    public static boolean renderObjective = true;

    public static int left_height = 39;
    public static int right_height = 39;
    /*
     * If the Euclidian distance to the moused-over block in meters is less than this value, the "Looking at" text will appear on the debug overlay.
     */
    public static double rayTraceDistance = 20.0D;

    private FontRenderer fontrenderer = null;
    private RenderGameOverlayEvent eventParent;
    //private static final String MC_VERSION = MinecraftForge.MC_VERSION;
    private GuiOverlayDebugForge debugOverlay;

    public ForgeIngameGui(Minecraft mc)
    {
        super(mc);
        debugOverlay = new GuiOverlayDebugForge(mc);
    }

    @Override
    public void func_238445_a_(MatrixStack mStack, float partialTicks)
    {
        this.field_194811_H = this.field_73839_d.func_228018_at_().func_198107_o();
        this.field_194812_I = this.field_73839_d.func_228018_at_().func_198087_p();
        eventParent = new RenderGameOverlayEvent(mStack, partialTicks, this.field_73839_d.func_228018_at_());
        renderHealthMount = field_73839_d.field_71439_g.func_184187_bx() instanceof LivingEntity;
        renderFood = !renderHealthMount;
        renderJumpBar = field_73839_d.field_71439_g.func_110317_t();

        right_height = 39;
        left_height = 39;

        if (pre(ALL, mStack)) return;

        fontrenderer = field_73839_d.field_71466_p;
        //mc.entityRenderer.setupOverlayRendering();
        RenderSystem.enableBlend();
        if (renderVignette && Minecraft.func_71375_t())
        {
            func_212303_b(field_73839_d.func_175606_aa());
        }
        else
        {
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
        }

        if (renderHelmet) renderHelmet(partialTicks, mStack);

        if (renderPortal && !field_73839_d.field_71439_g.func_70644_a(Effects.field_76431_k))
        {
            func_194805_e(partialTicks);
        }

        if (this.field_73839_d.field_71442_b.func_178889_l() == GameType.SPECTATOR)
        {
            if (renderSpectatorTooltip) field_175197_u.func_238528_a_(mStack, partialTicks);
        }
        else if (!this.field_73839_d.field_71474_y.field_74319_N)
        {
            if (renderHotbar) func_238443_a_(partialTicks, mStack);
        }

        if (!this.field_73839_d.field_71474_y.field_74319_N) {
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            func_230926_e_(-90);
            field_73842_c.setSeed((long)(field_73837_f * 312871));

            if (renderCrosshairs) func_238456_d_(mStack);
            if (renderBossHealth) renderBossHealth(mStack);

            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (this.field_73839_d.field_71442_b.func_78755_b() && this.field_73839_d.func_175606_aa() instanceof PlayerEntity)
            {
                if (renderHealth) renderHealth(this.field_194811_H, this.field_194812_I, mStack);
                if (renderArmor)  renderArmor(mStack, this.field_194811_H, this.field_194812_I);
                if (renderFood)   renderFood(this.field_194811_H, this.field_194812_I, mStack);
                if (renderHealthMount) renderHealthMount(this.field_194811_H, this.field_194812_I, mStack);
                if (renderAir)    renderAir(this.field_194811_H, this.field_194812_I, mStack);
            }

            if (renderJumpBar)
            {
                func_238446_a_(mStack, this.field_194811_H / 2 - 91);
            }
            else if (renderExperiance)
            {
                renderExperience(this.field_194811_H / 2 - 91, mStack);
            }
            if (this.field_73839_d.field_71474_y.field_92117_D && this.field_73839_d.field_71442_b.func_178889_l() != GameType.SPECTATOR) {
                this.func_238453_b_(mStack);
             } else if (this.field_73839_d.field_71439_g.func_175149_v()) {
                this.field_175197_u.func_238527_a_(mStack);
             }
        }

        renderSleepFade(this.field_194811_H, this.field_194812_I, mStack);

        renderHUDText(this.field_194811_H, this.field_194812_I, mStack);
        renderFPSGraph(mStack);
        func_238444_a_(mStack);
        if (!field_73839_d.field_71474_y.field_74319_N) {
            renderRecordOverlay(this.field_194811_H, this.field_194812_I, partialTicks, mStack);
            renderSubtitles(mStack);
            renderTitle(this.field_194811_H, this.field_194812_I, partialTicks, mStack);
        }


        Scoreboard scoreboard = this.field_73839_d.field_71441_e.func_96441_U();
        ScoreObjective objective = null;
        ScorePlayerTeam scoreplayerteam = scoreboard.func_96509_i(field_73839_d.field_71439_g.func_195047_I_());
        if (scoreplayerteam != null)
        {
            int slot = scoreplayerteam.func_178775_l().func_175746_b();
            if (slot >= 0) objective = scoreboard.func_96539_a(3 + slot);
        }
        ScoreObjective scoreobjective1 = objective != null ? objective : scoreboard.func_96539_a(1);
        if (renderObjective && scoreobjective1 != null)
        {
            this.func_238447_a_(mStack, scoreobjective1);
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        RenderSystem.disableAlphaTest();

        renderChat(this.field_194811_H, this.field_194812_I, mStack);

        renderPlayerList(this.field_194811_H, this.field_194812_I, mStack);

        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableAlphaTest();

        post(ALL, mStack);
    }

    @Override
    protected void func_238456_d_(MatrixStack mStack)
    {
        if (pre(CROSSHAIRS, mStack)) return;
        bind(AbstractGui.field_230665_h_);
        RenderSystem.enableBlend();
        RenderSystem.enableAlphaTest();
        super.func_238456_d_(mStack);
        post(CROSSHAIRS, mStack);
    }

    @Override
    protected void func_238444_a_(MatrixStack mStack)
    {
        if (pre(POTION_ICONS, mStack)) return;
        super.func_238444_a_(mStack);
        post(POTION_ICONS, mStack);
    }

    protected void renderSubtitles(MatrixStack mStack)
    {
        if (pre(SUBTITLES, mStack)) return;
        this.field_184049_t.func_195620_a(mStack);
        post(SUBTITLES, mStack);
    }

    protected void renderBossHealth(MatrixStack mStack)
    {
        if (pre(BOSSHEALTH, mStack)) return;
        bind(AbstractGui.field_230665_h_);
        RenderSystem.defaultBlendFunc();
        field_73839_d.func_213239_aq().func_76320_a("bossHealth");
        RenderSystem.enableBlend();
        this.field_184050_w.func_238484_a_(mStack);
        RenderSystem.disableBlend();
        field_73839_d.func_213239_aq().func_76319_b();
        post(BOSSHEALTH, mStack);
    }

    @Override
    protected void func_212303_b(Entity entity)
    {
        MatrixStack mStack = new MatrixStack();
        if (pre(VIGNETTE, mStack))
        {
            // Need to put this here, since Vanilla assumes this state after the vignette was rendered.
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            return;
        }
        super.func_212303_b(entity);
        post(VIGNETTE, mStack);
    }


    private void renderHelmet(float partialTicks, MatrixStack mStack)
    {
        if (pre(HELMET, mStack)) return;

        ItemStack itemstack = this.field_73839_d.field_71439_g.field_71071_by.func_70440_f(3);

        if (this.field_73839_d.field_71474_y.func_243230_g().func_243192_a() && !itemstack.func_190926_b())
        {
            Item item = itemstack.func_77973_b();
            if (item == Blocks.field_196625_cS.func_199767_j())
            {
                func_194808_p();
            }
            else
            {
                item.renderHelmetOverlay(itemstack, field_73839_d.field_71439_g, this.field_194811_H, this.field_194812_I, partialTicks);
            }
        }

        post(HELMET, mStack);
    }

    protected void renderArmor(MatrixStack mStack, int width, int height)
    {
        if (pre(ARMOR, mStack)) return;
        field_73839_d.func_213239_aq().func_76320_a("armor");

        RenderSystem.enableBlend();
        int left = width / 2 - 91;
        int top = height - left_height;

        int level = field_73839_d.field_71439_g.func_70658_aO();
        for (int i = 1; level > 0 && i < 20; i += 2)
        {
            if (i < level)
            {
                func_238474_b_(mStack, left, top, 34, 9, 9, 9);
            }
            else if (i == level)
            {
                func_238474_b_(mStack, left, top, 25, 9, 9, 9);
            }
            else if (i > level)
            {
                func_238474_b_(mStack, left, top, 16, 9, 9, 9);
            }
            left += 8;
        }
        left_height += 10;

        RenderSystem.disableBlend();
        field_73839_d.func_213239_aq().func_76319_b();
        post(ARMOR, mStack);
    }

    @Override
    protected void func_194805_e(float partialTicks)
    {
        MatrixStack mStack = new MatrixStack();
        if (pre(PORTAL, mStack)) return;

        float f1 = field_73839_d.field_71439_g.field_71080_cy + (field_73839_d.field_71439_g.field_71086_bY - field_73839_d.field_71439_g.field_71080_cy) * partialTicks;

        if (f1 > 0.0F)
        {
            super.func_194805_e(f1);
        }

        post(PORTAL, mStack);
    }

    @Override
    protected void func_238443_a_(float partialTicks, MatrixStack mStack)
    {
        if (pre(HOTBAR, mStack)) return;

        if (field_73839_d.field_71442_b.func_178889_l() == GameType.SPECTATOR)
        {
            this.field_175197_u.func_238528_a_(mStack, partialTicks);
        }
        else
        {
            super.func_238443_a_(partialTicks, mStack);
        }

        post(HOTBAR, mStack);
    }

    protected void renderAir(int width, int height, MatrixStack mStack)
    {
        if (pre(AIR, mStack)) return;
        field_73839_d.func_213239_aq().func_76320_a("air");
        PlayerEntity player = (PlayerEntity)this.field_73839_d.func_175606_aa();
        RenderSystem.enableBlend();
        int left = width / 2 + 91;
        int top = height - right_height;

        int air = player.func_70086_ai();
        if (player.func_208600_a(FluidTags.field_206959_a) || air < 300)
        {
            int full = MathHelper.func_76143_f((double)(air - 2) * 10.0D / 300.0D);
            int partial = MathHelper.func_76143_f((double)air * 10.0D / 300.0D) - full;

            for (int i = 0; i < full + partial; ++i)
            {
                func_238474_b_(mStack, left - i * 8 - 9, top, (i < full ? 16 : 25), 18, 9, 9);
            }
            right_height += 10;
        }

        RenderSystem.disableBlend();
        field_73839_d.func_213239_aq().func_76319_b();
        post(AIR, mStack);
    }

    public void renderHealth(int width, int height, MatrixStack mStack)
    {
        bind(field_230665_h_);
        if (pre(HEALTH, mStack)) return;
        field_73839_d.func_213239_aq().func_76320_a("health");
        RenderSystem.enableBlend();

        PlayerEntity player = (PlayerEntity)this.field_73839_d.func_175606_aa();
        int health = MathHelper.func_76123_f(player.func_110143_aJ());
        boolean highlight = field_175191_F > (long)field_73837_f && (field_175191_F - (long)field_73837_f) / 3L %2L == 1L;

        if (health < this.field_175194_C && player.field_70172_ad > 0)
        {
            this.field_175190_E = Util.func_211177_b();
            this.field_175191_F = (long)(this.field_73837_f + 20);
        }
        else if (health > this.field_175194_C && player.field_70172_ad > 0)
        {
            this.field_175190_E = Util.func_211177_b();
            this.field_175191_F = (long)(this.field_73837_f + 10);
        }

        if (Util.func_211177_b() - this.field_175190_E > 1000L)
        {
            this.field_175194_C = health;
            this.field_175189_D = health;
            this.field_175190_E = Util.func_211177_b();
        }

        this.field_175194_C = health;
        int healthLast = this.field_175189_D;

        ModifiableAttributeInstance attrMaxHealth = player.func_110148_a(Attributes.field_233818_a_);
        float healthMax = (float)attrMaxHealth.func_111126_e();
        float absorb = MathHelper.func_76123_f(player.func_110139_bj());

        int healthRows = MathHelper.func_76123_f((healthMax + absorb) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);

        this.field_73842_c.setSeed((long)(field_73837_f * 312871));

        int left = width / 2 - 91;
        int top = height - left_height;
        left_height += (healthRows * rowHeight);
        if (rowHeight != 10) left_height += 10 - rowHeight;

        int regen = -1;
        if (player.func_70644_a(Effects.field_76428_l))
        {
            regen = field_73837_f % 25;
        }

        final int TOP =  9 * (field_73839_d.field_71441_e.func_72912_H().func_76093_s() ? 5 : 0);
        final int BACKGROUND = (highlight ? 25 : 16);
        int MARGIN = 16;
        if (player.func_70644_a(Effects.field_76436_u))      MARGIN += 36;
        else if (player.func_70644_a(Effects.field_82731_v)) MARGIN += 72;
        float absorbRemaining = absorb;

        for (int i = MathHelper.func_76123_f((healthMax + absorb) / 2.0F) - 1; i >= 0; --i)
        {
            //int b0 = (highlight ? 1 : 0);
            int row = MathHelper.func_76123_f((float)(i + 1) / 10.0F) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;

            if (health <= 4) y += field_73842_c.nextInt(2);
            if (i == regen) y -= 2;

            func_238474_b_(mStack, x, y, BACKGROUND, TOP, 9, 9);

            if (highlight)
            {
                if (i * 2 + 1 < healthLast)
                    func_238474_b_(mStack, x, y, MARGIN + 54, TOP, 9, 9); //6
                else if (i * 2 + 1 == healthLast)
                    func_238474_b_(mStack, x, y, MARGIN + 63, TOP, 9, 9); //7
            }

            if (absorbRemaining > 0.0F)
            {
                if (absorbRemaining == absorb && absorb % 2.0F == 1.0F)
                {
                    func_238474_b_(mStack, x, y, MARGIN + 153, TOP, 9, 9); //17
                    absorbRemaining -= 1.0F;
                }
                else
                {
                    func_238474_b_(mStack, x, y, MARGIN + 144, TOP, 9, 9); //16
                    absorbRemaining -= 2.0F;
                }
            }
            else
            {
                if (i * 2 + 1 < health)
                    func_238474_b_(mStack, x, y, MARGIN + 36, TOP, 9, 9); //4
                else if (i * 2 + 1 == health)
                    func_238474_b_(mStack, x, y, MARGIN + 45, TOP, 9, 9); //5
            }
        }

        RenderSystem.disableBlend();
        field_73839_d.func_213239_aq().func_76319_b();
        post(HEALTH, mStack);
    }

    public void renderFood(int width, int height, MatrixStack mStack)
    {
        if (pre(FOOD, mStack)) return;
        field_73839_d.func_213239_aq().func_76320_a("food");

        PlayerEntity player = (PlayerEntity)this.field_73839_d.func_175606_aa();
        RenderSystem.enableBlend();
        int left = width / 2 + 91;
        int top = height - right_height;
        right_height += 10;
        boolean unused = false;// Unused flag in vanilla, seems to be part of a 'fade out' mechanic

        FoodStats stats = field_73839_d.field_71439_g.func_71024_bL();
        int level = stats.func_75116_a();

        for (int i = 0; i < 10; ++i)
        {
            int idx = i * 2 + 1;
            int x = left - i * 8 - 9;
            int y = top;
            int icon = 16;
            byte background = 0;

            if (field_73839_d.field_71439_g.func_70644_a(Effects.field_76438_s))
            {
                icon += 36;
                background = 13;
            }
            if (unused) background = 1; //Probably should be a += 1 but vanilla never uses this

            if (player.func_71024_bL().func_75115_e() <= 0.0F && field_73837_f % (level * 3 + 1) == 0)
            {
                y = top + (field_73842_c.nextInt(3) - 1);
            }

            func_238474_b_(mStack, x, y, 16 + background * 9, 27, 9, 9);

            if (idx < level)
                func_238474_b_(mStack, x, y, icon + 36, 27, 9, 9);
            else if (idx == level)
                func_238474_b_(mStack, x, y, icon + 45, 27, 9, 9);
        }
        RenderSystem.disableBlend();
        field_73839_d.func_213239_aq().func_76319_b();
        post(FOOD, mStack);
    }

    protected void renderSleepFade(int width, int height, MatrixStack mStack)
    {
        if (field_73839_d.field_71439_g.func_71060_bI() > 0)
        {
            field_73839_d.func_213239_aq().func_76320_a("sleep");
            RenderSystem.disableDepthTest();
            RenderSystem.disableAlphaTest();
            int sleepTime = field_73839_d.field_71439_g.func_71060_bI();
            float opacity = (float)sleepTime / 100.0F;

            if (opacity > 1.0F)
            {
                opacity = 1.0F - (float)(sleepTime - 100) / 10.0F;
            }

            int color = (int)(220.0F * opacity) << 24 | 1052704;
            func_238467_a_(mStack, 0, 0, width, height, color);
            RenderSystem.enableAlphaTest();
            RenderSystem.enableDepthTest();
            field_73839_d.func_213239_aq().func_76319_b();
        }
    }

    protected void renderExperience(int x, MatrixStack mStack)
    {
        bind(field_230665_h_);
        if (pre(EXPERIENCE, mStack)) return;
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (field_73839_d.field_71442_b.func_78763_f())
        {
            super.func_238454_b_(mStack, x);
        }
        RenderSystem.enableBlend();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        post(EXPERIENCE, mStack);
    }

    @Override
    public void func_238446_a_(MatrixStack mStack, int x)
    {
        bind(field_230665_h_);
        if (pre(JUMPBAR, mStack)) return;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        super.func_238446_a_(mStack, x);

        RenderSystem.enableBlend();
        field_73839_d.func_213239_aq().func_76319_b();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        post(JUMPBAR, mStack);
    }

    protected void renderHUDText(int width, int height, MatrixStack mStack)
    {
        field_73839_d.func_213239_aq().func_76320_a("forgeHudText");
        RenderSystem.defaultBlendFunc();
        ArrayList<String> listL = new ArrayList<String>();
        ArrayList<String> listR = new ArrayList<String>();

        if (field_73839_d.func_71355_q())
        {
            long time = field_73839_d.field_71441_e.func_82737_E();
            if (time >= 120500L)
            {
                listR.add(I18n.func_135052_a("demo.demoExpired"));
            }
            else
            {
                listR.add(I18n.func_135052_a("demo.remainingTime", StringUtils.func_76337_a((int)(120500L - time))));
            }
        }

        if (this.field_73839_d.field_71474_y.field_74330_P && !pre(DEBUG, mStack))
        {
            debugOverlay.update();
            listL.addAll(debugOverlay.getLeft());
            listR.addAll(debugOverlay.getRight());
            post(DEBUG, mStack);
        }

        RenderGameOverlayEvent.Text event = new RenderGameOverlayEvent.Text(mStack, eventParent, listL, listR);
        if (!MinecraftForge.EVENT_BUS.post(event))
        {
            int top = 2;
            for (String msg : listL)
            {
                if (msg == null) continue;
                func_238467_a_(mStack, 1, top - 1, 2 + fontrenderer.func_78256_a(msg) + 1, top + fontrenderer.field_78288_b - 1, -1873784752);
                fontrenderer.func_238421_b_(mStack, msg, 2, top, 14737632);
                top += fontrenderer.field_78288_b;
            }

            top = 2;
            for (String msg : listR)
            {
                if (msg == null) continue;
                int w = fontrenderer.func_78256_a(msg);
                int left = width - 2 - w;
                func_238467_a_(mStack, left - 1, top - 1, left + w + 1, top + fontrenderer.field_78288_b - 1, -1873784752);
                fontrenderer.func_238421_b_(mStack, msg, left, top, 14737632);
                top += fontrenderer.field_78288_b;
            }
        }

        field_73839_d.func_213239_aq().func_76319_b();
        post(TEXT, mStack);
    }

    protected void renderFPSGraph(MatrixStack mStack)
    {
        if (this.field_73839_d.field_71474_y.field_74330_P && this.field_73839_d.field_71474_y.field_181657_aC && !pre(FPS_GRAPH, mStack))
        {
            this.debugOverlay.func_194818_a(mStack);
            post(FPS_GRAPH, mStack);
        }
    }

    protected void renderRecordOverlay(int width, int height, float partialTicks, MatrixStack mStack)
    {
        if (field_73845_h > 0)
        {
            field_73839_d.func_213239_aq().func_76320_a("overlayMessage");
            float hue = (float)field_73845_h - partialTicks;
            int opacity = (int)(hue * 255.0F / 20.0F);
            if (opacity > 255) opacity = 255;

            if (opacity > 8)
            {
                RenderSystem.pushMatrix();
                RenderSystem.translatef((float)(width / 2), (float)(height - 68), 0.0F);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                int color = (field_73844_j ? MathHelper.func_181758_c(hue / 50.0F, 0.7F, 0.6F) & WHITE : WHITE);
                func_238448_a_(mStack, fontrenderer, -4, fontrenderer.func_238414_a_(field_73838_g), 16777215 | (opacity << 24));
                fontrenderer.func_238422_b_(mStack, field_73838_g.func_241878_f(), -fontrenderer.func_238414_a_(field_73838_g) / 2, -4, color | (opacity << 24));
                RenderSystem.disableBlend();
                RenderSystem.popMatrix();
            }

            field_73839_d.func_213239_aq().func_76319_b();
        }
    }

    protected void renderTitle(int width, int height, float partialTicks, MatrixStack mStack)
    {
        if (field_175201_x != null && field_175195_w > 0)
        {
            field_73839_d.func_213239_aq().func_76320_a("titleAndSubtitle");
            float age = (float)this.field_175195_w - partialTicks;
            int opacity = 255;

            if (field_175195_w > field_175193_B + field_175192_A)
            {
                float f3 = (float)(field_175199_z + field_175192_A + field_175193_B) - age;
                opacity = (int)(f3 * 255.0F / (float)field_175199_z);
            }
            if (field_175195_w <= field_175193_B) opacity = (int)(age * 255.0F / (float)this.field_175193_B);

            opacity = MathHelper.func_76125_a(opacity, 0, 255);

            if (opacity > 8)
            {
                RenderSystem.pushMatrix();
                RenderSystem.translatef((float)(width / 2), (float)(height / 2), 0.0F);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.pushMatrix();
                RenderSystem.scalef(4.0F, 4.0F, 4.0F);
                int l = opacity << 24 & -16777216;
                this.func_175179_f().func_238407_a_(mStack, this.field_175201_x.func_241878_f(), (float)(-this.func_175179_f().func_238414_a_(this.field_175201_x) / 2), -10.0F, 16777215 | l);
                RenderSystem.popMatrix();
                if (this.field_175200_y != null)
                {
                    RenderSystem.pushMatrix();
                    RenderSystem.scalef(2.0F, 2.0F, 2.0F);
                    this.func_175179_f().func_238407_a_(mStack, this.field_175200_y.func_241878_f(), (float)(-this.func_175179_f().func_238414_a_(this.field_175200_y) / 2), 5.0F, 16777215 | l);
                    RenderSystem.popMatrix();
                }
                RenderSystem.disableBlend();
                RenderSystem.popMatrix();
            }

            this.field_73839_d.func_213239_aq().func_76319_b();
        }
    }

    protected void renderChat(int width, int height, MatrixStack mStack)
    {
        field_73839_d.func_213239_aq().func_76320_a("chat");

        RenderGameOverlayEvent.Chat event = new RenderGameOverlayEvent.Chat(mStack, eventParent, 0, height - 48);
        if (MinecraftForge.EVENT_BUS.post(event)) return;

        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) event.getPosX(), (float) event.getPosY(), 0.0F);
        field_73840_e.func_238492_a_(mStack, field_73837_f);
        RenderSystem.popMatrix();

        post(CHAT, mStack);

        field_73839_d.func_213239_aq().func_76319_b();
    }

    protected void renderPlayerList(int width, int height, MatrixStack mStack)
    {
        ScoreObjective scoreobjective = this.field_73839_d.field_71441_e.func_96441_U().func_96539_a(0);
        ClientPlayNetHandler handler = field_73839_d.field_71439_g.field_71174_a;

        if (field_73839_d.field_71474_y.field_74321_H.func_151470_d() && (!field_73839_d.func_71387_A() || handler.func_175106_d().size() > 1 || scoreobjective != null))
        {
            this.field_175196_v.func_175246_a(true);
            if (pre(PLAYER_LIST, mStack)) return;
            this.field_175196_v.func_238523_a_(mStack, width, this.field_73839_d.field_71441_e.func_96441_U(), scoreobjective);
            post(PLAYER_LIST, mStack);
        }
        else
        {
            this.field_175196_v.func_175246_a(false);
        }
    }

    protected void renderHealthMount(int width, int height, MatrixStack mStack)
    {
        PlayerEntity player = (PlayerEntity)field_73839_d.func_175606_aa();
        Entity tmp = player.func_184187_bx();
        if (!(tmp instanceof LivingEntity)) return;

        bind(field_230665_h_);

        if (pre(HEALTHMOUNT, mStack)) return;

        boolean unused = false;
        int left_align = width / 2 + 91;

        field_73839_d.func_213239_aq().func_219895_b("mountHealth");
        RenderSystem.enableBlend();
        LivingEntity mount = (LivingEntity)tmp;
        int health = (int)Math.ceil((double)mount.func_110143_aJ());
        float healthMax = mount.func_110138_aP();
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
                func_238474_b_(mStack, x, top, BACKGROUND, 9, 9, 9);

                if (i * 2 + 1 + heart < health)
                    func_238474_b_(mStack, x, top, FULL, 9, 9, 9);
                else if (i * 2 + 1 + heart == health)
                    func_238474_b_(mStack, x, top, HALF, 9, 9, 9);
            }

            right_height += 10;
        }
        RenderSystem.disableBlend();
        post(HEALTHMOUNT, mStack);
    }

    //Helper macros
    private boolean pre(ElementType type, MatrixStack mStack)
    {
        return MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Pre(mStack, eventParent, type));
    }
    private void post(ElementType type, MatrixStack mStack)
    {
        MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(mStack, eventParent, type));
    }
    private void bind(ResourceLocation res)
    {
        field_73839_d.func_110434_K().func_110577_a(res);
    }

    private class GuiOverlayDebugForge extends DebugOverlayGui
    {
        private Minecraft mc;
        private GuiOverlayDebugForge(Minecraft mc)
        {
            super(mc);
            this.mc = mc;
        }
        public void update()
        {
            Entity entity = this.mc.func_175606_aa();
            this.field_211537_g = entity.func_213324_a(rayTraceDistance, 0.0F, false);
            this.field_211538_h = entity.func_213324_a(rayTraceDistance, 0.0F, true);
        }
        @Override protected void func_230024_c_(MatrixStack mStack){}
        @Override protected void func_230025_d_(MatrixStack mStack){}
        private List<String> getLeft()
        {
            List<String> ret = this.func_209011_c();
            ret.add("");
            ret.add("Debug: Pie [shift]: " + (this.mc.field_71474_y.field_74329_Q ? "visible" : "hidden") + " FPS [alt]: " + (this.mc.field_71474_y.field_181657_aC ? "visible" : "hidden"));
            ret.add("For help: press F3 + Q");
            return ret;
        }
        private List<String> getRight(){ return this.func_175238_c(); }
    }
}
