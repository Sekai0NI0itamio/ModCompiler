package net.minecraft.client.renderer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.GuiBannerResultRenderer;
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiProfilerChartRenderer;
import net.minecraft.client.gui.render.pip.GuiSignRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class GameRenderer implements TrackedWaypoint.Projector, AutoCloseable {
    private static final ResourceLocation BLUR_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("blur");
    public static final int MAX_BLUR_RADIUS = 10;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final float PROJECTION_Z_NEAR = 0.05F;
    public static final float PROJECTION_3D_HUD_Z_FAR = 100.0F;
    private static final float PORTAL_SPINNING_SPEED = 20.0F;
    private static final float NAUSEA_SPINNING_SPEED = 7.0F;
    private final Minecraft minecraft;
    private final RandomSource random = RandomSource.create();
    private float renderDistance;
    public final ItemInHandRenderer itemInHandRenderer;
    private final ScreenEffectRenderer screenEffectRenderer;
    private final RenderBuffers renderBuffers;
    private float spinningEffectTime;
    private float spinningEffectSpeed;
    private float fovModifier;
    private float oldFovModifier;
    private float darkenWorldAmount;
    private float darkenWorldAmountO;
    private boolean renderBlockOutline = true;
    private long lastScreenshotAttempt;
    private boolean hasWorldScreenshot;
    private long lastActiveTime = Util.getMillis();
    private final LightTexture lightTexture;
    private final OverlayTexture overlayTexture = new OverlayTexture();
    private boolean panoramicMode;
    protected final CubeMap cubeMap = new CubeMap(ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama"));
    protected final PanoramaRenderer panorama = new PanoramaRenderer(this.cubeMap);
    private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
    private final FogRenderer fogRenderer = new FogRenderer();
    private final GuiRenderer guiRenderer;
    private final GuiRenderState guiRenderState;
    @Nullable
    private ResourceLocation postEffectId;
    private boolean effectActive;
    private final Camera mainCamera = new Camera();
    private final Lighting lighting = new Lighting();
    private final GlobalSettingsUniform globalSettingsUniform = new GlobalSettingsUniform();
    private final PerspectiveProjectionMatrixBuffer levelProjectionMatrixBuffer = new PerspectiveProjectionMatrixBuffer("level");
    private final CachedPerspectiveProjectionMatrixBuffer hud3dProjectionMatrixBuffer = new CachedPerspectiveProjectionMatrixBuffer("3d hud", 0.05F, 100.0F);

    public GameRenderer(Minecraft p_234219_, ItemInHandRenderer p_234220_, RenderBuffers p_234222_) {
        this.minecraft = p_234219_;
        this.itemInHandRenderer = p_234220_;
        this.lightTexture = new LightTexture(this, p_234219_);
        this.renderBuffers = p_234222_;
        this.guiRenderState = new GuiRenderState();
        MultiBufferSource.BufferSource multibuffersource$buffersource = p_234222_.bufferSource();
        this.guiRenderer = new GuiRenderer(
            this.guiRenderState,
            multibuffersource$buffersource,
            List.of(
                new GuiEntityRenderer(multibuffersource$buffersource, p_234219_.getEntityRenderDispatcher()),
                new GuiSkinRenderer(multibuffersource$buffersource),
                new GuiBookModelRenderer(multibuffersource$buffersource),
                new GuiBannerResultRenderer(multibuffersource$buffersource),
                new GuiSignRenderer(multibuffersource$buffersource),
                new GuiProfilerChartRenderer(multibuffersource$buffersource)
            )
        );
        this.screenEffectRenderer = new ScreenEffectRenderer(p_234219_, multibuffersource$buffersource);
    }

    @Override
    public void close() {
        this.globalSettingsUniform.close();
        this.lightTexture.close();
        this.overlayTexture.close();
        this.resourcePool.close();
        this.guiRenderer.close();
        this.levelProjectionMatrixBuffer.close();
        this.hud3dProjectionMatrixBuffer.close();
        this.lighting.close();
        this.cubeMap.close();
        this.fogRenderer.close();
    }

    public void setRenderBlockOutline(boolean p_172776_) {
        this.renderBlockOutline = p_172776_;
    }

    public void setPanoramicMode(boolean p_172780_) {
        this.panoramicMode = p_172780_;
    }

    public boolean isPanoramicMode() {
        return this.panoramicMode;
    }

    public void clearPostEffect() {
        this.postEffectId = null;
    }

    public void togglePostEffect() {
        this.effectActive = !this.effectActive;
    }

    public void checkEntityPostEffect(@Nullable Entity p_109107_) {
        this.postEffectId = null;
        if (p_109107_ instanceof Creeper) {
            this.setPostEffect(ResourceLocation.withDefaultNamespace("creeper"));
        } else if (p_109107_ instanceof Spider) {
            this.setPostEffect(ResourceLocation.withDefaultNamespace("spider"));
        } else if (p_109107_ instanceof EnderMan) {
            this.setPostEffect(ResourceLocation.withDefaultNamespace("invert"));
        } else if (p_109107_ != null) {
            var rl = net.minecraftforge.client.EntitySpectatorShaderManager.get(p_109107_.getType());
            if (rl != null)
                this.setPostEffect(rl);
        }
    }

    private void setPostEffect(ResourceLocation p_367260_) {
        this.postEffectId = p_367260_;
        this.effectActive = true;
    }

    public void processBlurEffect() {
        PostChain postchain = this.minecraft.getShaderManager().getPostChain(BLUR_POST_CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
        if (postchain != null) {
            // TODO: [Forge][Rendering] Check if this blend management is needed anymnore with the new render pipeline changes
            boolean wasBlendEnabled = com.mojang.blaze3d.opengl.GlStateManager._isBlendEnabled();
            if (wasBlendEnabled)
                com.mojang.blaze3d.opengl.GlStateManager._disableBlend();
            postchain.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
            if (wasBlendEnabled)
                com.mojang.blaze3d.opengl.GlStateManager._enableBlend();
        }
    }

    public void preloadUiShader(ResourceProvider p_172723_) {
        GpuDevice gpudevice = RenderSystem.getDevice();
        BiFunction<ResourceLocation, ShaderType, String> bifunction = (p_389360_, p_389361_) -> {
            ResourceLocation resourcelocation = p_389361_.idConverter().idToFile(p_389360_);

            try {
                String s;
                try (Reader reader = p_172723_.getResourceOrThrow(resourcelocation).openAsReader()) {
                    s = IOUtils.toString(reader);
                }

                return s;
            } catch (IOException ioexception) {
                LOGGER.error("Coudln't preload {} shader {}: {}", p_389361_, p_389360_, ioexception);
                return null;
            }
        };
        gpudevice.precompilePipeline(RenderPipelines.GUI, bifunction);
        gpudevice.precompilePipeline(RenderPipelines.GUI_TEXTURED, bifunction);
        if (TracyClient.isAvailable()) {
            gpudevice.precompilePipeline(RenderPipelines.TRACY_BLIT, bifunction);
        }
    }

    public void tick() {
        this.tickFov();
        this.lightTexture.tick();
        LocalPlayer localplayer = this.minecraft.player;
        if (this.minecraft.getCameraEntity() == null) {
            this.minecraft.setCameraEntity(localplayer);
        }

        this.mainCamera.tick();
        this.itemInHandRenderer.tick();
        float f = localplayer.portalEffectIntensity;
        float f1 = localplayer.getEffectBlendFactor(MobEffects.NAUSEA, 1.0F);
        if (!(f > 0.0F) && !(f1 > 0.0F)) {
            this.spinningEffectSpeed = 0.0F;
        } else {
            this.spinningEffectSpeed = (f * 20.0F + f1 * 7.0F) / (f + f1);
            this.spinningEffectTime = this.spinningEffectTime + this.spinningEffectSpeed;
        }

        if (this.minecraft.level.tickRateManager().runsNormally()) {
            this.minecraft.levelRenderer.tickParticles(this.mainCamera);
            this.darkenWorldAmountO = this.darkenWorldAmount;
            if (this.minecraft.gui.getBossOverlay().shouldDarkenScreen()) {
                this.darkenWorldAmount += 0.05F;
                if (this.darkenWorldAmount > 1.0F) {
                    this.darkenWorldAmount = 1.0F;
                }
            } else if (this.darkenWorldAmount > 0.0F) {
                this.darkenWorldAmount -= 0.0125F;
            }

            this.screenEffectRenderer.tick();
        }
    }

    @Nullable
    public ResourceLocation currentPostEffect() {
        return this.postEffectId;
    }

    public void resize(int p_109098_, int p_109099_) {
        this.resourcePool.clear();
        this.minecraft.levelRenderer.resize(p_109098_, p_109099_);
    }

    public void pick(float p_109088_) {
        Entity entity = this.minecraft.getCameraEntity();
        if (entity != null) {
            if (this.minecraft.level != null && this.minecraft.player != null) {
                Profiler.get().push("pick");
                double d0 = this.minecraft.player.blockInteractionRange();
                double d1 = this.minecraft.player.entityInteractionRange();
                HitResult hitresult = this.pick(entity, d0, d1, p_109088_);
                this.minecraft.hitResult = hitresult;
                this.minecraft.crosshairPickEntity = hitresult instanceof EntityHitResult entityhitresult ? entityhitresult.getEntity() : null;
                Profiler.get().pop();
            }
        }
    }

    private HitResult pick(Entity p_328026_, double p_328198_, double p_332188_, float p_336363_) {
        double d0 = Math.max(p_328198_, p_332188_);
        double d1 = Mth.square(d0);
        Vec3 vec3 = p_328026_.getEyePosition(p_336363_);
        HitResult hitresult = p_328026_.pick(d0, p_336363_, false);
        double d2 = hitresult.getLocation().distanceToSqr(vec3);
        if (hitresult.getType() != HitResult.Type.MISS) {
            d1 = d2;
            d0 = Math.sqrt(d2);
        }

        Vec3 vec31 = p_328026_.getViewVector(p_336363_);
        Vec3 vec32 = vec3.add(vec31.x * d0, vec31.y * d0, vec31.z * d0);
        float f = 1.0F;
        AABB aabb = p_328026_.getBoundingBox().expandTowards(vec31.scale(d0)).inflate(1.0, 1.0, 1.0);
        EntityHitResult entityhitresult = ProjectileUtil.getEntityHitResult(p_328026_, vec3, vec32, aabb, EntitySelector.CAN_BE_PICKED, d1);
        return entityhitresult != null && entityhitresult.getLocation().distanceToSqr(vec3) < d2
            ? filterHitResult(entityhitresult, vec3, p_332188_)
            : filterHitResult(hitresult, vec3, p_328198_);
    }

    private static HitResult filterHitResult(HitResult p_333216_, Vec3 p_336005_, double p_334240_) {
        Vec3 vec3 = p_333216_.getLocation();
        if (!vec3.closerThan(p_336005_, p_334240_)) {
            Vec3 vec31 = p_333216_.getLocation();
            Direction direction = Direction.getApproximateNearest(
                vec31.x - p_336005_.x, vec31.y - p_336005_.y, vec31.z - p_336005_.z
            );
            return BlockHitResult.miss(vec31, direction, BlockPos.containing(vec31));
        } else {
            return p_333216_;
        }
    }

    private void tickFov() {
        float f;
        if (this.minecraft.getCameraEntity() instanceof AbstractClientPlayer abstractclientplayer) {
            Options options = this.minecraft.options;
            boolean flag = options.getCameraType().isFirstPerson();
            float f1 = options.fovEffectScale().get().floatValue();
            f = abstractclientplayer.getFieldOfViewModifier(flag, f1);
        } else {
            f = 1.0F;
        }

        this.oldFovModifier = this.fovModifier;
        this.fovModifier = this.fovModifier + (f - this.fovModifier) * 0.5F;
        this.fovModifier = Mth.clamp(this.fovModifier, 0.1F, 1.5F);
    }

    private float getFov(Camera p_109142_, float p_109143_, boolean p_109144_) {
        if (this.panoramicMode) {
            return 90.0F;
        } else {
            float f = 70.0F;
            if (p_109144_) {
                f = this.minecraft.options.fov().get().intValue();
                f *= Mth.lerp(p_109143_, this.oldFovModifier, this.fovModifier);
            }

            if (p_109142_.getEntity() instanceof LivingEntity livingentity && livingentity.isDeadOrDying()) {
                float f1 = Math.min(livingentity.deathTime + p_109143_, 20.0F);
                f /= (1.0F - 500.0F / (f1 + 500.0F)) * 2.0F + 1.0F;
            }

            FogType fogtype = p_109142_.getFluidInCamera();
            if (fogtype == FogType.LAVA || fogtype == FogType.WATER) {
                float f2 = this.minecraft.options.fovEffectScale().get().floatValue();
                f *= Mth.lerp(f2, 1.0F, 0.85714287F);
            }

            return net.minecraftforge.client.event.ForgeEventFactoryClient.fireComputeFov(this, p_109142_, p_109143_, f, p_109144_).getFOV();
        }
    }

    private void bobHurt(PoseStack p_109118_, float p_109119_) {
        if (this.minecraft.getCameraEntity() instanceof LivingEntity livingentity) {
            float f2 = livingentity.hurtTime - p_109119_;
            if (livingentity.isDeadOrDying()) {
                float f = Math.min(livingentity.deathTime + p_109119_, 20.0F);
                p_109118_.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (f + 200.0F)));
            }

            if (f2 < 0.0F) {
                return;
            }

            f2 /= livingentity.hurtDuration;
            f2 = Mth.sin(f2 * f2 * f2 * f2 * (float) Math.PI);
            float f3 = livingentity.getHurtDir();
            p_109118_.mulPose(Axis.YP.rotationDegrees(-f3));
            float f1 = (float)(-f2 * 14.0 * this.minecraft.options.damageTiltStrength().get());
            p_109118_.mulPose(Axis.ZP.rotationDegrees(f1));
            p_109118_.mulPose(Axis.YP.rotationDegrees(f3));
        }
    }

    private void bobView(PoseStack p_109139_, float p_109140_) {
        if (this.minecraft.getCameraEntity() instanceof AbstractClientPlayer abstractclientplayer) {
            float f2 = abstractclientplayer.walkDist - abstractclientplayer.walkDistO;
            float $$5 = -(abstractclientplayer.walkDist + f2 * p_109140_);
            float $$6 = Mth.lerp(p_109140_, abstractclientplayer.oBob, abstractclientplayer.bob);
            p_109139_.translate(Mth.sin($$5 * (float) Math.PI) * $$6 * 0.5F, -Math.abs(Mth.cos($$5 * (float) Math.PI) * $$6), 0.0F);
            p_109139_.mulPose(Axis.ZP.rotationDegrees(Mth.sin($$5 * (float) Math.PI) * $$6 * 3.0F));
            p_109139_.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos($$5 * (float) Math.PI - 0.2F) * $$6) * 5.0F));
        }
    }

    private void renderItemInHand(float p_109123_, boolean p_408020_, Matrix4f p_331664_) {
        if (!this.panoramicMode) {
            PoseStack posestack = new PoseStack();
            posestack.pushPose();
            posestack.mulPose(p_331664_.invert(new Matrix4f()));
            Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
            matrix4fstack.pushMatrix().mul(p_331664_);
            this.bobHurt(posestack, p_109123_);
            if (this.minecraft.options.bobView().get()) {
                this.bobView(posestack, p_109123_);
            }

            if (this.minecraft.options.getCameraType().isFirstPerson()
                && !p_408020_
                && !this.minecraft.options.hideGui
                && this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
                this.lightTexture.turnOnLightLayer();
                this.itemInHandRenderer
                    .renderHandsWithItems(
                        p_109123_,
                        posestack,
                        this.renderBuffers.bufferSource(),
                        this.minecraft.player,
                        this.minecraft.getEntityRenderDispatcher().getPackedLightCoords(this.minecraft.player, p_109123_)
                    );
                this.lightTexture.turnOffLightLayer();
            }

            matrix4fstack.popMatrix();
            posestack.popPose();
        }
    }

    public Matrix4f getProjectionMatrix(float p_364788_) {
        Matrix4f matrix4f = new Matrix4f();
        return matrix4f.perspective(
            p_364788_ * (float) (Math.PI / 180.0), (float)this.minecraft.getWindow().getWidth() / this.minecraft.getWindow().getHeight(), 0.05F, this.getDepthFar()
        );
    }

    public float getDepthFar() {
        return Math.max(this.renderDistance * 4.0F, (float)(this.minecraft.options.cloudRange().get() * 16));
    }

    public static float getNightVisionScale(LivingEntity p_109109_, float p_109110_) {
        MobEffectInstance mobeffectinstance = p_109109_.getEffect(MobEffects.NIGHT_VISION);
        return !mobeffectinstance.endsWithin(200) ? 1.0F : 0.7F + Mth.sin((mobeffectinstance.getDuration() - p_109110_) * (float) Math.PI * 0.2F) * 0.3F;
    }

    public void render(DeltaTracker p_343467_, boolean p_109096_) {
        if (!this.minecraft.isWindowActive()
            && this.minecraft.options.pauseOnLostFocus
            && (!this.minecraft.options.touchscreen().get() || !this.minecraft.mouseHandler.isRightPressed())) {
            if (Util.getMillis() - this.lastActiveTime > 500L) {
                this.minecraft.pauseGame(false);
            }
        } else {
            this.lastActiveTime = Util.getMillis();
        }

        if (!this.minecraft.noRender) {
            this.globalSettingsUniform
                .update(
                    this.minecraft.getWindow().getWidth(),
                    this.minecraft.getWindow().getHeight(),
                    this.minecraft.options.glintStrength().get(),
                    this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime(),
                    p_343467_,
                    this.minecraft.options.getMenuBackgroundBlurriness()
                );
            ProfilerFiller profilerfiller = Profiler.get();
            boolean flag = this.minecraft.isGameLoadFinished();
            int i = (int)this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
            int j = (int)this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
            if (flag && p_109096_ && this.minecraft.level != null) {
                profilerfiller.push("world");
                this.renderLevel(p_343467_);
                this.tryTakeScreenshotIfNeeded();
                this.minecraft.levelRenderer.doEntityOutline();
                if (this.postEffectId != null && this.effectActive) {
                    RenderSystem.resetTextureMatrix();
                    PostChain postchain = this.minecraft.getShaderManager().getPostChain(this.postEffectId, LevelTargetBundle.MAIN_TARGETS);
                    if (postchain != null) {
                        postchain.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
                    }
                }
            }

            this.fogRenderer.endFrame();
            RenderTarget rendertarget = this.minecraft.getMainRenderTarget();
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(rendertarget.getDepthTexture(), 1.0);
            this.minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            this.guiRenderState.reset();
            GuiGraphics guigraphics = new GuiGraphics(this.minecraft, this.guiRenderState);
            if (flag && p_109096_ && this.minecraft.level != null) {
                profilerfiller.popPush("gui");
                this.minecraft.gui.render(guigraphics, p_343467_);
                profilerfiller.pop();
            }

            if (this.minecraft.getOverlay() != null) {
                try {
                    this.minecraft.getOverlay().render(guigraphics, i, j, p_343467_.getGameTimeDeltaTicks());
                } catch (Throwable throwable2) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable2, "Rendering overlay");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Overlay render details");
                    crashreportcategory.setDetail("Overlay name", () -> this.minecraft.getOverlay().getClass().getCanonicalName());
                    throw new ReportedException(crashreport);
                }
            } else if (flag && this.minecraft.screen != null) {
                try {
                    net.minecraftforge.client.ForgeHooksClient.drawScreen(this.minecraft.screen, guigraphics, i, j, p_343467_.getRealtimeDeltaTicks());
                } catch (Throwable throwable1) {
                    CrashReport crashreport1 = CrashReport.forThrowable(throwable1, "Rendering screen");
                    CrashReportCategory crashreportcategory1 = crashreport1.addCategory("Screen render details");
                    crashreportcategory1.setDetail("Screen name", () -> this.minecraft.screen.getClass().getCanonicalName());
                    this.minecraft.mouseHandler.fillMousePositionDetails(crashreportcategory1, this.minecraft.getWindow());
                    throw new ReportedException(crashreport1);
                }

                try {
                    if (this.minecraft.screen != null) {
                        this.minecraft.screen.handleDelayedNarration();
                    }
                } catch (Throwable throwable) {
                    CrashReport crashreport2 = CrashReport.forThrowable(throwable, "Narrating screen");
                    CrashReportCategory crashreportcategory2 = crashreport2.addCategory("Screen details");
                    crashreportcategory2.setDetail("Screen name", () -> this.minecraft.screen.getClass().getCanonicalName());
                    throw new ReportedException(crashreport2);
                }
            }

            if (flag && p_109096_ && this.minecraft.level != null) {
                this.minecraft.gui.renderSavingIndicator(guigraphics, p_343467_);
            }

            if (flag) {
                try (Zone zone = profilerfiller.zone("toasts")) {
                    this.minecraft.getToastManager().render(guigraphics);
                }
            }

            this.guiRenderer.render(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
            this.guiRenderer.incrementFrameNumber();
            this.resourcePool.endFrame();
        }
    }

    private void tryTakeScreenshotIfNeeded() {
        if (!this.hasWorldScreenshot && this.minecraft.isLocalServer()) {
            long i = Util.getMillis();
            if (i - this.lastScreenshotAttempt >= 1000L) {
                this.lastScreenshotAttempt = i;
                IntegratedServer integratedserver = this.minecraft.getSingleplayerServer();
                if (integratedserver != null && !integratedserver.isStopped()) {
                    integratedserver.getWorldScreenshotFile().ifPresent(p_234239_ -> {
                        if (Files.isRegularFile(p_234239_)) {
                            this.hasWorldScreenshot = true;
                        } else {
                            this.takeAutoScreenshot(p_234239_);
                        }
                    });
                }
            }
        }
    }

    private void takeAutoScreenshot(Path p_182643_) {
        if (this.minecraft.levelRenderer.countRenderedSections() > 10 && this.minecraft.levelRenderer.hasRenderedAllSections()) {
            Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget(), p_389363_ -> Util.ioPool().execute(() -> {
                int i = p_389363_.getWidth();
                int j = p_389363_.getHeight();
                int k = 0;
                int l = 0;
                if (i > j) {
                    k = (i - j) / 2;
                    i = j;
                } else {
                    l = (j - i) / 2;
                    j = i;
                }

                try (NativeImage nativeimage = new NativeImage(64, 64, false)) {
                    p_389363_.resizeSubRectTo(k, l, i, j, nativeimage);
                    nativeimage.writeToFile(p_182643_);
                } catch (IOException ioexception) {
                    LOGGER.warn("Couldn't save auto screenshot", (Throwable)ioexception);
                } finally {
                    p_389363_.close();
                }
            }));
        }
    }

    private boolean shouldRenderBlockOutline() {
        if (!this.renderBlockOutline) {
            return false;
        } else {
            Entity entity = this.minecraft.getCameraEntity();
            boolean flag = entity instanceof Player && !this.minecraft.options.hideGui;
            if (flag && !((Player)entity).getAbilities().mayBuild) {
                ItemStack itemstack = ((LivingEntity)entity).getMainHandItem();
                HitResult hitresult = this.minecraft.hitResult;
                if (hitresult != null && hitresult.getType() == HitResult.Type.BLOCK) {
                    BlockPos blockpos = ((BlockHitResult)hitresult).getBlockPos();
                    BlockState blockstate = this.minecraft.level.getBlockState(blockpos);
                    if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
                        flag = blockstate.getMenuProvider(this.minecraft.level, blockpos) != null;
                    } else {
                        BlockInWorld blockinworld = new BlockInWorld(this.minecraft.level, blockpos, false);
                        Registry<Block> registry = this.minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
                        flag = !itemstack.isEmpty() && (itemstack.canBreakBlockInAdventureMode(blockinworld) || itemstack.canPlaceOnBlockInAdventureMode(blockinworld));
                    }
                }
            }

            return flag;
        }
    }

    public void renderLevel(DeltaTracker p_342230_) {
        float f = p_342230_.getGameTimeDeltaPartialTick(true);
        LocalPlayer localplayer = this.minecraft.player;
        this.lightTexture.updateLightTexture(f);
        if (this.minecraft.getCameraEntity() == null) {
            this.minecraft.setCameraEntity(localplayer);
        }

        this.pick(f);
        ProfilerFiller profilerfiller = Profiler.get();
        profilerfiller.push("center");
        boolean flag = this.shouldRenderBlockOutline();
        profilerfiller.popPush("camera");
        Camera camera = this.mainCamera;
        Entity entity = (Entity)(this.minecraft.getCameraEntity() == null ? localplayer : this.minecraft.getCameraEntity());
        float f1 = this.minecraft.level.tickRateManager().isEntityFrozen(entity) ? 1.0F : f;
        camera.setup(this.minecraft.level, entity, !this.minecraft.options.getCameraType().isFirstPerson(), this.minecraft.options.getCameraType().isMirrored(), f1);
        this.renderDistance = this.minecraft.options.getEffectiveRenderDistance() * 16;
        float f2 = this.getFov(camera, f, true);
        Matrix4f matrix4f = this.getProjectionMatrix(f2);
        PoseStack posestack = new PoseStack();
        this.bobHurt(posestack, camera.getPartialTickTime());
        if (this.minecraft.options.bobView().get()) {
            this.bobView(posestack, camera.getPartialTickTime());
        }

        var cameraSetup = net.minecraftforge.client.event.ForgeEventFactoryClient.fireComputeCameraAngles(this, camera, f);
        camera.setRotation(cameraSetup.getYaw(), cameraSetup.getPitch(), cameraSetup.getRoll());

        matrix4f.mul(posestack.last().pose());
        float f3 = this.minecraft.options.screenEffectScale().get().floatValue();
        float f4 = Mth.lerp(f, localplayer.oPortalEffectIntensity, localplayer.portalEffectIntensity);
        float f5 = localplayer.getEffectBlendFactor(MobEffects.NAUSEA, f);
        float f6 = Math.max(f4, f5) * (f3 * f3);
        if (f6 > 0.0F) {
            float f7 = 5.0F / (f6 * f6 + 5.0F) - f6 * 0.04F;
            f7 *= f7;
            Vector3f vector3f = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            float f8 = (this.spinningEffectTime + f * this.spinningEffectSpeed) * (float) (Math.PI / 180.0);
            matrix4f.rotate(f8, vector3f);
            matrix4f.scale(1.0F / f7, 1.0F, 1.0F);
            matrix4f.rotate(-f8, vector3f);
        }

        float f9 = Math.max(f2, (float)this.minecraft.options.fov().get().intValue());
        Matrix4f matrix4f2 = this.getProjectionMatrix(f9);
        RenderSystem.setProjectionMatrix(this.levelProjectionMatrixBuffer.getBuffer(matrix4f), ProjectionType.PERSPECTIVE);
        Quaternionf quaternionf = camera.rotation().conjugate(new Quaternionf());
        Matrix4f matrix4f1 = new Matrix4f().rotation(quaternionf);
        this.minecraft.levelRenderer.prepareCullFrustum(camera.getPosition(), matrix4f1, matrix4f2);
        profilerfiller.popPush("fog");
        boolean flag1 = this.minecraft.level.effects().isFoggyAt(camera.getBlockPosition().getX(), camera.getBlockPosition().getZ())
            || this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
        Vector4f vector4f = this.fogRenderer.setupFog(camera, this.minecraft.options.getEffectiveRenderDistance(), flag1, p_342230_, this.getDarkenWorldAmount(f), this.minecraft.level);
        GpuBufferSlice gpubufferslice = this.fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);
        profilerfiller.popPush("level");
        this.minecraft.levelRenderer.renderLevel(this.resourcePool, p_342230_, flag, camera, matrix4f1, matrix4f, gpubufferslice, vector4f, !flag1);
        profilerfiller.popPush("hand");
        boolean flag2 = this.minecraft.getCameraEntity() instanceof LivingEntity && ((LivingEntity)this.minecraft.getCameraEntity()).isSleeping();
        RenderSystem.setProjectionMatrix(
            this.hud3dProjectionMatrixBuffer.getBuffer(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(), this.getFov(camera, f, false)),
            ProjectionType.PERSPECTIVE
        );
        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(this.minecraft.getMainRenderTarget().getDepthTexture(), 1.0);
        this.renderItemInHand(f, flag2, matrix4f1);
        profilerfiller.popPush("screen effects");
        MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();
        this.screenEffectRenderer.renderScreenEffect(flag2, f);
        multibuffersource$buffersource.endBatch();
        profilerfiller.pop();
        RenderSystem.setShaderFog(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
        if (this.minecraft.gui.shouldRenderDebugCrosshair()) {
            this.minecraft.getDebugOverlay().render3dCrosshair(camera);
        }
    }

    public void resetData() {
        this.screenEffectRenderer.resetItemActivation();
        this.minecraft.getMapTextureManager().resetData();
        this.mainCamera.reset();
        this.hasWorldScreenshot = false;
    }

    public void displayItemActivation(ItemStack p_109114_) {
        this.screenEffectRenderer.displayItemActivation(p_109114_, this.random);
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    public float getDarkenWorldAmount(float p_109132_) {
        return Mth.lerp(p_109132_, this.darkenWorldAmountO, this.darkenWorldAmount);
    }

    public float getRenderDistance() {
        return this.renderDistance;
    }

    public Camera getMainCamera() {
        return this.mainCamera;
    }

    public LightTexture lightTexture() {
        return this.lightTexture;
    }

    public OverlayTexture overlayTexture() {
        return this.overlayTexture;
    }

    @Override
    public Vec3 projectPointToScreen(Vec3 p_407933_) {
        Matrix4f matrix4f = this.getProjectionMatrix(this.getFov(this.mainCamera, 0.0F, true));
        Quaternionf quaternionf = this.mainCamera.rotation().conjugate(new Quaternionf());
        Matrix4f matrix4f1 = new Matrix4f().rotation(quaternionf);
        Matrix4f matrix4f2 = matrix4f.mul(matrix4f1);
        Vec3 vec3 = this.mainCamera.getPosition();
        Vec3 vec31 = p_407933_.subtract(vec3);
        Vector3f vector3f = matrix4f2.transformProject(vec31.toVector3f());
        return new Vec3(vector3f);
    }

    @Override
    public double projectHorizonToScreen() {
        float f = this.mainCamera.getXRot();
        if (f <= -90.0F) {
            return Double.NEGATIVE_INFINITY;
        } else if (f >= 90.0F) {
            return Double.POSITIVE_INFINITY;
        } else {
            float f1 = this.getFov(this.mainCamera, 0.0F, true);
            return Math.tan(f * (float) (Math.PI / 180.0)) / Math.tan(f1 / 2.0F * (float) (Math.PI / 180.0));
        }
    }

    public GlobalSettingsUniform getGlobalSettingsUniform() {
        return this.globalSettingsUniform;
    }

    public Lighting getLighting() {
        return this.lighting;
    }

    public void setLevel(@Nullable ClientLevel p_405879_) {
        if (p_405879_ != null) {
            this.lighting.updateLevel(p_405879_.effects().constantAmbientLight());
        }
    }

    public PanoramaRenderer getPanorama() {
        return this.panorama;
    }
}
