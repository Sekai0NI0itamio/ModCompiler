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

package net.minecraftforge.registries;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.Validate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.DefaultedRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.serialization.Lifecycle;

class NamespacedDefaultedWrapper<T extends IForgeRegistryEntry<T>> extends DefaultedRegistry<T> implements ILockableRegistry
{
    private static final Logger LOGGER = LogManager.getLogger();
    private boolean locked = false;
    private ForgeRegistry<T> delegate;

    private NamespacedDefaultedWrapper(ForgeRegistry<T> owner)
    {
        super("empty", owner.getRegistryKey(), Lifecycle.experimental());
        this.delegate = owner;
    }

    @Override
    public <V extends T> V m_5748_(int id, ResourceKey<T> key, V value, Lifecycle lifecycle)
    {
        if (locked)
            throw new IllegalStateException("Can not register to a locked registry. Modder should use Forge Register methods.");
        Validate.notNull(value);

        if (value.getRegistryName() == null)
            value.setRegistryName(key.m_135782_());

        int realId = this.delegate.add(id, value);
        if (realId != id && id != -1)
            LOGGER.warn("Registered object did not get ID it asked for. Name: {} Type: {} Expected: {} Got: {}", key, value.getRegistryType().getName(), id, realId);

        return value;
    }

    @Override
    public <V extends T> V m_7135_(ResourceKey<T> key, V value, Lifecycle lifecycle)
    {
        return m_5748_(-1, key, value, lifecycle);
    }

    @Override
    public <V extends T> V m_7794_(OptionalInt id, ResourceKey<T> key, V value, Lifecycle lifecycle) {
        int wanted = -1;
        if (id.isPresent() && m_7942_(id.getAsInt()) != null)
            wanted = id.getAsInt();
        return m_5748_(wanted, key, value, lifecycle);
    }

    // Reading Functions
    @Override
    public Optional<T> m_6612_(@Nullable ResourceLocation name)
    {
        return Optional.ofNullable( this.delegate.getRaw(name)); //get without default
    }

    @Override
    @Nullable
    public T m_7745_(@Nullable ResourceLocation name)
    {
        return this.delegate.getValue(name); //getOrDefault
    }

    @Override
    @Nullable
    public T m_6246_(@Nullable ResourceKey<T> name)
    {
        return name == null ? null : this.delegate.getRaw(name.m_135782_()); //get without default
    }

    @Override
    @Nullable
    public ResourceLocation m_7981_(T value)
    {
        return this.delegate.getKey(value);
    }

    @Override
    public boolean m_7804_(ResourceLocation key)
    {
        return this.delegate.containsKey(key);
    }

    @Override
    public int m_7447_(@Nullable T value)
    {
        return this.delegate.getID(value);
    }

    @Override
    @Nullable
    public T m_7942_(int id)
    {
        return this.delegate.getValue(id);
    }

    @Override
    public Iterator<T> iterator()
    {
        return this.delegate.iterator();
    }

    @Override
    public Set<ResourceLocation> m_6566_()
    {
        return this.delegate.getKeys();
    }

    @Override
    public Set<Map.Entry<ResourceKey<T>, T>> m_6579_()
    {
        return this.delegate.getEntries();
    }

    @Override
    @Nullable
    public T m_142697_(Random random)
    {
        Collection<T> values = this.delegate.getValues();
        return values.stream().skip(random.nextInt(values.size())).findFirst().orElse(this.delegate.getDefault());
    }

    @Override
    public ResourceLocation m_122315_()
    {
        return this.delegate.getDefaultKey();
    }

    //internal
    @Override
    public void lock(){ this.locked = true; }

    public static class Factory<V extends IForgeRegistryEntry<V>> implements IForgeRegistry.CreateCallback<V>
    {
        public static final ResourceLocation ID = new ResourceLocation("forge", "registry_defaulted_wrapper");
        @Override
        public void onCreate(IForgeRegistryInternal<V> owner, RegistryManager stage)
        {
            owner.setSlaveMap(ID, new NamespacedDefaultedWrapper<V>((ForgeRegistry<V>)owner));
        }
    }
}
