package net.minecraft.client.gui.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record TextureSetup(@Nullable GpuTextureView texure0, @Nullable GpuTextureView texure1, @Nullable GpuTextureView texure2) {
    private static final TextureSetup NO_TEXTURE_SETUP = new TextureSetup(null, null, null);
    private static int sortKeySeed;

    public static TextureSetup singleTexture(GpuTextureView p_409143_) {
        return new TextureSetup(p_409143_, null, null);
    }

    public static TextureSetup singleTextureWithLightmap(GpuTextureView p_409588_) {
        return new TextureSetup(p_409588_, null, Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
    }

    public static TextureSetup doubleTexture(GpuTextureView p_405803_, GpuTextureView p_409777_) {
        return new TextureSetup(p_405803_, p_409777_, null);
    }

    public static TextureSetup noTexture() {
        return NO_TEXTURE_SETUP;
    }

    public int getSortKey() {
        return this.hashCode();
    }

    public static void updateSortKeySeed() {
        sortKeySeed = Math.round(100000.0F * (float)Math.random());
    }
}
