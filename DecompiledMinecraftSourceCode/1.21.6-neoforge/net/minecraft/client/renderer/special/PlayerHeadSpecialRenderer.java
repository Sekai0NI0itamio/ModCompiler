package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class PlayerHeadSpecialRenderer implements SpecialModelRenderer<PlayerHeadSpecialRenderer.PlayerHeadRenderInfo> {
    private final Map<ResolvableProfile, PlayerHeadSpecialRenderer.PlayerHeadRenderInfo> updatedResolvableProfiles = new HashMap<>();
    private final SkinManager skinManager;
    private final SkullModelBase modelBase;
    private final PlayerHeadSpecialRenderer.PlayerHeadRenderInfo defaultPlayerHeadRenderInfo;

    PlayerHeadSpecialRenderer(SkinManager p_408526_, SkullModelBase p_406150_, PlayerHeadSpecialRenderer.PlayerHeadRenderInfo p_407994_) {
        this.skinManager = p_408526_;
        this.modelBase = p_406150_;
        this.defaultPlayerHeadRenderInfo = p_407994_;
    }

    public void render(
        @Nullable PlayerHeadSpecialRenderer.PlayerHeadRenderInfo p_408577_,
        ItemDisplayContext p_408440_,
        PoseStack p_407818_,
        MultiBufferSource p_407854_,
        int p_410692_,
        int p_408293_,
        boolean p_406476_
    ) {
        PlayerHeadSpecialRenderer.PlayerHeadRenderInfo playerheadspecialrenderer$playerheadrenderinfo = Objects.requireNonNullElse(p_408577_, this.defaultPlayerHeadRenderInfo);
        RenderType rendertype = playerheadspecialrenderer$playerheadrenderinfo.renderType();
        SkullBlockRenderer.renderSkull(null, 180.0F, 0.0F, p_407818_, p_407854_, p_410692_, this.modelBase, rendertype);
    }

    @Override
    public void getExtents(Set<Vector3f> p_407350_) {
        PoseStack posestack = new PoseStack();
        posestack.translate(0.5F, 0.0F, 0.5F);
        posestack.scale(-1.0F, -1.0F, 1.0F);
        this.modelBase.root().getExtentsForGui(posestack, p_407350_);
    }

    @Nullable
    public PlayerHeadSpecialRenderer.PlayerHeadRenderInfo extractArgument(ItemStack p_407162_) {
        ResolvableProfile resolvableprofile = p_407162_.get(DataComponents.PROFILE);
        if (resolvableprofile == null) {
            return null;
        } else {
            PlayerHeadSpecialRenderer.PlayerHeadRenderInfo playerheadspecialrenderer$playerheadrenderinfo = this.updatedResolvableProfiles.get(resolvableprofile);
            if (playerheadspecialrenderer$playerheadrenderinfo != null) {
                return playerheadspecialrenderer$playerheadrenderinfo;
            } else {
                ResolvableProfile resolvableprofile1 = resolvableprofile.pollResolve();
                return resolvableprofile1 != null ? this.createAndCacheIfTextureIsUnpacked(resolvableprofile1) : null;
            }
        }
    }

    @Nullable
    private PlayerHeadSpecialRenderer.PlayerHeadRenderInfo createAndCacheIfTextureIsUnpacked(ResolvableProfile p_407824_) {
        PlayerSkin playerskin = this.skinManager.getInsecureSkin(p_407824_.gameProfile(), null);
        if (playerskin != null) {
            PlayerHeadSpecialRenderer.PlayerHeadRenderInfo playerheadspecialrenderer$playerheadrenderinfo = PlayerHeadSpecialRenderer.PlayerHeadRenderInfo.create(
                playerskin
            );
            this.updatedResolvableProfiles.put(p_407824_, playerheadspecialrenderer$playerheadrenderinfo);
            return playerheadspecialrenderer$playerheadrenderinfo;
        } else {
            return null;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public record PlayerHeadRenderInfo(RenderType renderType) {
        static PlayerHeadSpecialRenderer.PlayerHeadRenderInfo create(PlayerSkin p_409644_) {
            return new PlayerHeadSpecialRenderer.PlayerHeadRenderInfo(SkullBlockRenderer.getPlayerSkinRenderType(p_409644_.texture()));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<PlayerHeadSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(PlayerHeadSpecialRenderer.Unbaked::new);

        @Override
        public MapCodec<PlayerHeadSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Nullable
        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet p_406669_) {
            SkullModelBase skullmodelbase = SkullBlockRenderer.createModel(p_406669_, SkullBlock.Types.PLAYER);
            return skullmodelbase == null
                ? null
                : new PlayerHeadSpecialRenderer(
                    Minecraft.getInstance().getSkinManager(), skullmodelbase, PlayerHeadSpecialRenderer.PlayerHeadRenderInfo.create(DefaultPlayerSkin.getDefaultSkin())
                );
        }
    }
}
