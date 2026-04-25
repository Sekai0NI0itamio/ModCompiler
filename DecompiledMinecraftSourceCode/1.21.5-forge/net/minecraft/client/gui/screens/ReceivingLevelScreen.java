package net.minecraft.client.gui.screens;

import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ReceivingLevelScreen extends Screen {
    private static final Component DOWNLOADING_TERRAIN_TEXT = Component.translatable("multiplayer.downloadingTerrain");
    private static final long CHUNK_LOADING_START_WAIT_LIMIT_MS = 30000L;
    private final long createdAt;
    private final BooleanSupplier levelReceived;
    private final ReceivingLevelScreen.Reason reason;
    @Nullable
    private TextureAtlasSprite cachedNetherPortalSprite;

    public ReceivingLevelScreen(BooleanSupplier p_310110_, ReceivingLevelScreen.Reason p_336020_) {
        super(GameNarrator.NO_TITLE);
        this.levelReceived = p_310110_;
        this.reason = p_336020_;
        this.createdAt = Util.getMillis();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void render(GuiGraphics p_281489_, int p_282902_, int p_283018_, float p_281251_) {
        super.render(p_281489_, p_282902_, p_283018_, p_281251_);
        p_281489_.drawCenteredString(this.font, DOWNLOADING_TERRAIN_TEXT, this.width / 2, this.height / 2 - 50, -1);
    }

    @Override
    public void renderBackground(GuiGraphics p_298240_, int p_297552_, int p_298125_, float p_297335_) {
        switch (this.reason) {
            case NETHER_PORTAL:
                p_298240_.blitSprite(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, this.getNetherPortalSprite(), 0, 0, p_298240_.guiWidth(), p_298240_.guiHeight());
                break;
            case END_PORTAL:
                TextureManager texturemanager = Minecraft.getInstance().getTextureManager();
                TextureSetup texturesetup = TextureSetup.doubleTexture(
                    texturemanager.getTexture(TheEndPortalRenderer.END_SKY_LOCATION).getTextureView(), texturemanager.getTexture(TheEndPortalRenderer.END_PORTAL_LOCATION).getTextureView()
                );
                p_298240_.fill(RenderPipelines.END_PORTAL, texturesetup, 0, 0, this.width, this.height);
                break;
            case OTHER:
                this.renderPanorama(p_298240_, p_297335_);
                this.renderBlurredBackground(p_298240_);
                this.renderMenuBackground(p_298240_);
        }
    }

    private TextureAtlasSprite getNetherPortalSprite() {
        if (this.cachedNetherPortalSprite != null) {
            return this.cachedNetherPortalSprite;
        } else {
            this.cachedNetherPortalSprite = this.minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
            return this.cachedNetherPortalSprite;
        }
    }

    @Override
    public void tick() {
        if (this.levelReceived.getAsBoolean() || Util.getMillis() > this.createdAt + 30000L) {
            this.onClose();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.getNarrator().saySystemNow(Component.translatable("narrator.ready_to_play"));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Reason {
        NETHER_PORTAL,
        END_PORTAL,
        OTHER;
    }
}
