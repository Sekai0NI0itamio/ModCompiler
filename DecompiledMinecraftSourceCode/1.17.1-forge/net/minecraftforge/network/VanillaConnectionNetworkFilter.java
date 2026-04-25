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

package net.minecraftforge.network;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import io.netty.channel.ChannelHandler;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagCollection;
import net.minecraftforge.common.ForgeTagHandler;
import net.minecraftforge.fmllegacy.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.tree.RootCommandNode;

/**
 * A filter for network packets, used to filter/modify parts of vanilla network messages that
 * will cause errors or warnings on vanilla clients, for example entity attributes that are added by Forge or mods.
 */
@ChannelHandler.Sharable
public class VanillaConnectionNetworkFilter extends VanillaPacketFilter
{

    private static final Logger LOGGER = LogManager.getLogger();

    public VanillaConnectionNetworkFilter()
    {
        super(
                ImmutableMap.<Class<? extends Packet<?>>, BiConsumer<Packet<?>, List<? super Packet<?>>>>builder()
                .put(handler(ClientboundUpdateAttributesPacket.class, VanillaConnectionNetworkFilter::filterEntityProperties))
                .put(handler(ClientboundCommandsPacket.class, VanillaConnectionNetworkFilter::filterCommandList))
                .put(handler(ClientboundUpdateTagsPacket.class, VanillaConnectionNetworkFilter::filterCustomTagTypes))
                .build()
        );
    }

    @Override
    protected boolean isNecessary(Connection manager)
    {
        return NetworkHooks.isVanillaConnection(manager);
    }

    /**
     * Filter for SEntityPropertiesPacket. Filters out any entity attributes that are not in the "minecraft" namespace.
     * A vanilla client would ignore these with an error log.
     */
    @Nonnull
    private static ClientboundUpdateAttributesPacket filterEntityProperties(ClientboundUpdateAttributesPacket msg)
    {
        ClientboundUpdateAttributesPacket newPacket = new ClientboundUpdateAttributesPacket(msg.m_133588_(), Collections.emptyList());
        msg.m_133591_().stream()
                .filter(snapshot -> {
                    ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(snapshot.m_133601_());
                    return key != null && key.m_135827_().equals("minecraft");
                })
                .forEach(snapshot -> newPacket.m_133591_().add(snapshot));
        return newPacket;
    }

    /**
     * Filter for SCommandListPacket. Uses {@link CommandTreeCleaner} to filter out any ArgumentTypes that are not in the "minecraft" or "brigadier" namespace.
     * A vanilla client would fail to deserialize the packet and disconnect with an error message if these were sent.
     */
    @Nonnull
    private static ClientboundCommandsPacket filterCommandList(ClientboundCommandsPacket packet)
    {
        RootCommandNode<SharedSuggestionProvider> root = packet.m_131884_();
        RootCommandNode<SharedSuggestionProvider> newRoot = CommandTreeCleaner.cleanArgumentTypes(root, argType -> {
            ResourceLocation id = ArgumentTypes.getId(argType);
            return id != null && (id.m_135827_().equals("minecraft") || id.m_135827_().equals("brigadier"));
        });
        return new ClientboundCommandsPacket(newRoot);
    }

    /**
     * Filters out custom tag types that the vanilla client won't recognize.
     * It prevents a rare error from logging and reduces the packet size
     */
    private static ClientboundUpdateTagsPacket filterCustomTagTypes(ClientboundUpdateTagsPacket packet) {
        Map<ResourceKey<? extends Registry<?>>, TagCollection.NetworkPayload> tags = packet.m_179482_()
                .entrySet().stream().filter(e -> !ForgeTagHandler.getCustomTagTypeNames().contains(e.getKey().m_135782_()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new ClientboundUpdateTagsPacket(tags);
    }
}
