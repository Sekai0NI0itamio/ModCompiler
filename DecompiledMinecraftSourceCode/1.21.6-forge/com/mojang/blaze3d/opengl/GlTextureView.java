package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GlTextureView extends GpuTextureView {
    private boolean closed;

    protected GlTextureView(GlTexture p_409590_, int p_406886_, int p_408540_) {
        super(p_409590_, p_406886_, p_408540_);
        p_409590_.addViews();
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.texture().removeViews();
        }
    }

    public GlTexture texture() {
        return (GlTexture)super.texture();
    }
}
