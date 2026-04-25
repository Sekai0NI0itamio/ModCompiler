package net.minecraft.client.resources.model;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ModelBaker {
    ResolvedModel getModel(ResourceLocation p_397309_);

    SpriteGetter sprites();

    <T> T compute(ModelBaker.SharedOperationKey<T> p_395456_);

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface SharedOperationKey<T> {
        T compute(ModelBaker p_393089_);
    }

    /** Forge: Return the render type to use when baking this model, its a dirty hack to pass down this value to parents */
    @org.jetbrains.annotations.Nullable
    default net.minecraftforge.client.RenderTypeGroup renderType() {
        return null;
    }

    /** Forge: Return the fast graphics render type to use when baking this model, its a dirty hack to pass down this value to parents */
    @org.jetbrains.annotations.Nullable
    default net.minecraftforge.client.RenderTypeGroup renderTypeFast() {
        return null;
    }
}
