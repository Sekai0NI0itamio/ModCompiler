/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.registries;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;

import org.apache.commons.lang3.Validate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.DefaultedRegistry;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;

class NamespacedDefaultedWrapper<T extends IForgeRegistryEntry<T>> extends DefaultedRegistry<T> implements ILockableRegistry, IHolderHelperHolder<T>
{
    private final ForgeRegistry<T> delegate;
    private final NamespacedHolderHelper<T> holders;

    private boolean locked = false;
    private Lifecycle elementsLifecycle = Lifecycle.experimental();

    private NamespacedDefaultedWrapper(ForgeRegistry<T> owner, Function<T, Holder.Reference<T>> holderLookup)
    {
        super("empty", owner.getRegistryKey(), Lifecycle.experimental(), holderLookup);
        this.delegate = owner;
        this.holders = new NamespacedHolderHelper<>(owner, this, this.delegate.getDefaultKey(), holderLookup);
    }

    @Override
    public Holder<T> m_203704_(final int id, final ResourceKey<T> key, final T value, final Lifecycle lifecycle)
    {
        if (locked)
            throw new IllegalStateException("Can not register to a locked registry. Modder should use Forge Register methods.");

        Validate.notNull(value);
        this.elementsLifecycle = this.elementsLifecycle.add(lifecycle);

        if (value.getRegistryName() == null)
            value.setRegistryName(key.m_135782_());

        T oldValue = this.delegate.getRaw(value.getRegistryName());

        int realId = this.delegate.add(id, value);

        return this.holders.onAdded(RegistryManager.ACTIVE, realId, value, oldValue);
    }

    @Override
    public Holder<T> m_203505_(final ResourceKey<T> p_205891_, final T p_205892_, final Lifecycle p_205893_)
    {
        return m_203704_(-1, p_205891_, p_205892_, p_205893_);
    }

    @Override
    public Holder<T> m_203384_(final OptionalInt id, final ResourceKey<T> p_205885_, final T p_205886_, final Lifecycle p_205887_)
    {
        int wanted = -1;
        if (id.isPresent() && m_7942_(id.getAsInt()) != null)
            wanted = id.getAsInt();
        return m_203704_(wanted, p_205885_, p_205886_, p_205887_);
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
    public Optional<ResourceKey<T>> m_7854_(T p_122755_)
    {
        return this.delegate.getResourceKey(p_122755_);
    }

    @Override
    public boolean m_7804_(ResourceLocation key)
    {
        return this.delegate.containsKey(key);
    }

    @Override
    public boolean m_142003_(ResourceKey<T> key)
    {
       return this.delegate.getRegistryName().equals(key.m_211136_()) && m_7804_(key.m_135782_());
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
    public ResourceLocation m_122315_()
    {
        return this.delegate.getDefaultKey();
    }

    @Override
    public Lifecycle m_6228_(T value)
    {
        return Lifecycle.stable();
    }

    @Override
    public Lifecycle m_7837_()
    {
       return this.elementsLifecycle;
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
    public boolean m_142427_()
    {
        return this.delegate.isEmpty();
    }

    @Override
    public int m_183450_()
    {
        return this.delegate.size();
    }

    @Override
    public NamespacedHolderHelper<T> getHolderHelper()
    {
        return this.holders;
    }

    @Override public Optional<Holder<T>> m_203300_(int id) { return this.holders.getHolder(id); }
    @Override public Optional<Holder<T>> m_203636_(ResourceKey<T> key) { return this.holders.getHolder(key); }
    @Override public Holder<T> m_203538_(ResourceKey<T> key) { return this.holders.getOrCreateHolder(key); }
    @Override public Optional<Holder<T>> m_203454_(Random rand) { return this.holders.getRandom(rand); }
    @Override public Stream<Holder.Reference<T>> m_203611_() { return this.holders.holders();  }
    @Override public boolean m_203658_(TagKey<T> name) { return this.holders.isKnownTagName(name); }
    @Override public Stream<Pair<TagKey<T>, HolderSet.Named<T>>> m_203612_() { return this.holders.getTags(); }
    @Override public HolderSet.Named<T> m_203561_(TagKey<T> name) { return this.holders.getOrCreateTag(name); }
    @Override public Stream<TagKey<T>> m_203613_() { return this.holders.getTagNames(); }
    @Override public Registry<T> m_203521_() { return this.holders.freeze(); }
    @Override public Holder.Reference<T> m_203693_(T value) { return this.holders.createIntrusiveHolder(value); }
    @Override public Optional<HolderSet.Named<T>> m_203431_(TagKey<T> name) { return this.holders.getTag(name); }
    @Override public void m_203652_(Map<TagKey<T>, List<Holder<T>>> newTags) { this.holders.bindTags(newTags); }
    @Override public void m_203635_() { this.holders.resetTags(); }
    @Deprecated @Override public void unfreeze() { this.holders.unfreeze(); }

    /** @deprecated Forge: For internal use only. Use the Register events when registering values. */
    @Deprecated @Override public void lock(){ this.locked = true; }


    public static class Factory<V extends IForgeRegistryEntry<V>> implements IForgeRegistry.CreateCallback<V>, IForgeRegistry.AddCallback<V>
    {
        public static final ResourceLocation ID = new ResourceLocation("forge", "registry_defaulted_wrapper");

        @Override
        public void onCreate(IForgeRegistryInternal<V> owner, RegistryManager stage)
        {
            ForgeRegistry<V> fowner = (ForgeRegistry<V>)owner;
            owner.setSlaveMap(ID, new NamespacedDefaultedWrapper<V>(fowner, fowner.getBuilder().getVanillaHolder()));
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onAdd(IForgeRegistryInternal<V> owner, RegistryManager stage, int id, V value, V oldValue)
        {
            owner.getSlaveMap(ID, NamespacedDefaultedWrapper.class).holders.onAdded(stage, id, value, oldValue);
        }
    }
}
