/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.model.pipeline;

import net.minecraft.util.math.vector.TransformationMatrix;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector4f;

public class TRSRTransformer extends VertexTransformer
{
    private final TransformationMatrix transform;

    public TRSRTransformer(IVertexConsumer parent, TransformationMatrix transform)
    {
        super(parent);
        this.transform = transform;
    }

    @Override
    public void put(int element, float... data)
    {
        switch (getVertexFormat().func_227894_c_().get(element).func_177375_c())
        {
            case POSITION:
                Vector4f pos = new Vector4f(data[0], data[1], data[2], data[3]);
                transform.transformPosition(pos);
                data[0] = pos.func_195910_a();
                data[1] = pos.func_195913_b();
                data[2] = pos.func_195914_c();
                data[3] = pos.func_195915_d();
                break;
            case NORMAL:
                Vector3f normal = new Vector3f(data);
                transform.transformNormal(normal);
                data[0] = normal.func_195899_a();
                data[1] = normal.func_195900_b();
                data[2] = normal.func_195902_c();
                break;
        }
        super.put(element, data);
    }
}
