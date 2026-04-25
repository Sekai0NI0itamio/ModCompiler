/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.component;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.component.Component;
import net.minecraft.component.DataComponentType;
import org.jetbrains.annotations.Nullable;

public interface ComponentMap
extends Iterable<Component<?>> {
    public static final ComponentMap EMPTY = new ComponentMap(){

        @Override
        @Nullable
        public <T> T get(DataComponentType<? extends T> type) {
            return null;
        }

        @Override
        public Set<DataComponentType<?>> getTypes() {
            return Set.of();
        }

        @Override
        public Iterator<Component<?>> iterator() {
            return Collections.emptyIterator();
        }
    };
    public static final Codec<ComponentMap> CODEC = DataComponentType.TYPE_TO_VALUE_MAP_CODEC.flatComapMap(Builder::build, components -> {
        int i = components.size();
        if (i == 0) {
            return DataResult.success(Reference2ObjectMaps.emptyMap());
        }
        Reference2ObjectArrayMap reference2ObjectMap = new Reference2ObjectArrayMap(i);
        for (Component<?> component : components) {
            if (component.type().shouldSkipSerialization()) continue;
            reference2ObjectMap.put(component.type(), component.value());
        }
        return DataResult.success(reference2ObjectMap);
    });

    public static ComponentMap of(final ComponentMap base, final ComponentMap overrides) {
        return new ComponentMap(){

            @Override
            @Nullable
            public <T> T get(DataComponentType<? extends T> type) {
                T object = overrides.get(type);
                if (object != null) {
                    return object;
                }
                return base.get(type);
            }

            @Override
            public Set<DataComponentType<?>> getTypes() {
                return Sets.union(base.getTypes(), overrides.getTypes());
            }
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public <T> T get(DataComponentType<? extends T> var1);

    public Set<DataComponentType<?>> getTypes();

    default public boolean contains(DataComponentType<?> type) {
        return this.get(type) != null;
    }

    default public <T> T getOrDefault(DataComponentType<? extends T> type, T fallback) {
        T object = this.get(type);
        return object != null ? object : fallback;
    }

    @Nullable
    default public <T> Component<T> copy(DataComponentType<T> type) {
        T object = this.get(type);
        return object != null ? new Component<T>(type, object) : null;
    }

    @Override
    default public Iterator<Component<?>> iterator() {
        return Iterators.transform(this.getTypes().iterator(), type -> Objects.requireNonNull(this.copy((DataComponentType)type)));
    }

    default public Stream<Component<?>> stream() {
        return StreamSupport.stream(Spliterators.spliterator(this.iterator(), (long)this.size(), 1345), false);
    }

    default public int size() {
        return this.getTypes().size();
    }

    default public boolean isEmpty() {
        return this.size() == 0;
    }

    default public ComponentMap filtered(final Predicate<DataComponentType<?>> predicate) {
        return new ComponentMap(){

            @Override
            @Nullable
            public <T> T get(DataComponentType<? extends T> type) {
                return predicate.test(type) ? (T)ComponentMap.this.get(type) : null;
            }

            @Override
            public Set<DataComponentType<?>> getTypes() {
                return Sets.filter(ComponentMap.this.getTypes(), predicate::test);
            }
        };
    }

    public static class Builder {
        private final Reference2ObjectMap<DataComponentType<?>, Object> components = new Reference2ObjectArrayMap();

        Builder() {
        }

        public <T> Builder add(DataComponentType<T> type, @Nullable T value) {
            this.put(type, value);
            return this;
        }

        <T> void put(DataComponentType<T> type, @Nullable Object value) {
            if (value != null) {
                this.components.put(type, value);
            } else {
                this.components.remove(type);
            }
        }

        public Builder addAll(ComponentMap componentSet) {
            for (Component<?> component : componentSet) {
                this.components.put(component.type(), component.value());
            }
            return this;
        }

        public ComponentMap build() {
            return Builder.build(this.components);
        }

        private static ComponentMap build(Map<DataComponentType<?>, Object> components) {
            if (components.isEmpty()) {
                return EMPTY;
            }
            if (components.size() < 8) {
                return new SimpleComponentMap(new Reference2ObjectArrayMap(components));
            }
            return new SimpleComponentMap(new Reference2ObjectOpenHashMap(components));
        }

        record SimpleComponentMap(Reference2ObjectMap<DataComponentType<?>, Object> map) implements ComponentMap
        {
            @Override
            @Nullable
            public <T> T get(DataComponentType<? extends T> type) {
                return (T)this.map.get(type);
            }

            @Override
            public boolean contains(DataComponentType<?> type) {
                return this.map.containsKey(type);
            }

            @Override
            public Set<DataComponentType<?>> getTypes() {
                return this.map.keySet();
            }

            @Override
            public Iterator<Component<?>> iterator() {
                return Iterators.transform(Reference2ObjectMaps.fastIterator(this.map), Component::of);
            }

            @Override
            public int size() {
                return this.map.size();
            }

            @Override
            public String toString() {
                return this.map.toString();
            }
        }
    }
}

