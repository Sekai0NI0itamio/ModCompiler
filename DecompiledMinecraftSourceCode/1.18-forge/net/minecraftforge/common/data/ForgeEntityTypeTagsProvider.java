/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.Tags;

public final class ForgeEntityTypeTagsProvider extends EntityTypeTagsProvider
{
    public ForgeEntityTypeTagsProvider(DataGenerator generator, ExistingFileHelper existingFileHelper)
    {
        super(generator, "forge", existingFileHelper);
    }

    @Override
    protected void m_6577_()
    {
        m_206424_(Tags.EntityTypes.BOSSES).m_126584_(EntityType.f_20565_, EntityType.f_20496_);
    }

    @Override
    public String m_6055_()
    {
        return "Forge EntityType Tags";
    }
}
