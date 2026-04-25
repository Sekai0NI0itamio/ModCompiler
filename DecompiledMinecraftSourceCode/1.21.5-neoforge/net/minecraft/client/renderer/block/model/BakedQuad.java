package net.minecraft.client.renderer.block.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record BakedQuad(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite, boolean shade, int lightEmission, boolean ambientOcclusion) {
    public BakedQuad(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite, boolean shade, int lightEmission) {
        this(vertices, tintIndex, direction, sprite, shade, lightEmission, true);
    }

    public boolean isTinted() {
        return this.tintIndex != -1;
    }
}
