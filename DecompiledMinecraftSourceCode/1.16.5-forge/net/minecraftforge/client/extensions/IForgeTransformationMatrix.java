/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.extensions;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.*;

/*
 * Replacement interface for ModelRotation to allow custom transformations of vanilla models.
 * You should probably use TRSRTransformation directly.
 */
public interface IForgeTransformationMatrix
{
    default TransformationMatrix getTransformaion()
    {
        return (TransformationMatrix)this;
    }

    default boolean isIdentity()
    {
        return getTransformaion().equals(TransformationMatrix.func_227983_a_());
    }

    default void push(MatrixStack stack)
    {
        stack.func_227860_a_();

        Vector3f trans = getTransformaion().getTranslation();
        stack.func_227861_a_(trans.func_195899_a(), trans.func_195900_b(), trans.func_195902_c());

        stack.func_227863_a_(getTransformaion().func_227989_d_());

        Vector3f scale = getTransformaion().getScale();
        stack.func_227862_a_(scale.func_195899_a(), scale.func_195900_b(), scale.func_195902_c());

        stack.func_227863_a_(getTransformaion().getRightRot());

    }

    default TransformationMatrix compose(TransformationMatrix other)
    {
        if (getTransformaion().isIdentity()) return other;
        if (other.isIdentity()) return getTransformaion();
        Matrix4f m = getTransformaion().func_227988_c_();
        m.func_226595_a_(other.func_227988_c_());
        return new TransformationMatrix(m);
    }

    default TransformationMatrix inverse()
    {
        if (isIdentity()) return getTransformaion();
        Matrix4f m = getTransformaion().func_227988_c_().func_226601_d_();
        m.func_226600_c_();
        return new TransformationMatrix(m);
    }

    default void transformPosition(Vector4f position)
    {
        position.func_229372_a_(getTransformaion().func_227988_c_());
    }

    default void transformNormal(Vector3f normal)
    {
        normal.func_229188_a_(getTransformaion().getNormalMatrix());
        normal.func_229194_d_();
    }

    default Direction rotateTransform(Direction facing)
    {
        return Direction.func_229385_a_(getTransformaion().func_227988_c_(), facing);
    }

    /**
     * convert transformation from assuming center-block system to opposing-corner-block system
     */
    default TransformationMatrix blockCenterToCorner()
    {
        return applyOrigin(new Vector3f(.5f, .5f, .5f));
    }

    /**
     * convert transformation from assuming opposing-corner-block system to center-block system
     */
    default TransformationMatrix blockCornerToCenter()
    {
        return applyOrigin(new Vector3f(-.5f, -.5f, -.5f));
    }

    /**
     * Apply this transformation to a different origin.
     * Can be used for switching between coordinate systems.
     * Parameter is relative to the current origin.
     */
    default TransformationMatrix applyOrigin(Vector3f origin) {
        TransformationMatrix transform = getTransformaion();
        if (transform.isIdentity()) return TransformationMatrix.func_227983_a_();

        Matrix4f ret = transform.func_227988_c_();
        Matrix4f tmp = Matrix4f.func_226599_b_(origin.func_195899_a(), origin.func_195900_b(), origin.func_195902_c());
        ret.multiplyBackward(tmp);
        tmp.func_226591_a_();
        tmp.setTranslation(-origin.func_195899_a(), -origin.func_195900_b(), -origin.func_195902_c());
        ret.func_226595_a_(tmp);
        return new TransformationMatrix(ret);
    }
}
