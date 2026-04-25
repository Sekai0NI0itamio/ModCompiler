/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.model;

import net.minecraft.client.renderer.model.IModelTransform;

import com.google.common.collect.ImmutableMap;
import net.minecraft.util.math.vector.TransformationMatrix;

/**
 * Simple implementation of IModelState via a map and a default value.
 */
public final class SimpleModelTransform implements IModelTransform
{
    public static final SimpleModelTransform IDENTITY = new SimpleModelTransform(TransformationMatrix.func_227983_a_());

    private final ImmutableMap<?, TransformationMatrix> map;
    private final TransformationMatrix base;

    public SimpleModelTransform(ImmutableMap<?, TransformationMatrix> map)
    {
        this(map, TransformationMatrix.func_227983_a_());
    }

    public SimpleModelTransform(TransformationMatrix base)
    {
        this(ImmutableMap.of(), base);
    }

    public SimpleModelTransform(ImmutableMap<?, TransformationMatrix> map, TransformationMatrix base)
    {
        this.map = map;
        this.base = base;
    }

    @Override
    public TransformationMatrix func_225615_b_()
    {
        return base;
    }

    @Override
    public TransformationMatrix getPartTransformation(Object part)
    {
        return map.getOrDefault(part, TransformationMatrix.func_227983_a_());
    }
}
