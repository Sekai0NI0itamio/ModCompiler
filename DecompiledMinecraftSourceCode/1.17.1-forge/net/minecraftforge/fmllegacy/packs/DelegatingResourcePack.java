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

package net.minecraftforge.fmllegacy.packs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.ResourcePackFileNotFoundException;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.resources.ResourceLocation;

public class DelegatingResourcePack extends AbstractPackResources
{

    private final List<PackResources> delegates;
    private final Map<String, List<PackResources>> namespacesAssets;
    private final Map<String, List<PackResources>> namespacesData;

    private final String name;
    private final PackMetadataSection packInfo;

    public DelegatingResourcePack(String id, String name, PackMetadataSection packInfo, List<? extends PackResources> packs)
    {
        super(new File(id));
        this.name = name;
        this.packInfo = packInfo;
        this.delegates = ImmutableList.copyOf(packs);
        this.namespacesAssets = this.buildNamespaceMap(PackType.CLIENT_RESOURCES, delegates);
        this.namespacesData = this.buildNamespaceMap(PackType.SERVER_DATA, delegates);
    }

    private Map<String, List<PackResources>> buildNamespaceMap(PackType type, List<PackResources> packList)
    {
        Map<String, List<PackResources>> map = new HashMap<>();
        for (PackResources pack : packList)
        {
            for (String namespace : pack.m_5698_(type))
            {
                map.computeIfAbsent(namespace, k -> new ArrayList<>()).add(pack);
            }
        }
        map.replaceAll((k, list) -> ImmutableList.copyOf(list));
        return ImmutableMap.copyOf(map);
    }

    @Override
    public String m_8017_()
    {
        return name;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> T m_5550_(MetadataSectionSerializer<T> deserializer) throws IOException
    {
        if (deserializer.m_7991_().equals("pack"))
        {
            return (T) packInfo;
        }
        return null;
    }

    @Override
    public Collection<ResourceLocation> m_7466_(PackType type, String pathIn, String pathIn2, int maxDepth, Predicate<String> filter)
    {
        return delegates.stream()
                .flatMap(r -> r.m_7466_(type, pathIn, pathIn2, maxDepth, filter).stream())
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> m_5698_(PackType type)
    {
        return type == PackType.CLIENT_RESOURCES ? namespacesAssets.keySet() : namespacesData.keySet();
    }

    @Override
    public void close()
    {
        for (PackResources pack : delegates)
        {
            pack.close();
        }
    }

    @Override
    public InputStream m_5542_(String fileName) throws IOException
    {
        // root resources do not make sense here
        throw new ResourcePackFileNotFoundException(this.f_10203_, fileName);
    }

    @Override
    protected InputStream m_5541_(String resourcePath) throws IOException
    {
        // never called, we override all methods that call this
        throw new ResourcePackFileNotFoundException(this.f_10203_, resourcePath);
    }

    @Override
    protected boolean m_6105_(String resourcePath)
    {
        // never called, we override all methods that call this
        return false;
    }

    @Override
    public InputStream m_8031_(PackType type, ResourceLocation location) throws IOException
    {
        for (PackResources pack : getCandidatePacks(type, location))
        {
            if (pack.m_7211_(type, location))
            {
                return pack.m_8031_(type, location);
            }
        }
        throw new ResourcePackFileNotFoundException(this.f_10203_, getFullPath(type, location));
    }

    @Override
    public boolean m_7211_(PackType type, ResourceLocation location)
    {
        for (PackResources pack : getCandidatePacks(type, location))
        {
            if (pack.m_7211_(type, location))
            {
                return true;
            }
        }
        return false;
    }

    private List<PackResources> getCandidatePacks(PackType type, ResourceLocation location)
    {
        Map<String, List<PackResources>> map = type == PackType.CLIENT_RESOURCES ? namespacesAssets : namespacesData;
        List<PackResources> packsWithNamespace = map.get(location.m_135827_());
        return packsWithNamespace == null ? Collections.emptyList() : packsWithNamespace;
    }

    private static String getFullPath(PackType type, ResourceLocation location)
    {
        // stolen from ResourcePack
        return String.format("%s/%s/%s", type.m_10305_(), location.m_135827_(), location.m_135815_());
    }

}
