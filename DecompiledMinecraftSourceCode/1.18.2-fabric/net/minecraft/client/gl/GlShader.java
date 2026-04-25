/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.gl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Program;

@Environment(value=EnvType.CLIENT)
public interface GlShader {
    public int getProgramRef();

    public void markUniformsDirty();

    public Program getVertexShader();

    public Program getFragmentShader();

    public void attachReferencedShaders();
}

