/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.model.pipeline;

import com.mojang.blaze3d.vertex.VertexConsumer;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Assumes VertexFormatElement is present in the BufferBuilder's vertex format.
 */
public class VertexBufferConsumer implements IVertexConsumer
{
    private VertexFormat format;
    private List<VertexFormatElement> elements;
    private VertexConsumer renderer;
    private boolean overrideOverlayCoords;
    private int overlayCoordU;
    private int overlayCoordV;

    public VertexBufferConsumer()
    {
        setVertexFormat(DefaultVertexFormat.f_85811_);
    }

    public VertexBufferConsumer(VertexConsumer buffer)
    {
        setBuffer(buffer);
    }

    @Override
    public final VertexFormat getVertexFormat()
    {
        return format;
    }

    @Override
    public void put(int e, float... data)
    {
        final float d0 = data.length <= 0 ? 0 : data[0];
        final float d1 = data.length <= 1 ? 0 : data[1];
        final float d2 = data.length <= 2 ? 0 : data[2];
        final float d3 = data.length <= 3 ? 0 : data[3];

        var element = elements.get(e);

        switch (element.m_86048_())
        {
        case POSITION: // POSITION_3F
            if (element.m_86049_() == 0)
                renderer.m_5483_(d0, d1, d2);
            break;
        case NORMAL: // NORMAL_3B
            if (element.m_86049_() == 0)
                renderer.m_5601_(d0, d1, d2);
            break;
        case COLOR: // COLOR_4UB
            if (element.m_86049_() == 0)
                renderer.m_85950_(d0, d1, d2, d3);
            break;
        case UV: // TEX_2F
            switch(element.m_86049_())
            {
                case 0 -> renderer.m_7421_(d0, d1);
                case 1 -> {
                    if (overrideOverlayCoords)
                        renderer.m_7122_(overlayCoordU, overlayCoordV);
                    else
                        renderer.m_7122_((int) (d0 * 32767f), (int) (d1 * 32767f));
                }
                case 2 -> renderer.m_7120_((int) (d0 * 0xF0), (int) (d1 * 0xF0));
            }
            break;
        case PADDING:
            break;
        default:
            throw new IllegalArgumentException("Vertex element out of bounds: " + e);
        }
        if(e == 5)
        {
            renderer.m_5752_();
        }
    }

    public void setBuffer(VertexConsumer buffer)
    {
        this.renderer = buffer;
        setVertexFormat(buffer.getVertexFormat());
    }

    public void setVertexFormat(@Nullable VertexFormat format)
    {
        this.format = format != null ? format : DefaultVertexFormat.f_85811_;
        this.elements = this.format.m_86023_();
    }

    @Override
    public void setQuadTint(int tint) {}
    @Override
    public void setQuadOrientation(Direction orientation) {}
    @Override
    public void setApplyDiffuseLighting(boolean diffuse) {}
    @Override
    public void setTexture(TextureAtlasSprite texture ) {}

    public void setPackedOverlay(int packedOverlay)
    {
        this.overrideOverlayCoords = true;
        this.overlayCoordV = (packedOverlay >> 16) & 0xFFFF;
        this.overlayCoordU = (packedOverlay) & 0xFFFF;
    }
}
