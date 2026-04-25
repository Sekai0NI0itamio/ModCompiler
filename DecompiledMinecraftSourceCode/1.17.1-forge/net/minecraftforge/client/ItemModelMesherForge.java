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

package net.minecraftforge.client;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Maps;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.IRegistryDelegate;

/**
 * Wrapper around ItemModeMesher that cleans up the internal maps to respect ID remapping.
 */
public class ItemModelMesherForge extends ItemModelShaper
{
    final Map<IRegistryDelegate<Item>, ModelResourceLocation> locations = Maps.newHashMap();
    final Map<IRegistryDelegate<Item>, BakedModel> models = Maps.newHashMap();

    public ItemModelMesherForge(ModelManager manager)
    {
        super(manager);
    }

    @Override
    @Nullable
    public BakedModel m_109394_(Item item)
    {
        return models.get(item.delegate);
    }

    @Override
    public void m_109396_(Item item, ModelResourceLocation location)
    {
        IRegistryDelegate<Item> key = item.delegate;
        locations.put(key, location);
        models.put(key, m_109393_().m_119422_(location));
    }

    @Override
    public void m_109403_()
    {
        final ModelManager manager = this.m_109393_();
        for (Map.Entry<IRegistryDelegate<Item>, ModelResourceLocation> e : locations.entrySet())
        {
        	models.put(e.getKey(), manager.m_119422_(e.getValue()));
        }
    }

    public ModelResourceLocation getLocation(@Nonnull ItemStack stack)
    {
        ModelResourceLocation location = locations.get(stack.m_41720_().delegate);

        if (location == null)
        {
            location = ModelBakery.f_119230_;
        }

        return location;
    }
}
