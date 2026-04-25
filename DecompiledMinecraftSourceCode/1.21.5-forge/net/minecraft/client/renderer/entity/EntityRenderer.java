package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public abstract class EntityRenderer<T extends Entity, S extends EntityRenderState> {
    protected static final float NAMETAG_SCALE = 0.025F;
    public static final int LEASH_RENDER_STEPS = 24;
    public static final float LEASH_WIDTH = 0.05F;
    protected final EntityRenderDispatcher entityRenderDispatcher;
    private final Font font;
    protected float shadowRadius;
    protected float shadowStrength = 1.0F;
    private final S reusedState = this.createRenderState();

    protected EntityRenderer(EntityRendererProvider.Context p_174008_) {
        this.entityRenderDispatcher = p_174008_.getEntityRenderDispatcher();
        this.font = p_174008_.getFont();
    }

    public final int getPackedLightCoords(T p_114506_, float p_114507_) {
        BlockPos blockpos = BlockPos.containing(p_114506_.getLightProbePosition(p_114507_));
        return LightTexture.pack(this.getBlockLightLevel(p_114506_, blockpos), this.getSkyLightLevel(p_114506_, blockpos));
    }

    protected int getSkyLightLevel(T p_114509_, BlockPos p_114510_) {
        return p_114509_.level().getBrightness(LightLayer.SKY, p_114510_);
    }

    protected int getBlockLightLevel(T p_114496_, BlockPos p_114497_) {
        return p_114496_.isOnFire() ? 15 : p_114496_.level().getBrightness(LightLayer.BLOCK, p_114497_);
    }

    public boolean shouldRender(T p_114491_, Frustum p_114492_, double p_114493_, double p_114494_, double p_114495_) {
        if (!p_114491_.shouldRender(p_114493_, p_114494_, p_114495_)) {
            return false;
        } else if (!this.affectedByCulling(p_114491_)) {
            return true;
        } else {
            AABB aabb = this.getBoundingBoxForCulling(p_114491_).inflate(0.5);
            if (aabb.hasNaN() || aabb.getSize() == 0.0) {
                aabb = new AABB(
                    p_114491_.getX() - 2.0,
                    p_114491_.getY() - 2.0,
                    p_114491_.getZ() - 2.0,
                    p_114491_.getX() + 2.0,
                    p_114491_.getY() + 2.0,
                    p_114491_.getZ() + 2.0
                );
            }

            if (p_114492_.isVisible(aabb)) {
                return true;
            } else {
                if (p_114491_ instanceof Leashable leashable) {
                    Entity entity = leashable.getLeashHolder();
                    if (entity != null) {
                        AABB aabb1 = this.entityRenderDispatcher.getRenderer(entity).getBoundingBoxForCulling(entity);
                        return p_114492_.isVisible(aabb1) || p_114492_.isVisible(aabb.minmax(aabb1));
                    }
                }

                return false;
            }
        }
    }

    protected AABB getBoundingBoxForCulling(T p_365369_) {
        return p_365369_.getBoundingBox();
    }

    protected boolean affectedByCulling(T p_366877_) {
        return true;
    }

    public Vec3 getRenderOffset(S p_367733_) {
        return p_367733_.passengerOffset != null ? p_367733_.passengerOffset : Vec3.ZERO;
    }

    public void render(S p_370221_, PoseStack p_114488_, MultiBufferSource p_114489_, int p_114490_) {
        if (p_370221_.leashStates != null) {
            for (EntityRenderState.LeashState entityrenderstate$leashstate : p_370221_.leashStates) {
                renderLeash(p_114488_, p_114489_, entityrenderstate$leashstate);
            }
        }

        var event = net.minecraftforge.client.event.ForgeEventFactoryClient.fireRenderNameTagEvent(p_370221_, p_370221_.nameTag, this, p_114488_, p_114489_, p_114490_);
        if (!event.getResult().isDenied() && (event.getResult().isAllowed() || p_370221_.nameTag != null)) {
           this.renderNameTag(p_370221_, event.getContent(), p_114488_, p_114489_, p_114490_);
        }
    }

    private static void renderLeash(PoseStack p_344390_, MultiBufferSource p_342590_, EntityRenderState.LeashState p_366061_) {
        float f = (float)(p_366061_.end.x - p_366061_.start.x);
        float f1 = (float)(p_366061_.end.y - p_366061_.start.y);
        float f2 = (float)(p_366061_.end.z - p_366061_.start.z);
        float f3 = Mth.invSqrt(f * f + f2 * f2) * 0.05F / 2.0F;
        float f4 = f2 * f3;
        float f5 = f * f3;
        p_344390_.pushPose();
        p_344390_.translate(p_366061_.offset);
        VertexConsumer vertexconsumer = p_342590_.getBuffer(RenderType.leash());
        Matrix4f matrix4f = p_344390_.last().pose();

        for (int i = 0; i <= 24; i++) {
            addVertexPair(vertexconsumer, matrix4f, f, f1, f2, 0.05F, 0.05F, f4, f5, i, false, p_366061_);
        }

        for (int j = 24; j >= 0; j--) {
            addVertexPair(vertexconsumer, matrix4f, f, f1, f2, 0.05F, 0.0F, f4, f5, j, true, p_366061_);
        }

        p_344390_.popPose();
    }

    private static void addVertexPair(
        VertexConsumer p_344804_,
        Matrix4f p_343855_,
        float p_342047_,
        float p_343146_,
        float p_342344_,
        float p_342941_,
        float p_343681_,
        float p_343907_,
        float p_343356_,
        int p_342780_,
        boolean p_343253_,
        EntityRenderState.LeashState p_408542_
    ) {
        float f = p_342780_ / 24.0F;
        int i = (int)Mth.lerp(f, p_408542_.startBlockLight, p_408542_.endBlockLight);
        int j = (int)Mth.lerp(f, p_408542_.startSkyLight, p_408542_.endSkyLight);
        int k = LightTexture.pack(i, j);
        float f1 = p_342780_ % 2 == (p_343253_ ? 1 : 0) ? 0.7F : 1.0F;
        float f2 = 0.5F * f1;
        float f3 = 0.4F * f1;
        float f4 = 0.3F * f1;
        float f5 = p_342047_ * f;
        float f6;
        if (p_408542_.slack) {
            f6 = p_343146_ > 0.0F ? p_343146_ * f * f : p_343146_ - p_343146_ * (1.0F - f) * (1.0F - f);
        } else {
            f6 = p_343146_ * f;
        }

        float f7 = p_342344_ * f;
        p_344804_.addVertex(p_343855_, f5 - p_343907_, f6 + p_343681_, f7 + p_343356_).setColor(f2, f3, f4, 1.0F).setLight(k);
        p_344804_.addVertex(p_343855_, f5 + p_343907_, f6 + p_342941_ - p_343681_, f7 - p_343356_).setColor(f2, f3, f4, 1.0F).setLight(k);
    }

    protected boolean shouldShowName(T p_114504_, double p_363875_) {
        return p_114504_.shouldShowName() || p_114504_.hasCustomName() && p_114504_ == this.entityRenderDispatcher.crosshairPickEntity;
    }

    public Font getFont() {
        return this.font;
    }

    protected void renderNameTag(S p_364888_, Component p_114499_, PoseStack p_114500_, MultiBufferSource p_114501_, int p_114502_) {
        Vec3 vec3 = p_364888_.nameTagAttachment;
        if (vec3 != null) {
            boolean flag = !p_364888_.isDiscrete;
            int i = "deadmau5".equals(p_114499_.getString()) ? -10 : 0;
            p_114500_.pushPose();
            p_114500_.translate(vec3.x, vec3.y + 0.5, vec3.z);
            p_114500_.mulPose(this.entityRenderDispatcher.cameraOrientation());
            p_114500_.scale(0.025F, -0.025F, 0.025F);
            Matrix4f matrix4f = p_114500_.last().pose();
            Font font = this.getFont();
            float f = -font.width(p_114499_) / 2.0F;
            int j = (int)(Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
            font.drawInBatch(
                p_114499_, f, i, -2130706433, false, matrix4f, p_114501_, flag ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, j, p_114502_
            );
            if (flag) {
                font.drawInBatch(p_114499_, f, i, -1, false, matrix4f, p_114501_, Font.DisplayMode.NORMAL, 0, LightTexture.lightCoordsWithEmission(p_114502_, 2));
            }

            p_114500_.popPose();
        }
    }

    @Nullable
    protected Component getNameTag(T p_361489_) {
        return p_361489_.getDisplayName();
    }

    protected float getShadowRadius(S p_364114_) {
        return this.shadowRadius;
    }

    protected float getShadowStrength(S p_376038_) {
        return this.shadowStrength;
    }

    public abstract S createRenderState();

    public final S createRenderState(T p_363266_, float p_363950_) {
        S s = this.reusedState;
        this.extractRenderState(p_363266_, s, p_363950_);
        return s;
    }

    public void extractRenderState(T p_367571_, S p_367427_, float p_363243_) {
        p_367427_.entityType = p_367571_.getType();
        p_367427_.x = Mth.lerp(p_363243_, p_367571_.xOld, p_367571_.getX());
        p_367427_.y = Mth.lerp(p_363243_, p_367571_.yOld, p_367571_.getY());
        p_367427_.z = Mth.lerp(p_363243_, p_367571_.zOld, p_367571_.getZ());
        p_367427_.isInvisible = p_367571_.isInvisible();
        p_367427_.ageInTicks = p_367571_.tickCount + p_363243_;
        p_367427_.boundingBoxWidth = p_367571_.getBbWidth();
        p_367427_.boundingBoxHeight = p_367571_.getBbHeight();
        p_367427_.eyeHeight = p_367571_.getEyeHeight();
        if (p_367571_.isPassenger()
            && p_367571_.getVehicle() instanceof AbstractMinecart abstractminecart
            && abstractminecart.getBehavior() instanceof NewMinecartBehavior newminecartbehavior
            && newminecartbehavior.cartHasPosRotLerp()) {
            double d2 = Mth.lerp(p_363243_, abstractminecart.xOld, abstractminecart.getX());
            double d0 = Mth.lerp(p_363243_, abstractminecart.yOld, abstractminecart.getY());
            double d1 = Mth.lerp(p_363243_, abstractminecart.zOld, abstractminecart.getZ());
            p_367427_.passengerOffset = newminecartbehavior.getCartLerpPosition(p_363243_).subtract(new Vec3(d2, d0, d1));
        } else {
            p_367427_.passengerOffset = null;
        }

        p_367427_.distanceToCameraSq = this.entityRenderDispatcher.distanceToSqr(p_367571_);
        boolean flag1 = net.minecraftforge.client.ForgeHooksClient.isNameplateInRenderDistance(p_367571_, p_367427_.distanceToCameraSq) && this.shouldShowName(p_367571_, p_367427_.distanceToCameraSq);
        if (flag1) {
            p_367427_.nameTag = this.getNameTag(p_367571_);
            p_367427_.nameTagAttachment = p_367571_.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, p_367571_.getYRot(p_363243_));
        } else {
            p_367427_.nameTag = null;
        }

        label77: {
            p_367427_.isDiscrete = p_367571_.isDiscrete();
            if (p_367571_ instanceof Leashable leashable) {
                Entity $$11 = leashable.getLeashHolder();
                if ($$11 instanceof Entity) {
                    float f = p_367571_.getPreciseBodyRotation(p_363243_) * (float) (Math.PI / 180.0);
                    Vec3 vec31 = leashable.getLeashOffset(p_363243_);
                    BlockPos blockpos = BlockPos.containing(p_367571_.getEyePosition(p_363243_));
                    BlockPos blockpos1 = BlockPos.containing($$11.getEyePosition(p_363243_));
                    int i = this.getBlockLightLevel(p_367571_, blockpos);
                    int j = this.entityRenderDispatcher.getRenderer($$11).getBlockLightLevel($$11, blockpos1);
                    int k = p_367571_.level().getBrightness(LightLayer.SKY, blockpos);
                    int l = p_367571_.level().getBrightness(LightLayer.SKY, blockpos1);
                    boolean flag = $$11.supportQuadLeashAsHolder() && leashable.supportQuadLeash();
                    int i1 = flag ? 4 : 1;
                    if (p_367427_.leashStates == null || p_367427_.leashStates.size() != i1) {
                        p_367427_.leashStates = new ArrayList<>(i1);

                        for (int j1 = 0; j1 < i1; j1++) {
                            p_367427_.leashStates.add(new EntityRenderState.LeashState());
                        }
                    }

                    if (flag) {
                        float f1 = $$11.getPreciseBodyRotation(p_363243_) * (float) (Math.PI / 180.0);
                        Vec3 vec3 = $$11.getPosition(p_363243_);
                        Vec3[] avec3 = leashable.getQuadLeashOffsets();
                        Vec3[] avec31 = $$11.getQuadLeashHolderOffsets();
                        int k1 = 0;

                        while (true) {
                            if (k1 >= i1) {
                                break label77;
                            }

                            EntityRenderState.LeashState entityrenderstate$leashstate = p_367427_.leashStates.get(k1);
                            entityrenderstate$leashstate.offset = avec3[k1].yRot(-f);
                            entityrenderstate$leashstate.start = p_367571_.getPosition(p_363243_).add(entityrenderstate$leashstate.offset);
                            entityrenderstate$leashstate.end = vec3.add(avec31[k1].yRot(-f1));
                            entityrenderstate$leashstate.startBlockLight = i;
                            entityrenderstate$leashstate.endBlockLight = j;
                            entityrenderstate$leashstate.startSkyLight = k;
                            entityrenderstate$leashstate.endSkyLight = l;
                            entityrenderstate$leashstate.slack = false;
                            k1++;
                        }
                    } else {
                        Vec3 vec32 = vec31.yRot(-f);
                        EntityRenderState.LeashState entityrenderstate$leashstate1 = p_367427_.leashStates.getFirst();
                        entityrenderstate$leashstate1.offset = vec32;
                        entityrenderstate$leashstate1.start = p_367571_.getPosition(p_363243_).add(vec32);
                        entityrenderstate$leashstate1.end = $$11.getRopeHoldPosition(p_363243_);
                        entityrenderstate$leashstate1.startBlockLight = i;
                        entityrenderstate$leashstate1.endBlockLight = j;
                        entityrenderstate$leashstate1.startSkyLight = k;
                        entityrenderstate$leashstate1.endSkyLight = l;
                        break label77;
                    }
                }
            }

            p_367427_.leashStates = null;
        }

        p_367427_.displayFireAnimation = p_367571_.displayFireAnimation();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes() && !p_367427_.isInvisible && !minecraft.showOnlyReducedInfo()) {
            this.extractHitboxes(p_367571_, p_367427_, p_363243_);
        } else {
            p_367427_.hitboxesRenderState = null;
            p_367427_.serverHitboxesRenderState = null;
        }
    }

    private void extractHitboxes(T p_392440_, S p_392322_, float p_392236_) {
        p_392322_.hitboxesRenderState = this.extractHitboxes(p_392440_, p_392236_, false);
        p_392322_.serverHitboxesRenderState = null;
    }

    private HitboxesRenderState extractHitboxes(T p_393912_, float p_391350_, boolean p_397237_) {
        Builder<HitboxRenderState> builder = new Builder<>();
        AABB aabb = p_393912_.getBoundingBox();
        HitboxRenderState hitboxrenderstate;
        if (p_397237_) {
            hitboxrenderstate = new HitboxRenderState(
                aabb.minX - p_393912_.getX(),
                aabb.minY - p_393912_.getY(),
                aabb.minZ - p_393912_.getZ(),
                aabb.maxX - p_393912_.getX(),
                aabb.maxY - p_393912_.getY(),
                aabb.maxZ - p_393912_.getZ(),
                0.0F,
                1.0F,
                0.0F
            );
        } else {
            hitboxrenderstate = new HitboxRenderState(
                aabb.minX - p_393912_.getX(),
                aabb.minY - p_393912_.getY(),
                aabb.minZ - p_393912_.getZ(),
                aabb.maxX - p_393912_.getX(),
                aabb.maxY - p_393912_.getY(),
                aabb.maxZ - p_393912_.getZ(),
                1.0F,
                1.0F,
                1.0F
            );
        }

        builder.add(hitboxrenderstate);
        Entity entity = p_393912_.getVehicle();
        if (entity != null) {
            float f = Math.min(entity.getBbWidth(), p_393912_.getBbWidth()) / 2.0F;
            float f1 = 0.0625F;
            Vec3 vec3 = entity.getPassengerRidingPosition(p_393912_).subtract(p_393912_.position());
            HitboxRenderState hitboxrenderstate1 = new HitboxRenderState(
                vec3.x - f, vec3.y, vec3.z - f, vec3.x + f, vec3.y + 0.0625, vec3.z + f, 1.0F, 1.0F, 0.0F
            );
            builder.add(hitboxrenderstate1);
        }

        this.extractAdditionalHitboxes(p_393912_, builder, p_391350_);
        Vec3 vec31 = p_393912_.getViewVector(p_391350_);
        return new HitboxesRenderState(vec31.x, vec31.y, vec31.z, builder.build());
    }

    protected void extractAdditionalHitboxes(T p_392955_, Builder<HitboxRenderState> p_395671_, float p_395030_) {
        net.minecraft.client.renderer.entity.EnderDragonRenderer.extractAdditionalHitboxexGeneric(p_392955_, p_395671_, p_395030_); // Forge: Extract for our generic multi-part entity system
    }

    @Nullable
    private static Entity getServerSideEntity(Entity p_397464_) {
        IntegratedServer integratedserver = Minecraft.getInstance().getSingleplayerServer();
        if (integratedserver != null) {
            ServerLevel serverlevel = integratedserver.getLevel(p_397464_.level().dimension());
            if (serverlevel != null) {
                return serverlevel.getEntity(p_397464_.getId());
            }
        }

        return null;
    }
}
