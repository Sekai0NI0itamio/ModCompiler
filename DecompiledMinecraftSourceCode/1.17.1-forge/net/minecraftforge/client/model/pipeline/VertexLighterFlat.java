/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.client.model.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.minecraft.world.level.BlockAndTintGetter;

public class VertexLighterFlat extends QuadGatheringTransformer
{
    protected static final VertexFormatElement NORMAL_4F = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.NORMAL, 4);
    
    // TODO 1.16/1.17 possibly refactor out the need for the "unpacked" format entirely. It's creating more headaches than solutions.
    // This mess reverses the conversion to float bits done in LightUtil.unpack
    private static final int LIGHTMAP_PACKING_FACTOR = ((256 << (8 * (DefaultVertexFormat.f_85808_.m_86041_().m_86074_() - 1))) - 1) >>> 1;
    // Max lightmap value, for rescaling
    private static final int LIGHTMAP_MAX = 0xF0;
    // Inlined factor for rescaling input lightmap values, "rounded" up to the next float value to avoid precision loss when result is truncated to int
    private static final float LIGHTMAP_RESCALE = Math.nextAfter((float) LIGHTMAP_PACKING_FACTOR / LIGHTMAP_MAX, LIGHTMAP_PACKING_FACTOR);

    protected final BlockInfo blockInfo;
    private int tint = -1;
    private boolean diffuse = true;

    protected int posIndex = -1;
    protected int normalIndex = -1;
    protected int colorIndex = -1;
    protected int lightmapIndex = -1;

    protected VertexFormat baseFormat;
    protected PoseStack.Pose pose;
    
    public VertexLighterFlat(BlockColors colors)
    {
        this.blockInfo = new BlockInfo(colors);
    }

    @Override
    public void setParent(IVertexConsumer parent)
    {
        super.setParent(parent);
        setVertexFormat(parent.getVertexFormat());
    }
    
    public void setTransform(final PoseStack.Pose pose)
    {
        this.pose = pose;
    }

    private void updateIndices()
    {
        for(int i = 0; i < getVertexFormat().m_86023_().size(); i++)
        {
            switch(getVertexFormat().m_86023_().get(i).m_86048_())
            {
                case POSITION:
                    posIndex = i;
                    break;
                case NORMAL:
                    normalIndex = i;
                    break;
                case COLOR:
                    colorIndex = i;
                    break;
                case UV:
                    if(getVertexFormat().m_86023_().get(i).m_86049_() == 2)
                    {
                        lightmapIndex = i;
                    }
                    break;
                default:
            }
        }
        if(posIndex == -1)
        {
            throw new IllegalArgumentException("vertex lighter needs format with position");
        }
        if(lightmapIndex == -1)
        {
            throw new IllegalArgumentException("vertex lighter needs format with lightmap");
        }
        if(colorIndex == -1)
        {
            throw new IllegalArgumentException("vertex lighter needs format with color");
        }
    }

    @Override
    public void setVertexFormat(VertexFormat format)
    {
        if (Objects.equals(format, baseFormat)) return;
        baseFormat = format;
        super.setVertexFormat(withNormal(format));
        updateIndices();
    }

    static VertexFormat withNormal(VertexFormat format)
    {
        //This is the case in 99.99%. Cache the value, so we don't have to redo it every time, and the speed up the equals check in LightUtil
        if (format == DefaultVertexFormat.f_85811_)
            return DefaultVertexFormat.f_85811_;
        return withNormalUncached(format);
    }

    private static VertexFormat withNormalUncached(VertexFormat format)
    {
        if (format == null || format.hasNormal())
            return format;
        Map<String, VertexFormatElement> m = Maps.newHashMap(format.getElementMapping());
        m.put("Normal", NORMAL_4F);
        return new VertexFormat(ImmutableMap.copyOf(m));
    }

    @Override
    protected void processQuad()
    {
        float[][] position = quadData[posIndex];
        float[][] normal = null;
        float[][] lightmap = quadData[lightmapIndex];
        float[][] color = quadData[colorIndex];

        if (dataLength[normalIndex] >= 3
            && (quadData[normalIndex][0][0] != 0
            ||  quadData[normalIndex][0][1] != 0
            ||  quadData[normalIndex][0][2] != 0))
        {
            normal = quadData[normalIndex];
        }
        else // normals must be generated
        {
            normal = new float[4][4];
            Vector3f v1 = new Vector3f(position[3]);
            Vector3f t = new Vector3f(position[1]);
            Vector3f v2 = new Vector3f(position[2]);
            v1.m_122267_(t);
            t.set(position[0]);
            v2.m_122267_(t);
            v2.m_122279_(v1);
            v2.m_122278_();
            for(int v = 0; v < 4; v++)
            {
                normal[v][0] = v2.m_122239_();
                normal[v][1] = v2.m_122260_();
                normal[v][2] = v2.m_122269_();
                normal[v][3] = 0;
            }
        }

        int multiplier = -1;
        if(tint != -1)
        {
            multiplier = blockInfo.getColorMultiplier(tint);
        }

        VertexFormat format = parent.getVertexFormat();
        int count = format.m_86023_().size();

        for(int v = 0; v < 4; v++)
        {
            float x = position[v][0] - .5f;
            float y = position[v][1] - .5f;
            float z = position[v][2] - .5f;

//            if(blockInfo.getState().getBlock().isFullCube(blockInfo.getState()))
            {
                x += normal[v][0] * .5f;
                y += normal[v][1] * .5f;
                z += normal[v][2] * .5f;
            }

            float blockLight = lightmap[v][0] * LIGHTMAP_RESCALE, skyLight = lightmap[v][1] * LIGHTMAP_RESCALE;
            updateLightmap(normal[v], lightmap[v], x, y, z);
            if(dataLength[lightmapIndex] > 1)
            {
                if(blockLight > lightmap[v][0]) lightmap[v][0] = blockLight;
                if(skyLight > lightmap[v][1]) lightmap[v][1] = skyLight;
            }
            updateColor(normal[v], color[v], x, y, z, tint, multiplier);
            if(diffuse)
            {
                float d = LightUtil.diffuseLight(normal[v][0], normal[v][1], normal[v][2]);
                for(int i = 0; i < 3; i++)
                {
                    color[v][i] *= d;
                }
            }

            // no need for remapping cause all we could've done is add 1 element to the end
            for(int e = 0; e < count; e++)
            {
                VertexFormatElement element = format.m_86023_().get(e);
                switch(element.m_86048_())
                {
                    case POSITION:
                        final Vector4f pos = new Vector4f(
                                position[v][0], position[v][1], position[v][2], 1);
                        pos.m_123607_(pose.m_85861_());

                        position[v][0] = pos.m_123601_();
                        position[v][1] = pos.m_123615_();
                        position[v][2] = pos.m_123616_();
                        parent.put(e, position[v]);
                        break;
                    case NORMAL:
                        final Vector3f norm = new Vector3f(normal[v]);
                        norm.m_122249_(pose.m_85864_());

                        normal[v][0] = norm.m_122239_();
                        normal[v][1] = norm.m_122260_();
                        normal[v][2] = norm.m_122269_();
                        parent.put(e, normal[v]);
                        break;
                    case COLOR:
                        parent.put(e, color[v]);
                        break;
                    case UV:
                        if(element.m_86049_() == 2)
                        {
                            parent.put(e, lightmap[v]);
                            break;
                        }
                        // else fallthrough to default
                    default:
                        parent.put(e, quadData[e][v]);
                }
            }
        }
        tint = -1;
    }

    protected void updateLightmap(float[] normal, float[] lightmap, float x, float y, float z)
    {
        final float e1 = 1f - 1e-2f;
        final float e2 = 0.95f;

        boolean full = blockInfo.isFullCube();
        Direction side = null;

             if((full || y < -e1) && normal[1] < -e2) side = Direction.DOWN;
        else if((full || y >  e1) && normal[1] >  e2) side = Direction.UP;
        else if((full || z < -e1) && normal[2] < -e2) side = Direction.NORTH;
        else if((full || z >  e1) && normal[2] >  e2) side = Direction.SOUTH;
        else if((full || x < -e1) && normal[0] < -e2) side = Direction.WEST;
        else if((full || x >  e1) && normal[0] >  e2) side = Direction.EAST;

        int i = side == null ? 0 : side.ordinal() + 1;
        int brightness = blockInfo.getPackedLight()[i];

        lightmap[0] = LightTexture.m_109883_(brightness) / (float) 0xF;
        lightmap[1] = LightTexture.m_109894_(brightness) / (float) 0xF;
    }

    protected void updateColor(float[] normal, float[] color, float x, float y, float z, float tint, int multiplier)
    {
        if(tint != -1)
        {
            color[0] *= (float)(multiplier >> 0x10 & 0xFF) / 0xFF;
            color[1] *= (float)(multiplier >> 0x8 & 0xFF) / 0xFF;
            color[2] *= (float)(multiplier & 0xFF) / 0xFF;
        }
    }

    @Override
    public void setQuadTint(int tint)
    {
        this.tint = tint;
    }
    @Override
    public void setQuadOrientation(Direction orientation) {}
    public void setQuadCulled() {}
    @Override
    public void setTexture(TextureAtlasSprite texture) {}
    @Override
    public void setApplyDiffuseLighting(boolean diffuse)
    {
        this.diffuse = diffuse;
    }

    public void setWorld(BlockAndTintGetter world)
    {
        blockInfo.setWorld(world);
    }

    public void setState(BlockState state)
    {
        blockInfo.setState(state);
    }

    public void setBlockPos(BlockPos blockPos)
    {
        blockInfo.setBlockPos(blockPos);
    }

    public void resetBlockInfo()
    {
        blockInfo.reset();
    }

    public void updateBlockInfo()
    {
        blockInfo.updateFlatLighting();
    }
}
