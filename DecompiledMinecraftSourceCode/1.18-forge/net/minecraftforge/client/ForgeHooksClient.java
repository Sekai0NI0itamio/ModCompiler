/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client;

import com.google.common.collect.ImmutableMap;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.WorldStem;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.worldselection.WorldPreset;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.renderer.texture.TextureAtlas;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Transformation;
import com.mojang.math.Vector3f;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.model.ForgeModelBakery;
import net.minecraftforge.client.textures.ForgeTextureMetadata;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.model.TransformationHelper;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.ForgeI18n;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.BOSSINFO;
import static net.minecraftforge.fml.VersionChecker.Status.BETA;
import static net.minecraftforge.fml.VersionChecker.Status.BETA_OUTDATED;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;

public class ForgeHooksClient
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker CLIENTHOOKS = MarkerManager.getMarker("CLIENTHOOKS");

    //private static final ResourceLocation ITEM_GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

    /**
     * Contains the *extra* GUI layers.
     * The current top layer stays in Minecraft#currentScreen, and the rest serve as a background for it.
     */
    private static final Stack<Screen> guiLayers = new Stack<>();

    public static void resizeGuiLayers(Minecraft minecraft, int width, int height) {
        guiLayers.forEach(screen -> screen.m_6574_(minecraft, width, height));
    }

    public static void clearGuiLayers(Minecraft minecraft)
    {
        while(guiLayers.size() > 0)
            popGuiLayerInternal(minecraft);
    }

    private static void popGuiLayerInternal(Minecraft minecraft)
    {
        if (minecraft.f_91080_ != null)
            minecraft.f_91080_.m_7861_();
        minecraft.f_91080_ = guiLayers.pop();
    }

    public static void pushGuiLayer(Minecraft minecraft, Screen screen)
    {
        if (minecraft.f_91080_ != null)
            guiLayers.push(minecraft.f_91080_);
        minecraft.f_91080_ = Objects.requireNonNull(screen);
        screen.m_6575_(minecraft, minecraft.m_91268_().m_85445_(), minecraft.m_91268_().m_85446_());
        NarratorChatListener.f_93311_.m_168785_(screen.m_142562_());
    }

    public static void popGuiLayer(Minecraft minecraft)
    {
        if (guiLayers.size() == 0)
        {
            minecraft.m_91152_(null);
            return;
        }

        popGuiLayerInternal(minecraft);
        if (minecraft.f_91080_ != null)
            NarratorChatListener.f_93311_.m_168785_(minecraft.f_91080_.m_142562_());
    }

    public static float getGuiFarPlane()
    {
        // 1000 units for the overlay background,
        // and 2000 units for each layered Screen,

        return 1000.0F + 2000.0F * (1 + guiLayers.size());
    }

    public static String getArmorTexture(Entity entity, ItemStack armor, String _default, EquipmentSlot slot, String type)
    {
        String result = armor.m_41720_().getArmorTexture(armor, entity, slot, type);
        return result != null ? result : _default;
    }

    public static boolean onDrawHighlight(LevelRenderer context, Camera camera, HitResult target, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource)
    {
        switch (target.m_6662_()) {
            case BLOCK:
                if (!(target instanceof BlockHitResult)) return false;
                return MinecraftForge.EVENT_BUS.post(new DrawSelectionEvent.HighlightBlock(context, camera, target, partialTick, poseStack, bufferSource));
            case ENTITY:
                if (!(target instanceof EntityHitResult)) return false;
                return MinecraftForge.EVENT_BUS.post(new DrawSelectionEvent.HighlightEntity(context, camera, target, partialTick, poseStack, bufferSource));
            default:
                return MinecraftForge.EVENT_BUS.post(new DrawSelectionEvent(context, camera, target, partialTick, poseStack, bufferSource));
        }
    }

    @Deprecated(forRemoval = true, since = "1.18.2")
    public static void dispatchRenderLast(LevelRenderer context, PoseStack poseStack, float partialTick, Matrix4f projectionMatrix, long finishTimeNano)
    {
        MinecraftForge.EVENT_BUS.post(new RenderLevelLastEvent(context, poseStack, partialTick, projectionMatrix, finishTimeNano));
    }

    public static void dispatchRenderStage(RenderLevelStageEvent.Stage stage, LevelRenderer levelRenderer, PoseStack poseStack, Matrix4f projectionMatrix, int renderTick, Camera camera, Frustum frustum)
    {
        var profiler = Minecraft.m_91087_().m_91307_();
        profiler.m_6180_(stage.toString());
        MinecraftForge.EVENT_BUS.post(new RenderLevelStageEvent(stage, levelRenderer, poseStack, projectionMatrix, renderTick, MinecraftForgeClient.getPartialTick(), camera, frustum));
        profiler.m_7238_();
    }

    public static void dispatchRenderStage(RenderType renderType, LevelRenderer levelRenderer, PoseStack poseStack, Matrix4f projectionMatrix, int renderTick, Camera camera, Frustum frustum)
    {
        RenderLevelStageEvent.Stage stage = RenderLevelStageEvent.Stage.fromRenderType(renderType);
        if (stage != null)
            dispatchRenderStage(stage, levelRenderer, poseStack, projectionMatrix, renderTick, camera, frustum);
    }

    public static boolean renderSpecificFirstPersonHand(InteractionHand hand, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick, float interpPitch, float swingProgress, float equipProgress, ItemStack stack)
    {
        return MinecraftForge.EVENT_BUS.post(new RenderHandEvent(hand, poseStack, bufferSource, packedLight, partialTick, interpPitch, swingProgress, equipProgress, stack));
    }

    public static boolean renderSpecificFirstPersonArm(PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, AbstractClientPlayer player, HumanoidArm arm)
    {
        return MinecraftForge.EVENT_BUS.post(new RenderArmEvent(poseStack, multiBufferSource, packedLight, player, arm));
    }

    public static void onTextureStitchedPre(TextureAtlas map, Set<ResourceLocation> resourceLocations)
    {
        StartupMessageManager.mcLoaderConsumer().ifPresent(c->c.accept("Atlas Stitching : "+map.m_118330_().toString()));
        ModLoader.get().postEvent(new TextureStitchEvent.Pre(map, resourceLocations));
//        ModelLoader.White.INSTANCE.register(map); // TODO Custom TAS
        Sheets.f_110743_.values().stream()
                .filter(rm -> rm.m_119193_().equals(map.m_118330_()))
                .forEach(rm -> resourceLocations.add(rm.m_119203_()));
    }

    public static void onTextureStitchedPost(TextureAtlas map)
    {
        ModLoader.get().postEvent(new TextureStitchEvent.Post(map));
    }

    public static void onBlockColorsInit(BlockColors blockColors)
    {
        ModLoader.get().postEvent(new ColorHandlerEvent.Block(blockColors));
    }

    public static void onItemColorsInit(ItemColors itemColors, BlockColors blockColors)
    {
        ModLoader.get().postEvent(new ColorHandlerEvent.Item(itemColors, blockColors));
    }

    static final ThreadLocal<RenderType> renderType = new ThreadLocal<RenderType>();

    public static void setRenderType(RenderType layer)
    {
        renderType.set(layer);
    }

    public static Model getArmorModel(LivingEntity entityLiving, ItemStack itemStack, EquipmentSlot slot, HumanoidModel<?> _default)
    {
        return RenderProperties.get(itemStack).getBaseArmorModel(entityLiving, itemStack, slot, _default);
    }

    /** Copies humanoid model properties from the original model to another, used for armor models */
    @SuppressWarnings("unchecked")
    public static <T extends LivingEntity> void copyModelProperties(HumanoidModel<T> original, HumanoidModel<?> replacement)
    {
        // this function does not make use of the <T> generic, so the unchecked cast should be safe
        original.m_102872_((HumanoidModel<T>)replacement);
        replacement.f_102808_.f_104207_ = original.f_102808_.f_104207_;
        replacement.f_102809_.f_104207_ = original.f_102809_.f_104207_;
        replacement.f_102810_.f_104207_ = original.f_102810_.f_104207_;
        replacement.f_102811_.f_104207_ = original.f_102811_.f_104207_;
        replacement.f_102812_.f_104207_ = original.f_102812_.f_104207_;
        replacement.f_102813_.f_104207_ = original.f_102813_.f_104207_;
        replacement.f_102814_.f_104207_ = original.f_102814_.f_104207_;
    }

    //This properly moves the domain, if provided, to the front of the string before concatenating
    public static String fixDomain(String base, String complex)
    {
        int idx = complex.indexOf(':');
        if (idx == -1)
        {
            return base + complex;
        }

        String name = complex.substring(idx + 1, complex.length());
        if (idx > 1)
        {
            String domain = complex.substring(0, idx);
            return domain + ':' + base + name;
        }
        else
        {
            return base + name;
        }
    }

    public static float getFieldOfView(Player entity, float fov)
    {
        FOVModifierEvent fovModifierEvent = new FOVModifierEvent(entity, fov);
        MinecraftForge.EVENT_BUS.post(fovModifierEvent);
        return fovModifierEvent.getNewfov();
    }

    public static double getFieldOfView(GameRenderer renderer, Camera camera, double partialTick, double fov) {
        EntityViewRenderEvent.FieldOfView event = new EntityViewRenderEvent.FieldOfView(renderer, camera, partialTick, fov);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getFOV();
    }

    /**
     * Initialization of Forge Renderers.
     */
    static
    {
        //FluidRegistry.renderIdFluid = RenderingRegistry.getNextAvailableRenderId();
        //RenderingRegistry.registerBlockHandler(RenderBlockFluid.instance);
    }

    public static void renderMainMenu(TitleScreen gui, PoseStack poseStack, Font font, int width, int height, int alpha)
    {
        VersionChecker.Status status = ForgeVersion.getStatus();
        if (status == BETA || status == BETA_OUTDATED)
        {
            // render a warning at the top of the screen,
            Component line = new TranslatableComponent("forge.update.beta.1", ChatFormatting.RED, ChatFormatting.RESET).m_130940_(ChatFormatting.RED);
            GuiComponent.m_93215_(poseStack, font, line, width / 2, 4 + (0 * (font.f_92710_ + 1)), 0xFFFFFF | alpha);
            line = new TranslatableComponent("forge.update.beta.2");
            GuiComponent.m_93215_(poseStack, font, line, width / 2, 4 + (1 * (font.f_92710_ + 1)), 0xFFFFFF | alpha);
        }

        String line = null;
        switch(status)
        {
            //case FAILED:        line = " Version check failed"; break;
            //case UP_TO_DATE:    line = "Forge up to date"}; break;
            //case AHEAD:         line = "Using non-recommended Forge build, issues may arise."}; break;
            case OUTDATED:
            case BETA_OUTDATED: line = I18n.m_118938_("forge.update.newversion", ForgeVersion.getTarget()); break;
            default: break;
        }

        forgeStatusLine = line;
    }

    public static String forgeStatusLine;
    public static SoundInstance playSound(SoundEngine manager, SoundInstance sound)
    {
        PlaySoundEvent e = new PlaySoundEvent(manager, sound);
        MinecraftForge.EVENT_BUS.post(e);
        return e.getSound();
    }

    public static void drawScreen(Screen screen, PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        poseStack.m_85836_();
        guiLayers.forEach(layer -> {
            // Prevent the background layers from thinking the mouse is over their controls and showing them as highlighted.
            drawScreenInternal(layer, poseStack, Integer.MAX_VALUE, Integer.MAX_VALUE, partialTick);
            poseStack.m_85837_(0,0,2000);
        });
        drawScreenInternal(screen, poseStack, mouseX, mouseY, partialTick);
        poseStack.m_85849_();
    }

    private static void drawScreenInternal(Screen screen, PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        if (!MinecraftForge.EVENT_BUS.post(new ScreenEvent.DrawScreenEvent.Pre(screen, poseStack, mouseX, mouseY, partialTick)))
            screen.m_6305_(poseStack, mouseX, mouseY, partialTick);
        MinecraftForge.EVENT_BUS.post(new ScreenEvent.DrawScreenEvent.Post(screen, poseStack, mouseX, mouseY, partialTick));
    }

    public static float getFogDensity(FogMode type, Camera camera, float partialTick, float density)
    {
        EntityViewRenderEvent.FogDensity event = new EntityViewRenderEvent.FogDensity(type, camera, partialTick, density);
        if (MinecraftForge.EVENT_BUS.post(event)) return event.getDensity();
        return -1;
    }

    /**
     * @deprecated to be removed in 1.19, use other onFogRender hook with more params
     */
    @Deprecated(forRemoval = true, since = "1.18.2")
    public static void onFogRender(FogMode type, Camera camera, float partialTick, float distance)
    {
        MinecraftForge.EVENT_BUS.post(new EntityViewRenderEvent.RenderFogEvent(type, camera, partialTick, distance));
    }

    public static void onFogRender(FogMode type, Camera camera, float partialTick, float nearDistance, float farDistance, FogShape shape)
    {
        EntityViewRenderEvent.RenderFogEvent event = new EntityViewRenderEvent.RenderFogEvent(type, camera, partialTick, nearDistance, farDistance, shape);
        if (MinecraftForge.EVENT_BUS.post(event))
        {
            RenderSystem.m_157445_(event.getNearPlaneDistance());
            RenderSystem.m_157443_(event.getFarPlaneDistance());
            RenderSystem.m_202160_(event.getFogShape());
        }
    }

    public static EntityViewRenderEvent.CameraSetup onCameraSetup(GameRenderer renderer, Camera camera, float partial)
    {
        EntityViewRenderEvent.CameraSetup event = new EntityViewRenderEvent.CameraSetup(renderer, camera, partial, camera.m_90590_(), camera.m_90589_(), 0);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    public static void onModelBake(ModelManager modelManager, Map<ResourceLocation, BakedModel> modelRegistry, ForgeModelBakery modelLoader)
    {
        ModLoader.get().postEvent(new ModelBakeEvent(modelManager, modelRegistry, modelLoader));
        modelLoader.onPostBakeEvent(modelRegistry);
    }

    private static final Matrix4f flipX;
    private static final Matrix3f flipXNormal;
    static {
        flipX = Matrix4f.m_27632_(-1,1,1);
        flipXNormal = new Matrix3f(flipX);
    }

    public static BakedModel handleCameraTransforms(PoseStack poseStack, BakedModel model, ItemTransforms.TransformType cameraTransformType, boolean leftHandHackery)
    {
        PoseStack stack = new PoseStack();
        model = model.handlePerspective(cameraTransformType, stack);

        // If the stack is not empty, the code has added a matrix for us to use.
        if (!stack.m_85851_())
        {
            // Apply the transformation to the real matrix stack, flipping for left hand
            Matrix4f tMat = stack.m_85850_().m_85861_();
            Matrix3f nMat = stack.m_85850_().m_85864_();
            if (leftHandHackery)
            {
                tMat.multiplyBackward(flipX);
                tMat.m_27644_(flipX);
                nMat.multiplyBackward(flipXNormal);
                nMat.m_8178_(flipXNormal);
            }
            poseStack.m_85850_().m_85861_().m_27644_(tMat);
            poseStack.m_85850_().m_85864_().m_8178_(nMat);
        }
        return model;
    }

    @SuppressWarnings("deprecation")
    public static TextureAtlasSprite[] getFluidSprites(BlockAndTintGetter level, BlockPos pos, FluidState fluidStateIn)
    {
        ResourceLocation overlayTexture = fluidStateIn.m_76152_().getAttributes().getOverlayTexture();
        return new TextureAtlasSprite[] {
                Minecraft.m_91087_().m_91258_(TextureAtlas.f_118259_).apply(fluidStateIn.m_76152_().getAttributes().getStillTexture(level, pos)),
                Minecraft.m_91087_().m_91258_(TextureAtlas.f_118259_).apply(fluidStateIn.m_76152_().getAttributes().getFlowingTexture(level, pos)),
                overlayTexture == null ? null : Minecraft.m_91087_().m_91258_(TextureAtlas.f_118259_).apply(overlayTexture),
        };
    }

    public static void gatherFluidTextures(Set<Material> textures)
    {
        ForgeRegistries.FLUIDS.getValues().stream()
                .flatMap(ForgeHooksClient::getFluidMaterials)
                .forEach(textures::add);
    }

    public static Stream<Material> getFluidMaterials(Fluid fluid)
    {
        return fluid.getAttributes().getTextures()
                .filter(Objects::nonNull)
                .map(ForgeHooksClient::getBlockMaterial);
    }

    @SuppressWarnings("deprecation")
    public static Material getBlockMaterial(ResourceLocation loc)
    {
        return new Material(TextureAtlas.f_118259_, loc);
    }

    /**
     * internal, relies on fixed format of FaceBakery
     */
    // TODO Do we need this?
    public static void fillNormal(int[] faceData, Direction facing)
    {
        Vector3f v1 = getVertexPos(faceData, 3);
        Vector3f t1 = getVertexPos(faceData, 1);
        Vector3f v2 = getVertexPos(faceData, 2);
        Vector3f t2 = getVertexPos(faceData, 0);
        v1.m_122267_(t1);
        v2.m_122267_(t2);
        v2.m_122279_(v1);
        v2.m_122278_();

        int x = ((byte) Math.round(v2.m_122239_() * 127)) & 0xFF;
        int y = ((byte) Math.round(v2.m_122260_() * 127)) & 0xFF;
        int z = ((byte) Math.round(v2.m_122269_() * 127)) & 0xFF;

        int normal = x | (y << 0x08) | (z << 0x10);

        for(int i = 0; i < 4; i++)
        {
            faceData[i * 8 + 7] = normal;
        }
    }

    private static Vector3f getVertexPos(int[] data, int vertex)
    {
        int idx = vertex * 8;

        float x = Float.intBitsToFloat(data[idx]);
        float y = Float.intBitsToFloat(data[idx + 1]);
        float z = Float.intBitsToFloat(data[idx + 2]);

        return new Vector3f(x, y, z);
    }

    public static void loadEntityShader(Entity entity, GameRenderer entityRenderer)
    {
        if (entity != null)
        {
            ResourceLocation shader = ClientRegistry.getEntityShader(entity.getClass());
            if (shader != null)
            {
                entityRenderer.m_109128_(shader);
            }
        }
    }

    private static int slotMainHand = 0;

    public static boolean shouldCauseReequipAnimation(@Nonnull ItemStack from, @Nonnull ItemStack to, int slot)
    {
        boolean fromInvalid = from.m_41619_();
        boolean toInvalid   = to.m_41619_();

        if (fromInvalid && toInvalid) return false;
        if (fromInvalid || toInvalid) return true;

        boolean changed = false;
        if (slot != -1)
        {
            changed = slot != slotMainHand;
            slotMainHand = slot;
        }
        return from.m_41720_().shouldCauseReequipAnimation(from, to, changed);
    }

    public static RenderGameOverlayEvent.BossInfo renderBossEventPre(PoseStack poseStack, Window res, LerpingBossEvent bossInfo, int x, int y, int increment)
    {
        RenderGameOverlayEvent.BossInfo evt = new RenderGameOverlayEvent.BossInfo(poseStack, new RenderGameOverlayEvent(poseStack, MinecraftForgeClient.getPartialTick(), res),
                BOSSINFO, bossInfo, x, y, increment);
        MinecraftForge.EVENT_BUS.post(evt);
        return evt;
    }

    public static void renderBossEventPost(PoseStack poseStack, Window res)
    {
        MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(poseStack, new RenderGameOverlayEvent(poseStack, MinecraftForgeClient.getPartialTick(), res), BOSSINFO));
    }

    public static ScreenshotEvent onScreenshot(NativeImage image, File screenshotFile)
    {
        ScreenshotEvent event = new ScreenshotEvent(image, screenshotFile);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    public static void onClientChangeGameType(PlayerInfo info, GameType currentGameMode, GameType newGameMode)
    {
        if (currentGameMode != newGameMode)
        {
            ClientPlayerChangeGameTypeEvent evt = new ClientPlayerChangeGameTypeEvent(info, currentGameMode, newGameMode);
            MinecraftForge.EVENT_BUS.post(evt);
        }
    }

    @SuppressWarnings("deprecation")
    public static BakedModel handlePerspective(BakedModel model, ItemTransforms.TransformType type, PoseStack stack)
    {
        Transformation tr = TransformationHelper.toTransformation(model.m_7442_().m_111808_(type));
        if(!tr.isIdentity()) {
            tr.push(stack);
        }
        return model;
    }

    public static void onMovementInputUpdate(Player player, Input movementInput)
    {
        MinecraftForge.EVENT_BUS.post(new MovementInputUpdateEvent(player, movementInput));
    }

    public static boolean onScreenMouseClickedPre(Screen guiScreen, double mouseX, double mouseY, int button)
    {
        Event event = new ScreenEvent.MouseClickedEvent.Pre(guiScreen, mouseX, mouseY, button);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenMouseClickedPost(Screen guiScreen, double mouseX, double mouseY, int button, boolean handled)
    {
        Event event = new ScreenEvent.MouseClickedEvent.Post(guiScreen, mouseX, mouseY, button, handled);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getResult() == Event.Result.DEFAULT ? handled : event.getResult() == Event.Result.ALLOW;
    }

    public static boolean onScreenMouseReleasedPre(Screen guiScreen, double mouseX, double mouseY, int button)
    {
        Event event = new ScreenEvent.MouseReleasedEvent.Pre(guiScreen, mouseX, mouseY, button);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenMouseReleasedPost(Screen guiScreen, double mouseX, double mouseY, int button, boolean handled)
    {
        Event event = new ScreenEvent.MouseReleasedEvent.Post(guiScreen, mouseX, mouseY, button, handled);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getResult() == Event.Result.DEFAULT ? handled : event.getResult() == Event.Result.ALLOW;
    }

    public static boolean onScreenMouseDragPre(Screen guiScreen, double mouseX, double mouseY, int mouseButton, double dragX, double dragY)
    {
        Event event = new ScreenEvent.MouseDragEvent.Pre(guiScreen, mouseX, mouseY, mouseButton, dragX, dragY);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenMouseDragPost(Screen guiScreen, double mouseX, double mouseY, int mouseButton, double dragX, double dragY)
    {
        Event event = new ScreenEvent.MouseDragEvent.Post(guiScreen, mouseX, mouseY, mouseButton, dragX, dragY);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenMouseScrollPre(MouseHandler mouseHelper, Screen guiScreen, double scrollDelta)
    {
        Window mainWindow = guiScreen.getMinecraft().m_91268_();
        double mouseX = mouseHelper.m_91589_() * (double) mainWindow.m_85445_() / (double) mainWindow.m_85443_();
        double mouseY = mouseHelper.m_91594_() * (double) mainWindow.m_85446_() / (double) mainWindow.m_85444_();
        Event event = new ScreenEvent.MouseScrollEvent.Pre(guiScreen, mouseX, mouseY, scrollDelta);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenMouseScrollPost(MouseHandler mouseHelper, Screen guiScreen, double scrollDelta)
    {
        Window mainWindow = guiScreen.getMinecraft().m_91268_();
        double mouseX = mouseHelper.m_91589_() * (double) mainWindow.m_85445_() / (double) mainWindow.m_85443_();
        double mouseY = mouseHelper.m_91594_() * (double) mainWindow.m_85446_() / (double) mainWindow.m_85444_();
        Event event = new ScreenEvent.MouseScrollEvent.Post(guiScreen, mouseX, mouseY, scrollDelta);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenKeyPressedPre(Screen guiScreen, int keyCode, int scanCode, int modifiers)
    {
        Event event = new ScreenEvent.KeyboardKeyPressedEvent.Pre(guiScreen, keyCode, scanCode, modifiers);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenKeyPressedPost(Screen guiScreen, int keyCode, int scanCode, int modifiers)
    {
        Event event = new ScreenEvent.KeyboardKeyPressedEvent.Post(guiScreen, keyCode, scanCode, modifiers);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenKeyReleasedPre(Screen guiScreen, int keyCode, int scanCode, int modifiers)
    {
        Event event = new ScreenEvent.KeyboardKeyReleasedEvent.Pre(guiScreen, keyCode, scanCode, modifiers);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenKeyReleasedPost(Screen guiScreen, int keyCode, int scanCode, int modifiers)
    {
        Event event = new ScreenEvent.KeyboardKeyReleasedEvent.Post(guiScreen, keyCode, scanCode, modifiers);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenCharTypedPre(Screen guiScreen, char codePoint, int modifiers)
    {
        Event event = new ScreenEvent.KeyboardCharTypedEvent.Pre(guiScreen, codePoint, modifiers);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onScreenCharTypedPost(Screen guiScreen, char codePoint, int modifiers)
    {
        Event event = new ScreenEvent.KeyboardCharTypedEvent.Post(guiScreen, codePoint, modifiers);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static void onRecipesUpdated(RecipeManager mgr)
    {
        Event event = new RecipesUpdatedEvent(mgr);
        MinecraftForge.EVENT_BUS.post(event);
    }

    public static void fireMouseInput(int button, int action, int mods)
    {
        MinecraftForge.EVENT_BUS.post(new InputEvent.MouseInputEvent(button, action, mods));
    }

    public static void fireKeyInput(int key, int scanCode, int action, int modifiers)
    {
        MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent(key, scanCode, action, modifiers));
    }

    public static boolean onMouseScroll(MouseHandler mouseHelper, double scrollDelta)
    {
        Event event = new InputEvent.MouseScrollEvent(scrollDelta, mouseHelper.m_91560_(), mouseHelper.m_168090_(), mouseHelper.m_91584_(), mouseHelper.m_91589_(), mouseHelper.m_91594_());
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onRawMouseClicked(int button, int action, int mods)
    {
        return MinecraftForge.EVENT_BUS.post(new InputEvent.RawMouseEvent(button, action, mods));
    }

    public static InputEvent.ClickInputEvent onClickInput(int button, KeyMapping keyBinding, InteractionHand hand)
    {
        InputEvent.ClickInputEvent event = new InputEvent.ClickInputEvent(button, keyBinding, hand);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    public static void drawItemLayered(ItemRenderer renderer, BakedModel modelIn, ItemStack itemStackIn, PoseStack poseStack,
                                       MultiBufferSource bufferSource, int packedLight, int packedOverlay, boolean fabulous)
    {
        for(com.mojang.datafixers.util.Pair<BakedModel,RenderType> layerModel : modelIn.getLayerModels(itemStackIn, fabulous))
        {
            BakedModel layer = layerModel.getFirst();
            RenderType rendertype = layerModel.getSecond();
            net.minecraftforge.client.ForgeHooksClient.setRenderType(rendertype); // neded for compatibility with MultiLayerModels
            VertexConsumer ivertexbuilder;
            if (fabulous)
            {
                ivertexbuilder = ItemRenderer.m_115222_(bufferSource, rendertype, true, itemStackIn.m_41790_());
            } else {
                ivertexbuilder = ItemRenderer.m_115211_(bufferSource, rendertype, true, itemStackIn.m_41790_());
            }
            renderer.m_115189_(layer, itemStackIn, packedLight, packedOverlay, poseStack, ivertexbuilder);
        }
        net.minecraftforge.client.ForgeHooksClient.setRenderType(null);
    }

    public static boolean isNameplateInRenderDistance(Entity entity, double squareDistance) {
        if (entity instanceof LivingEntity) {
            final AttributeInstance attribute = ((LivingEntity) entity).m_21051_(ForgeMod.NAMETAG_DISTANCE.get());
            if (attribute != null) {
                return !(squareDistance > (attribute.m_22135_() * attribute.m_22135_()));
            }
        }
        return !(squareDistance > 4096.0f);
    }

    public static void renderPistonMovedBlocks(BlockPos pos, BlockState state, PoseStack stack, MultiBufferSource bufferSource, Level level, boolean checkSides, int packedOverlay, BlockRenderDispatcher blockRenderer) {
        RenderType.m_110506_().stream()
                .filter(t -> ItemBlockRenderTypes.canRenderInLayer(state, t))
                .forEach(rendertype ->
                {
                    setRenderType(rendertype);
                    VertexConsumer ivertexbuilder = bufferSource.m_6299_(rendertype == RenderType.m_110466_() ? RenderType.m_110469_() : rendertype);
                    blockRenderer.m_110937_().m_111047_(level, blockRenderer.m_110910_(state), state, pos, stack, ivertexbuilder, checkSides, new Random(), state.m_60726_(pos), packedOverlay);
                });
        setRenderType(null);
    }

    public static void registerForgeWorldPresetScreens()
    {
        ForgeWorldPresetScreens.registerPresets();
    }

    public static WorldPreset.PresetEditor getPresetEditor(Optional<WorldPreset> generator, @Nullable WorldPreset.PresetEditor biomegeneratortypescreens$ifactory)
    {
        return ForgeWorldPresetScreens.getPresetEditor(generator, biomegeneratortypescreens$ifactory);
    }

    public static boolean hasPresetEditor(Optional<WorldPreset> generator)
    {
        return getPresetEditor(generator, null) != null;
    }

    public static Optional<WorldPreset> getWorldPresetFromGenerator(WorldGenSettings dimensionGeneratorSettings)
    {
        return WorldPreset.m_101524_(dimensionGeneratorSettings);
    }

    public static Optional<WorldPreset> getDefaultWorldPreset()
    {
        return Optional.of(ForgeWorldPresetScreens.getDefaultPreset());
    }

    public static boolean shouldRenderEffect(MobEffectInstance effectInstance)
    {
        return RenderProperties.getEffectRenderer(effectInstance).shouldRender(effectInstance);
    }

    @Nullable
    public static TextureAtlasSprite loadTextureAtlasSprite(
            TextureAtlas textureAtlas,
            ResourceManager resourceManager, TextureAtlasSprite.Info textureInfo,
            Resource resource,
            int atlasWidth, int atlasHeight,
            int spriteX, int spriteY, int mipmapLevel,
            NativeImage image
    )
    {
        ForgeTextureMetadata metadata = ForgeTextureMetadata.forResource(resource);
        return metadata.getLoader() == null ? null : metadata.getLoader().load(textureAtlas, resourceManager, textureInfo, resource, atlasWidth, atlasHeight, spriteX, spriteY, mipmapLevel, image);
    }


    private static final Map<ModelLayerLocation, Supplier<LayerDefinition>> layerDefinitions = new HashMap<>();

    public static void registerLayerDefinition(ModelLayerLocation layerLocation, Supplier<LayerDefinition> supplier)
    {
        layerDefinitions.put(layerLocation, supplier);
    }

    public static void loadLayerDefinitions(ImmutableMap.Builder<ModelLayerLocation, LayerDefinition> builder) {
        layerDefinitions.forEach((k, v) -> builder.put(k, v.get()));
    }

    public static void processForgeListPingData(ServerStatus packet, ServerData target)
    {
        if (packet.getForgeData() != null) {
            final Map<String, String> mods = packet.getForgeData().getRemoteModData();
            final Map<ResourceLocation, Pair<String, Boolean>> remoteChannels = packet.getForgeData().getRemoteChannels();
            final int fmlver = packet.getForgeData().getFMLNetworkVersion();

            boolean fmlNetMatches = fmlver == NetworkConstants.FMLNETVERSION;
            boolean channelsMatch = NetworkRegistry.checkListPingCompatibilityForClient(remoteChannels);
            AtomicBoolean result = new AtomicBoolean(true);
            final List<String> extraClientMods = new ArrayList<>();
            ModList.get().forEachModContainer((modid, mc) ->
                    mc.getCustomExtension(IExtensionPoint.DisplayTest.class).ifPresent(ext-> {
                        boolean foundModOnServer = ext.remoteVersionTest().test(mods.get(modid), true);
                        result.compareAndSet(true, foundModOnServer);
                        if (!foundModOnServer) {
                            extraClientMods.add(modid);
                        }
                    })
            );
            boolean modsMatch = result.get();

            final Map<String, String> extraServerMods = mods.entrySet().stream().
                    filter(e -> !Objects.equals(NetworkConstants.IGNORESERVERONLY, e.getValue())).
                    filter(e -> !ModList.get().isLoaded(e.getKey())).
                    collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            LOGGER.debug(CLIENTHOOKS, "Received FML ping data from server at {}: FMLNETVER={}, mod list is compatible : {}, channel list is compatible: {}, extra server mods: {}", target.f_105363_, fmlver, modsMatch, channelsMatch, extraServerMods);

            String extraReason = null;

            if (!extraServerMods.isEmpty()) {
                extraReason = "fml.menu.multiplayer.extraservermods";
                LOGGER.info(CLIENTHOOKS, ForgeI18n.parseMessage(extraReason) + ": {}", extraServerMods.entrySet().stream()
                        .map(e -> e.getKey() + "@" + e.getValue())
                        .collect(Collectors.joining(", ")));
            }
            if (!modsMatch) {
                extraReason = "fml.menu.multiplayer.modsincompatible";
                LOGGER.info(CLIENTHOOKS, "Client has mods that are missing on server: {}", extraClientMods);
            }
            if (!channelsMatch) {
                extraReason = "fml.menu.multiplayer.networkincompatible";
            }

            if (fmlver < NetworkConstants.FMLNETVERSION) {
                extraReason = "fml.menu.multiplayer.serveroutdated";
            }
            if (fmlver > NetworkConstants.FMLNETVERSION) {
                extraReason = "fml.menu.multiplayer.clientoutdated";
            }
            target.forgeData = new ExtendedServerListData("FML", extraServerMods.isEmpty() && fmlNetMatches && channelsMatch && modsMatch, mods.size(), extraReason, packet.getForgeData().isTruncated());
        } else {
            target.forgeData = new ExtendedServerListData("VANILLA", NetworkRegistry.canConnectToVanillaServer(),0, null);
        }

    }

    private static final ResourceLocation ICON_SHEET = new ResourceLocation(ForgeVersion.MOD_ID, "textures/gui/icons.png");
    public static void drawForgePingInfo(JoinMultiplayerScreen gui, ServerData target, PoseStack poseStack, int x, int y, int width, int relativeMouseX, int relativeMouseY) {
        int idx;
        String tooltip;
        if (target.forgeData == null)
            return;
        switch (target.forgeData.type) {
            case "FML":
                if (target.forgeData.isCompatible) {
                    idx = 0;
                    tooltip = ForgeI18n.parseMessage("fml.menu.multiplayer.compatible", target.forgeData.numberOfMods);
                } else {
                    idx = 16;
                    if(target.forgeData.extraReason != null) {
                        String extraReason = ForgeI18n.parseMessage(target.forgeData.extraReason);
                        tooltip = ForgeI18n.parseMessage("fml.menu.multiplayer.incompatible.extra", extraReason);
                    } else {
                        tooltip = ForgeI18n.parseMessage("fml.menu.multiplayer.incompatible");
                    }
                }
                if (target.forgeData.truncated)
                {
                    tooltip += "\n" + ForgeI18n.parseMessage("fml.menu.multiplayer.truncated");
                }
                break;
            case "VANILLA":
                if (target.forgeData.isCompatible) {
                    idx = 48;
                    tooltip = ForgeI18n.parseMessage("fml.menu.multiplayer.vanilla");
                } else {
                    idx = 80;
                    tooltip = ForgeI18n.parseMessage("fml.menu.multiplayer.vanilla.incompatible");
                }
                break;
            default:
                idx = 64;
                tooltip = ForgeI18n.parseMessage("fml.menu.multiplayer.unknown", target.forgeData.type);
        }

        RenderSystem.m_157456_(0, ICON_SHEET);
        GuiComponent.m_93160_(poseStack, x + width - 18, y + 10, 16, 16, 0, idx, 16, 16, 256, 256);

        if(relativeMouseX > width - 15 && relativeMouseX < width && relativeMouseY > 10 && relativeMouseY < 26) {
            //this is not the most proper way to do it,
            //but works best here and has the least maintenance overhead
            gui.m_99707_(Arrays.stream(tooltip.split("\n")).map(TextComponent::new).collect(Collectors.toList()));
        }
    }

    private static Connection getClientConnection()
    {
        return Minecraft.m_91087_().m_91403_()!=null ? Minecraft.m_91087_().m_91403_().m_6198_() : null;
    }

    public static void handleClientLevelClosing(ClientLevel level)
    {
        Connection client = getClientConnection();
        // ONLY revert a non-local connection
        if (client != null && !client.m_129531_())
        {
            GameData.revertToFrozen();
        }
    }

    public static void firePlayerLogin(MultiPlayerGameMode pc, LocalPlayer player, Connection networkManager) {
        MinecraftForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggedInEvent(pc, player, networkManager));
    }

    public static void firePlayerLogout(MultiPlayerGameMode pc, LocalPlayer player) {
        MinecraftForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggedOutEvent(pc, player, player != null ? player.f_108617_ != null ? player.f_108617_.m_6198_() : null : null));
    }

    public static void firePlayerRespawn(MultiPlayerGameMode pc, LocalPlayer oldPlayer, LocalPlayer newPlayer, Connection networkManager) {
        MinecraftForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.RespawnEvent(pc, oldPlayer, newPlayer, networkManager));
    }


    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid="forge", bus= Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientEvents
    {
        @Nullable
        private static ShaderInstance rendertypeEntityTranslucentUnlitShader;

        public static ShaderInstance getEntityTranslucentUnlitShader()
        {
            return Objects.requireNonNull(rendertypeEntityTranslucentUnlitShader, "Attempted to call getEntityTranslucentUnlitShader before shaders have finished loading.");
        }

        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) throws IOException
        {
            event.registerShader(new ShaderInstance(event.getResourceManager(), new ResourceLocation("forge","rendertype_entity_unlit_translucent"), DefaultVertexFormat.f_85812_), (p_172645_) -> {
                rendertypeEntityTranslucentUnlitShader = p_172645_;
            });
        }
    }

    public static Font getTooltipFont(@Nullable Font forcedFont, @Nonnull ItemStack stack, Font fallbackFont)
    {
        if (forcedFont != null)
        {
            return forcedFont;
        }
        Font stackFont = RenderProperties.get(stack).getFont(stack);
        return stackFont == null ? fallbackFont : stackFont;
    }

    public static RenderTooltipEvent.Pre onRenderTooltipPre(@Nonnull ItemStack stack, PoseStack poseStack, int x, int y, int screenWidth, int screenHeight, @Nonnull List<ClientTooltipComponent> components, @Nullable Font forcedFont, @Nonnull Font fallbackFont)
    {
        var preEvent = new RenderTooltipEvent.Pre(stack, poseStack, x, y, screenWidth, screenHeight, getTooltipFont(forcedFont, stack, fallbackFont), components);
        MinecraftForge.EVENT_BUS.post(preEvent);
        return preEvent;
    }

    public static RenderTooltipEvent.Color onRenderTooltipColor(@Nonnull ItemStack stack, PoseStack poseStack, int x, int y, @Nonnull Font font, @Nonnull List<ClientTooltipComponent> components)
    {
        var colorEvent = new RenderTooltipEvent.Color(stack, poseStack, x, y, font, 0xf0100010, 0x505000FF, 0x5028007f, components);
        MinecraftForge.EVENT_BUS.post(colorEvent);
        return colorEvent;
    }

    public static List<ClientTooltipComponent> gatherTooltipComponents(ItemStack stack, List<? extends FormattedText> textElements, int mouseX, int screenWidth, int screenHeight, @Nullable Font forcedFont, Font fallbackFont)
    {
        return gatherTooltipComponents(stack, textElements, Optional.empty(), mouseX, screenWidth, screenHeight, forcedFont, fallbackFont);
    }

    public static List<ClientTooltipComponent> gatherTooltipComponents(ItemStack stack, List<? extends FormattedText> textElements, Optional<TooltipComponent> itemComponent, int mouseX, int screenWidth, int screenHeight, @Nullable Font forcedFont, Font fallbackFont)
    {
        Font font = getTooltipFont(forcedFont, stack, fallbackFont);
        List<Either<FormattedText, TooltipComponent>> elements = textElements.stream()
                .map((Function<FormattedText, Either<FormattedText, TooltipComponent>>) Either::left)
                .collect(Collectors.toCollection(ArrayList::new));
        itemComponent.ifPresent(c -> elements.add(1, Either.right(c)));

        var event = new RenderTooltipEvent.GatherComponents(stack, screenWidth, screenHeight, elements, -1);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return List.of();

        // text wrapping
        int tooltipTextWidth = event.getTooltipElements().stream()
                .mapToInt(either -> either.map(font::m_92852_, component -> 0))
                .max()
                .orElse(0);

        boolean needsWrap = false;

        int tooltipX = mouseX + 12;
        if (tooltipX + tooltipTextWidth + 4 > screenWidth)
        {
            tooltipX = mouseX - 16 - tooltipTextWidth;
            if (tooltipX < 4) // if the tooltip doesn't fit on the screen
            {
                if (mouseX > screenWidth / 2)
                    tooltipTextWidth = mouseX - 12 - 8;
                else
                    tooltipTextWidth = screenWidth - 16 - mouseX;
                needsWrap = true;
            }
        }

        if (event.getMaxWidth() > 0 && tooltipTextWidth > event.getMaxWidth())
        {
            tooltipTextWidth = event.getMaxWidth();
            needsWrap = true;
        }

        int tooltipTextWidthF = tooltipTextWidth;
        if (needsWrap)
        {
            return event.getTooltipElements().stream()
                    .flatMap(either -> either.map(
                            text -> font.m_92923_(text, tooltipTextWidthF).stream().map(ClientTooltipComponent::m_169948_),
                            component -> Stream.of(ClientTooltipComponent.m_169950_(component))
                    ))
                    .toList();
        }
        return event.getTooltipElements().stream()
                .map(either -> either.map(
                        text -> ClientTooltipComponent.m_169948_(text instanceof Component ? ((Component) text).m_7532_() : Language.m_128107_().m_5536_(text)),
                        ClientTooltipComponent::m_169950_
                ))
                .toList();
    }

    public static Comparator<ParticleRenderType> makeParticleRenderTypeComparator(List<ParticleRenderType> renderOrder)
    {
        Comparator<ParticleRenderType> vanillaComparator = Comparator.comparingInt(renderOrder::indexOf);
        return (typeOne, typeTwo) ->
        {
            boolean vanillaOne = renderOrder.contains(typeOne);
            boolean vanillaTwo = renderOrder.contains(typeTwo);

            if (vanillaOne && vanillaTwo)
            {
                return vanillaComparator.compare(typeOne, typeTwo);
            }
            if (!vanillaOne && !vanillaTwo)
            {
                return Integer.compare(System.identityHashCode(typeOne), System.identityHashCode(typeTwo));
            }
            return vanillaOne ? -1 : 1;
        };
    }

    public static Event.Result onScreenPotionSize(Screen screen)
    {
        final ScreenEvent.PotionSizeEvent event = new ScreenEvent.PotionSizeEvent(screen);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getResult();
    }

    public static boolean isBlockInSolidLayer(BlockState state)
    {
        return ItemBlockRenderTypes.canRenderInLayer(state, RenderType.m_110451_());
    }

    public static void createWorldConfirmationScreen(
            LevelStorageSource save, String worldName, boolean creatingWorld,
            Function<LevelStorageSource.LevelStorageAccess, WorldStem.WorldDataSupplier> worldData,
            Function<Function<LevelStorageSource.LevelStorageAccess, WorldStem.WorldDataSupplier>, Runnable> runAfter)
    {
        Component title = new TranslatableComponent("selectWorld.backupQuestion.experimental");
        Component msg = new TranslatableComponent("selectWorld.backupWarning.experimental")
                .m_130946_("\n\n")
                .m_7220_(new TranslatableComponent("forge.selectWorld.backupWarning.experimental.additional"));

        Screen screen = new ConfirmScreen(confirmed ->
        {
            if (confirmed)
            {
                //The WorldData is re-created when re-running the runnable,
                // so make sure to be setting the field to true on the right instance.
                runAfter.apply(worldData.andThen(wds -> (rm, dpc) ->
                        wds.m_206951_(rm, dpc).mapFirst(wd -> wd instanceof PrimaryLevelData pld ? pld.withConfirmedWarning(true) : wd))
                ).run();
            }
            else
            {
                Minecraft.m_91087_().m_91152_(null);

                if (creatingWorld) // delete save when cancelling creation.
                {
                    try (LevelStorageSource.LevelStorageAccess levelSave = save.m_78260_(worldName))
                    {
                        levelSave.m_78311_();
                    }
                    catch (IOException e)
                    {
                        SystemToast.m_94866_(Minecraft.m_91087_(), worldName);
                        LOGGER.error("Failed to delete world {}", worldName, e);
                    }
                }
            }
        }, title, msg, CommonComponents.f_130659_, CommonComponents.f_130656_);

        Minecraft.m_91087_().m_91152_(screen);
    }

    public static int getMaxMipmapLevel(int width, int height)
    {
        return Math.min(
                Mth.m_14173_(Math.max(1, width)),
                Mth.m_14173_(Math.max(1, height))
        );
    }
}
