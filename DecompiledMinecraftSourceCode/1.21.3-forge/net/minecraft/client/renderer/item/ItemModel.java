package net.minecraft.client.renderer.item;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.util.RegistryContextSwapper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ItemModel {
    void update(
        ItemStackRenderState p_377489_,
        ItemStack p_376390_,
        ItemModelResolver p_378232_,
        ItemDisplayContext p_376927_,
        @Nullable ClientLevel p_377374_,
        @Nullable LivingEntity p_376127_,
        int p_377873_
    );

    @OnlyIn(Dist.CLIENT)
    public record BakingContext(ModelBaker blockModelBaker, EntityModelSet entityModelSet, ItemModel missingItemModel, @Nullable RegistryContextSwapper contextSwapper) {
    }

    @OnlyIn(Dist.CLIENT)
    public interface Unbaked extends ResolvableModel {
        MapCodec<? extends ItemModel.Unbaked> type();

        ItemModel bake(ItemModel.BakingContext p_376062_);
    }
}
