/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
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

import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.DefaultedRegistry;
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
    public <V extends T> V func_218382_a(int id, RegistryKey<T> key, V value, Lifecycle lifecycle)
    {
        if (locked)
            throw new IllegalStateException("Can not register to a locked registry. Modder should use Forge Register methods.");
        Validate.notNull(value);

        if (value.getRegistryName() == null)
            value.setRegistryName(key.func_240901_a_());

        int realId = this.delegate.add(id, value);
        if (realId != id && id != -1)
            LOGGER.warn("Registered object did not get ID it asked for. Name: {} Type: {} Expected: {} Got: {}", key, value.getRegistryType().getName(), id, realId);

        return value;
    }

    @Override
    public <V extends T> V func_218381_a(RegistryKey<T> key, V value, Lifecycle lifecycle)
    {
        return func_218382_a(-1, key, value, lifecycle);
    }

    @Override
    public <V extends T> V func_241874_a(OptionalInt id, RegistryKey<T> key, V value, Lifecycle lifecycle) {
        int wanted = -1;
        if (id.isPresent() && func_148745_a(id.getAsInt()) != null)
            wanted = id.getAsInt();
        return func_218382_a(wanted, key, value, lifecycle);
    }

    // Reading Functions
    @Override
    public Optional<T> func_241873_b(@Nullable ResourceLocation name)
    {
        return Optional.ofNullable( this.delegate.getRaw(name)); //get without default
    }

    @Override
    @Nullable
    public T func_82594_a(@Nullable ResourceLocation name)
    {
        return this.delegate.getValue(name); //getOrDefault
    }

    @Override
    @Nullable
    public T func_230516_a_(@Nullable RegistryKey<T> name)
    {
        return name == null ? null : this.delegate.getRaw(name.func_240901_a_()); //get without default
    }

    @Override
    @Nullable
    public ResourceLocation func_177774_c(T value)
    {
        return this.delegate.getKey(value);
    }

    @Override
    public boolean func_212607_c(ResourceLocation key)
    {
        return this.delegate.containsKey(key);
    }

    @Override
    public int func_148757_b(@Nullable T value)
    {
        return this.delegate.getID(value);
    }

    @Override
    @Nullable
    public T func_148745_a(int id)
    {
        return this.delegate.getValue(id);
    }

    @Override
    public Iterator<T> iterator()
    {
        return this.delegate.iterator();
    }

    @Override
    public Set<ResourceLocation> func_148742_b()
    {
        return this.delegate.getKeys();
    }

    @Override
    public Set<Map.Entry<RegistryKey<T>, T>> func_239659_c_()
    {
        return this.delegate.getEntries();
    }

    @Override
    @Nullable
    public T func_186801_a(Random random)
    {
        Collection<T> values = this.delegate.getValues();
        return values.stream().skip(random.nextInt(values.size())).findFirst().orElse(this.delegate.getDefault());
    }

    @Override
    public ResourceLocation func_212609_b()
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
