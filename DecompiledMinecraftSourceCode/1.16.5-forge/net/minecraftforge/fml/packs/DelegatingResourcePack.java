/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.packs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.IResourcePack;
import net.minecraft.resources.ResourcePack;
import net.minecraft.resources.ResourcePackFileNotFoundException;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.data.IMetadataSectionSerializer;
import net.minecraft.resources.data.PackMetadataSection;
import net.minecraft.util.ResourceLocation;

public class DelegatingResourcePack extends ResourcePack
{

    private final List<IResourcePack> delegates;
    private final Map<String, List<IResourcePack>> namespacesAssets;
    private final Map<String, List<IResourcePack>> namespacesData;

    private final String name;
    private final PackMetadataSection packInfo;

    public DelegatingResourcePack(String id, String name, PackMetadataSection packInfo, List<? extends IResourcePack> packs)
    {
        super(new File(id));
        this.name = name;
        this.packInfo = packInfo;
        this.delegates = ImmutableList.copyOf(packs);
        this.namespacesAssets = this.buildNamespaceMap(ResourcePackType.CLIENT_RESOURCES, delegates);
        this.namespacesData = this.buildNamespaceMap(ResourcePackType.SERVER_DATA, delegates);
    }

    private Map<String, List<IResourcePack>> buildNamespaceMap(ResourcePackType type, List<IResourcePack> packList)
    {
        Map<String, List<IResourcePack>> map = new HashMap<>();
        for (IResourcePack pack : packList)
        {
            for (String namespace : pack.func_195759_a(type))
            {
                map.computeIfAbsent(namespace, k -> new ArrayList<>()).add(pack);
            }
        }
        map.replaceAll((k, list) -> ImmutableList.copyOf(list));
        return ImmutableMap.copyOf(map);
    }

    @Override
    public String func_195762_a()
    {
        return name;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> T func_195760_a(IMetadataSectionSerializer<T> deserializer) throws IOException
    {
        if (deserializer.func_110483_a().equals("pack"))
        {
            return (T) packInfo;
        }
        return null;
    }

    @Override
    public Collection<ResourceLocation> func_225637_a_(ResourcePackType type, String pathIn, String pathIn2, int maxDepth, Predicate<String> filter)
    {
        return delegates.stream()
                .flatMap(r -> r.func_225637_a_(type, pathIn, pathIn2, maxDepth, filter).stream())
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> func_195759_a(ResourcePackType type)
    {
        return type == ResourcePackType.CLIENT_RESOURCES ? namespacesAssets.keySet() : namespacesData.keySet();
    }

    @Override
    public void close()
    {
        for (IResourcePack pack : delegates)
        {
            pack.close();
        }
    }

    @Override
    public InputStream func_195763_b(String fileName) throws IOException
    {
        // root resources do not make sense here
        throw new ResourcePackFileNotFoundException(this.field_195771_a, fileName);
    }

    @Override
    protected InputStream func_195766_a(String resourcePath) throws IOException
    {
        // never called, we override all methods that call this
        throw new ResourcePackFileNotFoundException(this.field_195771_a, resourcePath);
    }

    @Override
    protected boolean func_195768_c(String resourcePath)
    {
        // never called, we override all methods that call this
        return false;
    }

    @Override
    public InputStream func_195761_a(ResourcePackType type, ResourceLocation location) throws IOException
    {
        for (IResourcePack pack : getCandidatePacks(type, location))
        {
            if (pack.func_195764_b(type, location))
            {
                return pack.func_195761_a(type, location);
            }
        }
        throw new ResourcePackFileNotFoundException(this.field_195771_a, getFullPath(type, location));
    }

    @Override
    public boolean func_195764_b(ResourcePackType type, ResourceLocation location)
    {
        for (IResourcePack pack : getCandidatePacks(type, location))
        {
            if (pack.func_195764_b(type, location))
            {
                return true;
            }
        }
        return false;
    }

    private List<IResourcePack> getCandidatePacks(ResourcePackType type, ResourceLocation location)
    {
        Map<String, List<IResourcePack>> map = type == ResourcePackType.CLIENT_RESOURCES ? namespacesAssets : namespacesData;
        List<IResourcePack> packsWithNamespace = map.get(location.func_110624_b());
        return packsWithNamespace == null ? Collections.emptyList() : packsWithNamespace;
    }

    private static String getFullPath(ResourcePackType type, ResourceLocation location)
    {
        // stolen from ResourcePack
        return String.format("%s/%s/%s", type.func_198956_a(), location.func_110624_b(), location.func_110623_a());
    }

}
