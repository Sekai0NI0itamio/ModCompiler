package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;

@OnlyIn(Dist.CLIENT)
public class GlShaderModule implements AutoCloseable {
    private static final int NOT_ALLOCATED = -1;
    public static final GlShaderModule INVALID_SHADER = new GlShaderModule(-1, ResourceLocation.withDefaultNamespace("invalid"), ShaderType.VERTEX);
    private final ResourceLocation id;
    private int shaderId;
    private final ShaderType type;

    public GlShaderModule(int p_394164_, ResourceLocation p_396017_, ShaderType p_396671_) {
        this.id = p_396017_;
        this.shaderId = p_394164_;
        this.type = p_396671_;
    }

    public static GlShaderModule compile(ResourceLocation p_394653_, ShaderType p_392528_, String p_396613_) throws ShaderManager.CompilationException {
        RenderSystem.assertOnRenderThread();
        int i = GlStateManager.glCreateShader(GlConst.toGl(p_392528_));
        GlStateManager.glShaderSource(i, p_396613_);
        GlStateManager.glCompileShader(i);
        if (GlStateManager.glGetShaderi(i, 35713) == 0) {
            String s = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
            throw new ShaderManager.CompilationException("Couldn't compile " + p_392528_.getName() + " shader (" + p_394653_ + ") : " + s);
        } else {
            return new GlShaderModule(i, p_394653_, p_392528_);
        }
    }

    @Override
    public void close() {
        if (this.shaderId == -1) {
            throw new IllegalStateException("Already closed");
        } else {
            RenderSystem.assertOnRenderThread();
            GlStateManager.glDeleteShader(this.shaderId);
            this.shaderId = -1;
        }
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public int getShaderId() {
        return this.shaderId;
    }

    public String getDebugLabel() {
        return this.type.idConverter().idToFile(this.id).toString();
    }
}
