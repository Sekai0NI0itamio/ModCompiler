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

package net.minecraftforge.common;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.world.level.biome.Biomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import static net.minecraftforge.common.BiomeDictionary.Type.*;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.level.biome.Biome;

public class BiomeDictionary
{
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = LogManager.getLogger();

    public static final class Type
    {
        private static final Map<String, Type> byName = new TreeMap<>();
        private static Collection<Type> allTypes = Collections.unmodifiableCollection(byName.values());

        /*Temperature-based tags. Specifying neither implies a biome is temperate*/
        public static final Type HOT = new Type("HOT");
        public static final Type COLD = new Type("COLD");

        //Tags specifying the amount of vegetation a biome has. Specifying neither implies a biome to have moderate amounts*/
        public static final Type SPARSE = new Type("SPARSE");
        public static final Type DENSE = new Type("DENSE");

        //Tags specifying how moist a biome is. Specifying neither implies the biome as having moderate humidity*/
        public static final Type WET = new Type("WET");
        public static final Type DRY = new Type("DRY");

        /*Tree-based tags, SAVANNA refers to dry, desert-like trees (Such as Acacia), CONIFEROUS refers to snowy trees (Such as Spruce) and JUNGLE refers to jungle trees.
         * Specifying no tag implies a biome has temperate trees (Such as Oak)*/
        public static final Type SAVANNA = new Type("SAVANNA");
        public static final Type CONIFEROUS = new Type("CONIFEROUS");
        public static final Type JUNGLE = new Type("JUNGLE");

        /*Tags specifying the nature of a biome*/
        public static final Type SPOOKY = new Type("SPOOKY");
        public static final Type DEAD = new Type("DEAD");
        public static final Type LUSH = new Type("LUSH");
        public static final Type MUSHROOM = new Type("MUSHROOM");
        public static final Type MAGICAL = new Type("MAGICAL");
        public static final Type RARE = new Type("RARE");
        public static final Type PLATEAU = new Type("PLATEAU");
        public static final Type MODIFIED = new Type("MODIFIED");

        public static final Type OCEAN = new Type("OCEAN");
        public static final Type RIVER = new Type("RIVER");
        /**
         * A general tag for all water-based biomes. Shown as present if OCEAN or RIVER are.
         **/
        public static final Type WATER = new Type("WATER", OCEAN, RIVER);

        /*Generic types which a biome can be*/
        public static final Type MESA = new Type("MESA");
        public static final Type FOREST = new Type("FOREST");
        public static final Type PLAINS = new Type("PLAINS");
        public static final Type MOUNTAIN = new Type("MOUNTAIN");
        public static final Type HILLS = new Type("HILLS");
        public static final Type SWAMP = new Type("SWAMP");
        public static final Type SANDY = new Type("SANDY");
        public static final Type SNOWY = new Type("SNOWY");
        public static final Type WASTELAND = new Type("WASTELAND");
        public static final Type BEACH = new Type("BEACH");
        public static final Type VOID = new Type("VOID");

        /*Tags specifying the dimension a biome generates in. Specifying none implies a biome that generates in a modded dimension*/
        public static final Type OVERWORLD = new Type("OVERWORLD");
        public static final Type NETHER = new Type("NETHER");
        public static final Type END = new Type("END");

        private final String name;
        private final List<Type> subTypes;
        private final Set<ResourceKey<Biome>> biomes = new HashSet<>();
        private final Set<ResourceKey<Biome>> biomesUn = Collections.unmodifiableSet(biomes);

        private Type(String name, Type... subTypes)
        {
            this.name = name;
            this.subTypes = ImmutableList.copyOf(subTypes);

            byName.put(name, this);
        }

        /**
         * Gets the name for this type.
         */
        public String getName()
        {
            return name;
        }

        public String toString()
        {
            return name;
        }

        /**
         * Retrieves a Type instance by name,
         * if one does not exist already it creates one.
         * This can be used as intermediate measure for modders to
         * add their own Biome types.
         * <p>
         * There are <i>no</i> naming conventions besides:
         * <ul><li><b>Must</b> be all upper case (enforced by name.toUpper())</li>
         * <li><b>No</b> Special characters. {Unenforced, just don't be a pain, if it becomes a issue I WILL
         * make this RTE with no worry about backwards compatibility}</li></ul>
         * <p>
         * Note: For performance sake, the return value of this function SHOULD be cached.
         * Two calls with the same name SHOULD return the same value.
         *
         * @param name The name of this Type
         * @return An instance of Type for this name.
         */
        public static Type getType(String name, Type... subTypes)
        {
            name = name.toUpperCase();
            Type t = byName.get(name);
            if (t == null)
            {
                t = new Type(name, subTypes);
            }
            return t;
        }

        /**
         * Checks if a type instance exists for a given name. Does not have any side effects if a type does not already exist.
         * This can be used for checking if a user-defined type is valid, for example, in a codec which accepts biome dictionary names.
         * @param name The name.
         * @return {@code true} if a type exists with this name.
         *
         * @see #getType(String, Type...) #getType for type naming conventions.
         */
        public static boolean hasType(String name)
        {
            return byName.containsKey(name.toUpperCase());
        }

        /**
         * @return An unmodifiable collection of all current biome types.
         */
        public static Collection<Type> getAll()
        {
            return allTypes;
        }

        @Nullable
        public static Type fromVanilla(Biome.BiomeCategory category)
        {
            if (category == Biome.BiomeCategory.NONE)
                return null;
            if (category == Biome.BiomeCategory.THEEND)
                return VOID;
            return getType(category.name());
        }
    }

    private static final Map<ResourceKey<Biome>, BiomeInfo> biomeInfoMap = new HashMap<>();

    private static class BiomeInfo
    {
        private final Set<Type> types = new HashSet<Type>();
        private final Set<Type> typesUn = Collections.unmodifiableSet(this.types);
    }

    public static void init() {}
    static
    {
        registerVanillaBiomes();
    }

    /**
     * Adds the given types to the biome.
     *
     */
    public static void addTypes(ResourceKey<Biome> biome, Type... types)
    {
        Collection<Type> supertypes = listSupertypes(types);
        Collections.addAll(supertypes, types);

        for (Type type : supertypes)
        {
            type.biomes.add(biome);
        }

        BiomeInfo biomeInfo = getBiomeInfo(biome);
        Collections.addAll(biomeInfo.types, types);
        biomeInfo.types.addAll(supertypes);
    }

    /**
     * Gets the set of biomes that have the given type.
     *
     */
    @Nonnull
    public static Set<ResourceKey<Biome>> getBiomes(Type type)
    {
        return type.biomesUn;
    }

    /**
     * Gets the set of types that have been added to the given biome.
     *
     */
    @Nonnull
    public static Set<Type> getTypes(ResourceKey<Biome> biome)
    {
        return getBiomeInfo(biome).typesUn;
    }

    /**
     * Checks if the two given biomes have types in common.
     *
     * @return returns true if a common type is found, false otherwise
     */
    public static boolean areSimilar(ResourceKey<Biome> biomeA, ResourceKey<Biome> biomeB)
    {
        Set<Type> typesA = getTypes(biomeA);
        Set<Type> typesB = getTypes(biomeB);
        return typesA.stream().anyMatch(typesB::contains);
    }

    /**
     * Checks if the given type has been added to the given biome.
     *
     */
    public static boolean hasType(ResourceKey<Biome> biome, Type type)
    {
        return getTypes(biome).contains(type);
    }

    /**
     * Checks if any type has been added to the given biome.
     *
     */
    public static boolean hasAnyType(ResourceKey<Biome> biome)
    {
        return !getBiomeInfo(biome).types.isEmpty();
    }

    //Internal implementation
    private static BiomeInfo getBiomeInfo(ResourceKey<Biome> biome)
    {
        return biomeInfoMap.computeIfAbsent(biome, k -> new BiomeInfo());
    }

    private static Collection<Type> listSupertypes(Type... types)
    {
        Set<Type> supertypes = new HashSet<Type>();
        Deque<Type> next = new ArrayDeque<Type>();
        Collections.addAll(next, types);

        while (!next.isEmpty())
        {
            Type type = next.remove();

            for (Type sType : Type.byName.values())
            {
                if (sType.subTypes.contains(type) && supertypes.add(sType))
                    next.add(sType);
            }
        }

        return supertypes;
    }

    private static void registerVanillaBiomes()
    {
        addTypes(Biomes.f_48174_, OCEAN, OVERWORLD);
        addTypes(Biomes.f_48202_, PLAINS, OVERWORLD);
        addTypes(Biomes.f_48203_, HOT, DRY, SANDY, OVERWORLD);
        addTypes(Biomes.f_48204_, MOUNTAIN, HILLS, OVERWORLD);
        addTypes(Biomes.f_48205_, FOREST, OVERWORLD);
        addTypes(Biomes.f_48206_, COLD, CONIFEROUS, FOREST, OVERWORLD);
        addTypes(Biomes.f_48207_, WET, SWAMP, OVERWORLD);
        addTypes(Biomes.f_48208_, RIVER, OVERWORLD);
        addTypes(Biomes.f_48209_, HOT, DRY, NETHER);
        addTypes(Biomes.f_48210_, COLD, DRY, END);
        addTypes(Biomes.f_48211_, COLD, OCEAN, SNOWY, OVERWORLD);
        addTypes(Biomes.f_48212_, COLD, RIVER, SNOWY, OVERWORLD);
        addTypes(Biomes.f_48213_, COLD, SNOWY, WASTELAND, OVERWORLD);
        addTypes(Biomes.f_48214_, COLD, SNOWY, MOUNTAIN, OVERWORLD);
        addTypes(Biomes.f_48215_, MUSHROOM, RARE, OVERWORLD);
        addTypes(Biomes.f_48216_, MUSHROOM, BEACH, RARE, OVERWORLD);
        addTypes(Biomes.f_48217_, BEACH, OVERWORLD);
        addTypes(Biomes.f_48218_, HOT, DRY, SANDY, HILLS, OVERWORLD);
        addTypes(Biomes.f_48219_, FOREST, HILLS, OVERWORLD);
        addTypes(Biomes.f_48220_, COLD, CONIFEROUS, FOREST, HILLS, OVERWORLD);
        addTypes(Biomes.f_48221_, MOUNTAIN, OVERWORLD);
        addTypes(Biomes.f_48222_, HOT, WET, DENSE, JUNGLE, OVERWORLD);
        addTypes(Biomes.f_48223_, HOT, WET, DENSE, JUNGLE, HILLS, OVERWORLD);
        addTypes(Biomes.f_48224_, HOT, WET, JUNGLE, FOREST, RARE, OVERWORLD);
        addTypes(Biomes.f_48225_, OCEAN, OVERWORLD);
        addTypes(Biomes.f_48226_, BEACH, OVERWORLD);
        addTypes(Biomes.f_48148_, COLD, BEACH, SNOWY, OVERWORLD);
        addTypes(Biomes.f_48149_, FOREST, OVERWORLD);
        addTypes(Biomes.f_48150_, FOREST, HILLS, OVERWORLD);
        addTypes(Biomes.f_48151_, SPOOKY, DENSE, FOREST, OVERWORLD);
        addTypes(Biomes.f_48152_, COLD, CONIFEROUS, FOREST, SNOWY, OVERWORLD);
        addTypes(Biomes.f_48153_, COLD, CONIFEROUS, FOREST, SNOWY, HILLS, OVERWORLD);
        addTypes(Biomes.f_48154_, COLD, CONIFEROUS, FOREST, OVERWORLD);
        addTypes(Biomes.f_48155_, COLD, CONIFEROUS, FOREST, HILLS, OVERWORLD);
        addTypes(Biomes.f_48156_, MOUNTAIN, FOREST, SPARSE, OVERWORLD);
        addTypes(Biomes.f_48157_, HOT, SAVANNA, PLAINS, SPARSE, OVERWORLD);
        addTypes(Biomes.f_48158_, HOT, SAVANNA, PLAINS, SPARSE, RARE, OVERWORLD, PLATEAU);
        addTypes(Biomes.f_48159_, MESA, SANDY, DRY, OVERWORLD);
        addTypes(Biomes.f_48160_, MESA, SANDY, DRY, SPARSE, OVERWORLD, PLATEAU);
        addTypes(Biomes.f_48161_, MESA, SANDY, DRY, OVERWORLD, PLATEAU);
        addTypes(Biomes.f_48162_, END);
        addTypes(Biomes.f_48163_, END);
        addTypes(Biomes.f_48164_, END);
        addTypes(Biomes.f_48165_, END);
        addTypes(Biomes.f_48166_, OCEAN, HOT, OVERWORLD);
        addTypes(Biomes.f_48167_, OCEAN, OVERWORLD);
        addTypes(Biomes.f_48168_, OCEAN, COLD, OVERWORLD);
        addTypes(Biomes.f_48169_, OCEAN, HOT, OVERWORLD);
        addTypes(Biomes.f_48170_, OCEAN, OVERWORLD);
        addTypes(Biomes.f_48171_, OCEAN, COLD, OVERWORLD);
        addTypes(Biomes.f_48172_, OCEAN, COLD, OVERWORLD);
        addTypes(Biomes.f_48173_, VOID);
        addTypes(Biomes.f_48176_, PLAINS, RARE, OVERWORLD);
        addTypes(Biomes.f_48177_, HOT, DRY, SANDY, RARE, OVERWORLD);
        addTypes(Biomes.f_48178_, MOUNTAIN, SPARSE, RARE, OVERWORLD);
        addTypes(Biomes.f_48179_, FOREST, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.f_48180_, COLD, CONIFEROUS, FOREST, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.f_48181_, WET, SWAMP, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.f_48182_, COLD, SNOWY, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.f_48183_, HOT, WET, DENSE, JUNGLE, MOUNTAIN, RARE, OVERWORLD, MODIFIED);
        addTypes(Biomes.f_48184_, HOT, SPARSE, JUNGLE, HILLS, RARE, OVERWORLD, MODIFIED);
        addTypes(Biomes.f_48185_, FOREST, DENSE, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.f_48186_, FOREST, DENSE, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.f_48187_, SPOOKY, DENSE, FOREST, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.f_48188_, COLD, CONIFEROUS, FOREST, SNOWY, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.f_48189_, DENSE, FOREST, RARE, OVERWORLD);
        addTypes(Biomes.f_48190_, DENSE, FOREST, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.f_48191_, MOUNTAIN, SPARSE, RARE, OVERWORLD, MODIFIED);
        addTypes(Biomes.f_48192_, HOT, DRY, SPARSE, SAVANNA, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.f_48193_, HOT, DRY, SPARSE, SAVANNA, HILLS, RARE, OVERWORLD, PLATEAU);
        addTypes(Biomes.f_48194_, HOT, DRY, SPARSE, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.f_48195_, HOT, DRY, SPARSE, HILLS, RARE, OVERWORLD, PLATEAU, MODIFIED);
        addTypes(Biomes.f_48196_, HOT, DRY, SPARSE, MOUNTAIN, RARE, OVERWORLD, PLATEAU, MODIFIED);
        addTypes(Biomes.f_48197_, HOT, WET, RARE, JUNGLE, OVERWORLD);
        addTypes(Biomes.f_48198_, HOT, WET, RARE, JUNGLE, HILLS, OVERWORLD);
        addTypes(Biomes.f_48199_, HOT, DRY, NETHER);
        addTypes(Biomes.f_48200_, HOT, DRY, NETHER, FOREST);
        addTypes(Biomes.f_48201_, HOT, DRY, NETHER, FOREST);
        addTypes(Biomes.f_48175_, HOT, DRY, NETHER);

        if (DEBUG)
        {
            StringBuilder buf = new StringBuilder();
            buf.append("BiomeDictionary:\n");
            Type.byName.forEach((name, type) ->
                buf.append("    ").append(type.name).append(": ")
                .append(type.biomes.stream()
                    .map(ResourceKey::m_135782_)
                    .sorted((a,b) -> a.compareNamespaced(b))
                    .map(Object::toString)
                    .collect(Collectors.joining(", "))
                )
                .append('\n')
            );

            boolean missing = false;
            List<ResourceKey<Biome>> all = StreamSupport.stream(ForgeRegistries.BIOMES.spliterator(), false)
                .map(b -> ResourceKey.m_135785_(Registry.f_122885_, b.getRegistryName()))
                .sorted().collect(Collectors.toList());

            for (ResourceKey<Biome> key : all) {
                if (!biomeInfoMap.containsKey(key)) {
                    if (!missing) {
                        buf.append("Missing:\n");
                        missing = true;
                    }
                    buf.append("    ").append(key.m_135782_()).append('\n');
                }
            }
            LOGGER.debug(buf.toString());
        }
    }
}
