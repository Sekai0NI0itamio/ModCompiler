/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.world.biome.Biomes;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.*;
import static net.minecraftforge.common.BiomeDictionary.Type.*;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.common.collect.ImmutableList;

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
        private final Set<RegistryKey<Biome>> biomes = new HashSet<>();
        private final Set<RegistryKey<Biome>> biomesUn = Collections.unmodifiableSet(biomes);

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
         * @return An unmodifiable collection of all current biome types.
         */
        public static Collection<Type> getAll()
        {
            return allTypes;
        }

        @Nullable
        public static Type fromVanilla(Biome.Category category)
        {
            if (category == Biome.Category.NONE)
                return null;
            if (category == Biome.Category.THEEND)
                return VOID;
            return getType(category.name());
        }
    }

    private static final Map<RegistryKey<Biome>, BiomeInfo> biomeInfoMap = new HashMap<>();

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
    public static void addTypes(RegistryKey<Biome> biome, Type... types)
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
    public static Set<RegistryKey<Biome>> getBiomes(Type type)
    {
        return type.biomesUn;
    }

    /**
     * Gets the set of types that have been added to the given biome.
     *
     */
    @Nonnull
    public static Set<Type> getTypes(RegistryKey<Biome> biome)
    {
        return getBiomeInfo(biome).typesUn;
    }

    /**
     * Checks if the two given biomes have types in common.
     *
     * @return returns true if a common type is found, false otherwise
     */
    public static boolean areSimilar(RegistryKey<Biome> biomeA, RegistryKey<Biome> biomeB)
    {
        Set<Type> typesA = getTypes(biomeA);
        Set<Type> typesB = getTypes(biomeB);
        return typesA.stream().anyMatch(typesB::contains);
    }

    /**
     * Checks if the given type has been added to the given biome.
     *
     */
    public static boolean hasType(RegistryKey<Biome> biome, Type type)
    {
        return getTypes(biome).contains(type);
    }

    /**
     * Checks if any type has been added to the given biome.
     *
     */
    public static boolean hasAnyType(RegistryKey<Biome> biome)
    {
        return !getBiomeInfo(biome).types.isEmpty();
    }

    //Internal implementation
    private static BiomeInfo getBiomeInfo(RegistryKey<Biome> biome)
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
        addTypes(Biomes.field_76771_b, OCEAN, OVERWORLD);
        addTypes(Biomes.field_76772_c, PLAINS, OVERWORLD);
        addTypes(Biomes.field_76769_d, HOT, DRY, SANDY, OVERWORLD);
        addTypes(Biomes.field_76770_e, MOUNTAIN, HILLS, OVERWORLD);
        addTypes(Biomes.field_76767_f, FOREST, OVERWORLD);
        addTypes(Biomes.field_76768_g, COLD, CONIFEROUS, FOREST, OVERWORLD);
        addTypes(Biomes.field_76780_h, WET, SWAMP, OVERWORLD);
        addTypes(Biomes.field_76781_i, RIVER, OVERWORLD);
        addTypes(Biomes.field_235254_j_, HOT, DRY, NETHER);
        addTypes(Biomes.field_76779_k, COLD, DRY, END);
        addTypes(Biomes.field_76776_l, COLD, OCEAN, SNOWY, OVERWORLD);
        addTypes(Biomes.field_76777_m, COLD, RIVER, SNOWY, OVERWORLD);
        addTypes(Biomes.field_76774_n, COLD, SNOWY, WASTELAND, OVERWORLD);
        addTypes(Biomes.field_76775_o, COLD, SNOWY, MOUNTAIN, OVERWORLD);
        addTypes(Biomes.field_76789_p, MUSHROOM, RARE, OVERWORLD);
        addTypes(Biomes.field_76788_q, MUSHROOM, BEACH, RARE, OVERWORLD);
        addTypes(Biomes.field_76787_r, BEACH, OVERWORLD);
        addTypes(Biomes.field_76786_s, HOT, DRY, SANDY, HILLS, OVERWORLD);
        addTypes(Biomes.field_76785_t, FOREST, HILLS, OVERWORLD);
        addTypes(Biomes.field_76784_u, COLD, CONIFEROUS, FOREST, HILLS, OVERWORLD);
        addTypes(Biomes.field_76783_v, MOUNTAIN, OVERWORLD);
        addTypes(Biomes.field_76782_w, HOT, WET, DENSE, JUNGLE, OVERWORLD);
        addTypes(Biomes.field_76792_x, HOT, WET, DENSE, JUNGLE, HILLS, OVERWORLD);
        addTypes(Biomes.field_150574_L, HOT, WET, JUNGLE, FOREST, RARE, OVERWORLD);
        addTypes(Biomes.field_150575_M, OCEAN, OVERWORLD);
        addTypes(Biomes.field_150576_N, BEACH, OVERWORLD);
        addTypes(Biomes.field_150577_O, COLD, BEACH, SNOWY, OVERWORLD);
        addTypes(Biomes.field_150583_P, FOREST, OVERWORLD);
        addTypes(Biomes.field_150582_Q, FOREST, HILLS, OVERWORLD);
        addTypes(Biomes.field_150585_R, SPOOKY, DENSE, FOREST, OVERWORLD);
        addTypes(Biomes.field_150584_S, COLD, CONIFEROUS, FOREST, SNOWY, OVERWORLD);
        addTypes(Biomes.field_150579_T, COLD, CONIFEROUS, FOREST, SNOWY, HILLS, OVERWORLD);
        addTypes(Biomes.field_150578_U, COLD, CONIFEROUS, FOREST, OVERWORLD);
        addTypes(Biomes.field_150581_V, COLD, CONIFEROUS, FOREST, HILLS, OVERWORLD);
        addTypes(Biomes.field_150580_W, MOUNTAIN, FOREST, SPARSE, OVERWORLD);
        addTypes(Biomes.field_150588_X, HOT, SAVANNA, PLAINS, SPARSE, OVERWORLD);
        addTypes(Biomes.field_150587_Y, HOT, SAVANNA, PLAINS, SPARSE, RARE, OVERWORLD, PLATEAU);
        addTypes(Biomes.field_150589_Z, MESA, SANDY, DRY, OVERWORLD);
        addTypes(Biomes.field_150607_aa, MESA, SANDY, DRY, SPARSE, OVERWORLD, PLATEAU);
        addTypes(Biomes.field_150608_ab, MESA, SANDY, DRY, OVERWORLD, PLATEAU);
        addTypes(Biomes.field_201936_P, END);
        addTypes(Biomes.field_201937_Q, END);
        addTypes(Biomes.field_201938_R, END);
        addTypes(Biomes.field_201939_S, END);
        addTypes(Biomes.field_203614_T, OCEAN, HOT, OVERWORLD);
        addTypes(Biomes.field_203615_U, OCEAN, OVERWORLD);
        addTypes(Biomes.field_203616_V, OCEAN, COLD, OVERWORLD);
        addTypes(Biomes.field_203617_W, OCEAN, HOT, OVERWORLD);
        addTypes(Biomes.field_203618_X, OCEAN, OVERWORLD);
        addTypes(Biomes.field_203619_Y, OCEAN, COLD, OVERWORLD);
        addTypes(Biomes.field_203620_Z, OCEAN, COLD, OVERWORLD);
        addTypes(Biomes.field_185440_P, VOID);
        addTypes(Biomes.field_185441_Q, PLAINS, RARE, OVERWORLD);
        addTypes(Biomes.field_185442_R, HOT, DRY, SANDY, RARE, OVERWORLD);
        addTypes(Biomes.field_185443_S, MOUNTAIN, SPARSE, RARE, OVERWORLD);
        addTypes(Biomes.field_185444_T, FOREST, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.field_150590_f, COLD, CONIFEROUS, FOREST, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.field_150599_m, WET, SWAMP, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.field_185445_W, COLD, SNOWY, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.field_185446_X, HOT, WET, DENSE, JUNGLE, MOUNTAIN, RARE, OVERWORLD, MODIFIED);
        addTypes(Biomes.field_185447_Y, HOT, SPARSE, JUNGLE, HILLS, RARE, OVERWORLD, MODIFIED);
        addTypes(Biomes.field_185448_Z, FOREST, DENSE, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.field_185429_aa, FOREST, DENSE, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.field_185430_ab, SPOOKY, DENSE, FOREST, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.field_185431_ac, COLD, CONIFEROUS, FOREST, SNOWY, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.field_185432_ad, DENSE, FOREST, RARE, OVERWORLD);
        addTypes(Biomes.field_185433_ae, DENSE, FOREST, HILLS, RARE, OVERWORLD);
        addTypes(Biomes.field_185434_af, MOUNTAIN, SPARSE, RARE, OVERWORLD, MODIFIED);
        addTypes(Biomes.field_185435_ag, HOT, DRY, SPARSE, SAVANNA, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.field_185436_ah, HOT, DRY, SPARSE, SAVANNA, HILLS, RARE, OVERWORLD, PLATEAU);
        addTypes(Biomes.field_185437_ai, HOT, DRY, SPARSE, MOUNTAIN, RARE, OVERWORLD);
        addTypes(Biomes.field_185438_aj, HOT, DRY, SPARSE, HILLS, RARE, OVERWORLD, PLATEAU, MODIFIED);
        addTypes(Biomes.field_185439_ak, HOT, DRY, SPARSE, MOUNTAIN, RARE, OVERWORLD, PLATEAU, MODIFIED);
        addTypes(Biomes.field_222370_aw, HOT, WET, RARE, JUNGLE, OVERWORLD);
        addTypes(Biomes.field_222371_ax, HOT, WET, RARE, JUNGLE, HILLS, OVERWORLD);
        addTypes(Biomes.field_235252_ay_, HOT, DRY, NETHER);
        addTypes(Biomes.field_235253_az_, HOT, DRY, NETHER, FOREST);
        addTypes(Biomes.field_235250_aA_, HOT, DRY, NETHER, FOREST);
        addTypes(Biomes.field_235251_aB_, HOT, DRY, NETHER);

        if (DEBUG)
        {
            StringBuilder buf = new StringBuilder();
            buf.append("BiomeDictionary:\n");
            Type.byName.forEach((name, type) ->
                buf.append("    ").append(type.name).append(": ")
                .append(type.biomes.stream()
                    .map(RegistryKey::func_240901_a_)
                    .sorted((a,b) -> a.compareNamespaced(b))
                    .map(Object::toString)
                    .collect(Collectors.joining(", "))
                )
                .append('\n')
            );

            boolean missing = false;
            List<RegistryKey<Biome>> all = StreamSupport.stream(ForgeRegistries.BIOMES.spliterator(), false)
                .map(b -> RegistryKey.func_240903_a_(Registry.field_239720_u_, b.getRegistryName()))
                .sorted().collect(Collectors.toList());

            for (RegistryKey<Biome> key : all) {
                if (!biomeInfoMap.containsKey(key)) {
                    if (!missing) {
                        buf.append("Missing:\n");
                        missing = true;
                    }
                    buf.append("    ").append(key.func_240901_a_()).append('\n');
                }
            }
            LOGGER.debug(buf.toString());
        }
    }
}
