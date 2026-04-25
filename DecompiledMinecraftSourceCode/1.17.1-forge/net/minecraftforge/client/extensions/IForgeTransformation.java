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

package net.minecraftforge.client.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;

import com.mojang.math.Matrix4f;
import com.mojang.math.Transformation;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;

public interface IForgeTransformation
{
    private Transformation self()
    {
        return (Transformation)this;
    }

    default boolean isIdentity()
    {
        return self().equals(Transformation.m_121093_());
    }

    default void push(PoseStack stack)
    {
        stack.m_85836_();

        Vector3f trans = self().m_175940_();
        stack.m_85837_(trans.m_122239_(), trans.m_122260_(), trans.m_122269_());

        stack.m_85845_(self().m_121105_());

        Vector3f scale = self().m_175941_();
        stack.m_85841_(scale.m_122239_(), scale.m_122260_(), scale.m_122269_());

        stack.m_85845_(self().m_175942_());

    }

    default void transformPosition(Vector4f position)
    {
        position.m_123607_(self().m_121104_());
    }

    default void transformNormal(Vector3f normal)
    {
        normal.m_122249_(self().getNormalMatrix());
        normal.m_122278_();
    }

    default Direction rotateTransform(Direction facing)
    {
        return Direction.m_122384_(self().m_121104_(), facing);
    }

    /**
     * convert transformation from assuming center-block system to opposing-corner-block system
     */
    default Transformation blockCenterToCorner()
    {
        return applyOrigin(new Vector3f(.5f, .5f, .5f));
    }

    /**
     * convert transformation from assuming opposing-corner-block system to center-block system
     */
    default Transformation blockCornerToCenter()
    {
        return applyOrigin(new Vector3f(-.5f, -.5f, -.5f));
    }

    /**
     * Apply this transformation to a different origin.
     * Can be used for switching between coordinate systems.
     * Parameter is relative to the current origin.
     */
    default Transformation applyOrigin(Vector3f origin) {
        Transformation transform = self();
        if (transform.isIdentity()) return Transformation.m_121093_();

        Matrix4f ret = transform.m_121104_();
        Matrix4f tmp = Matrix4f.m_27653_(origin.m_122239_(), origin.m_122260_(), origin.m_122269_());
        ret.multiplyBackward(tmp);
        tmp.m_27624_();
        tmp.setTranslation(-origin.m_122239_(), -origin.m_122260_(), -origin.m_122269_());
        ret.m_27644_(tmp);
        return new Transformation(ret);
    }
}
