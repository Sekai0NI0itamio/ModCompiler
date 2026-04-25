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

import com.mojang.math.Transformation;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;

public class TRSRTransformer extends VertexTransformer
{
    private final Transformation transform;

    public TRSRTransformer(IVertexConsumer parent, Transformation transform)
    {
        super(parent);
        this.transform = transform;
    }

    @Override
    public void put(int element, float... data)
    {
        switch (getVertexFormat().m_86023_().get(element).m_86048_())
        {
            case POSITION:
                Vector4f pos = new Vector4f(data[0], data[1], data[2], data[3]);
                transform.transformPosition(pos);
                data[0] = pos.m_123601_();
                data[1] = pos.m_123615_();
                data[2] = pos.m_123616_();
                data[3] = pos.m_123617_();
                break;
            case NORMAL:
                Vector3f normal = new Vector3f(data);
                transform.transformNormal(normal);
                data[0] = normal.m_122239_();
                data[1] = normal.m_122260_();
                data[2] = normal.m_122269_();
                break;
        }
        super.put(element, data);
    }
}
