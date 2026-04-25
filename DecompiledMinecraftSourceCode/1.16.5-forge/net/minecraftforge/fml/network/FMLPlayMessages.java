/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.network;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.IHasContainer;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ITagCollection;
import net.minecraft.tags.ITagCollectionSupplier;
import net.minecraft.tags.TagRegistryManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeTagHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.RegistryManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FMLPlayMessages
{
    /**
     * Used to spawn a custom entity without the same restrictions as
     * {@link net.minecraft.network.play.server.SSpawnObjectPacket} or {@link net.minecraft.network.play.server.SSpawnMobPacket}
     *
     * To customize how your entity is created clientside (instead of using the default factory provided to the {@link EntityType})
     * see {@link EntityType.Builder#setCustomClientFactory}.
     */
    public static class SpawnEntity
    {
        private final Entity entity;
        private final int typeId;
        private final int entityId;
        private final UUID uuid;
        private final double posX, posY, posZ;
        private final byte pitch, yaw, headYaw;
        private final int velX, velY, velZ;
        private final PacketBuffer buf;

        SpawnEntity(Entity e)
        {
            this.entity = e;
            this.typeId = Registry.field_212629_r.func_148757_b(e.func_200600_R()); //TODO: Codecs
            this.entityId = e.func_145782_y();
            this.uuid = e.func_110124_au();
            this.posX = e.func_226277_ct_();
            this.posY = e.func_226278_cu_();
            this.posZ = e.func_226281_cx_();
            this.pitch = (byte) MathHelper.func_76141_d(e.field_70125_A * 256.0F / 360.0F);
            this.yaw = (byte) MathHelper.func_76141_d(e.field_70177_z * 256.0F / 360.0F);
            this.headYaw = (byte) (e.func_70079_am() * 256.0F / 360.0F);
            Vector3d vec3d = e.func_213322_ci();
            double d1 = MathHelper.func_151237_a(vec3d.field_72450_a, -3.9D, 3.9D);
            double d2 = MathHelper.func_151237_a(vec3d.field_72448_b, -3.9D, 3.9D);
            double d3 = MathHelper.func_151237_a(vec3d.field_72449_c, -3.9D, 3.9D);
            this.velX = (int)(d1 * 8000.0D);
            this.velY = (int)(d2 * 8000.0D);
            this.velZ = (int)(d3 * 8000.0D);
            this.buf = null;
        }

        private SpawnEntity(int typeId, int entityId, UUID uuid, double posX, double posY, double posZ,
                byte pitch, byte yaw, byte headYaw, int velX, int velY, int velZ, PacketBuffer buf)
        {
            this.entity = null;
            this.typeId = typeId;
            this.entityId = entityId;
            this.uuid = uuid;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.pitch = pitch;
            this.yaw = yaw;
            this.headYaw = headYaw;
            this.velX = velX;
            this.velY = velY;
            this.velZ = velZ;
            this.buf = buf;
        }

        public static void encode(SpawnEntity msg, PacketBuffer buf)
        {
            buf.func_150787_b(msg.typeId);
            buf.writeInt(msg.entityId);
            buf.writeLong(msg.uuid.getMostSignificantBits());
            buf.writeLong(msg.uuid.getLeastSignificantBits());
            buf.writeDouble(msg.posX);
            buf.writeDouble(msg.posY);
            buf.writeDouble(msg.posZ);
            buf.writeByte(msg.pitch);
            buf.writeByte(msg.yaw);
            buf.writeByte(msg.headYaw);
            buf.writeShort(msg.velX);
            buf.writeShort(msg.velY);
            buf.writeShort(msg.velZ);
            if (msg.entity instanceof IEntityAdditionalSpawnData)
            {
                ((IEntityAdditionalSpawnData) msg.entity).writeSpawnData(buf);
            }
        }

        public static SpawnEntity decode(PacketBuffer buf)
        {
            return new SpawnEntity(
                    buf.func_150792_a(),
                    buf.readInt(),
                    new UUID(buf.readLong(), buf.readLong()),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readByte(), buf.readByte(), buf.readByte(),
                    buf.readShort(), buf.readShort(), buf.readShort(),
                    buf
                    );
        }

        public static void handle(SpawnEntity msg, Supplier<NetworkEvent.Context> ctx)
        {
            ctx.get().enqueueWork(() -> {
                EntityType<?> type = Registry.field_212629_r.func_148745_a(msg.typeId);
                if (type == null)
                {
                    throw new RuntimeException(String.format("Could not spawn entity (id %d) with unknown type at (%f, %f, %f)", msg.entityId, msg.posX, msg.posY, msg.posZ));
                }

                Optional<World> world = LogicalSidedProvider.CLIENTWORLD.get(ctx.get().getDirection().getReceptionSide());
                Entity e = world.map(w->type.customClientSpawn(msg, w)).orElse(null);
                if (e == null)
                {
                    return;
                }

                e.func_213312_b(msg.posX, msg.posY, msg.posZ);
                e.func_70080_a(msg.posX, msg.posY, msg.posZ, (msg.yaw * 360) / 256.0F, (msg.pitch * 360) / 256.0F);
                e.func_70034_d((msg.headYaw * 360) / 256.0F);
                e.func_181013_g((msg.headYaw * 360) / 256.0F);

                e.func_145769_d(msg.entityId);
                e.func_184221_a(msg.uuid);
                world.filter(ClientWorld.class::isInstance).ifPresent(w->((ClientWorld)w).func_217411_a(msg.entityId, e));
                e.func_70016_h(msg.velX / 8000.0, msg.velY / 8000.0, msg.velZ / 8000.0);
                if (e instanceof IEntityAdditionalSpawnData)
                {
                    ((IEntityAdditionalSpawnData) e).readSpawnData(msg.buf);
                }
            });
            ctx.get().setPacketHandled(true);
        }

        public Entity getEntity()
        {
            return entity;
        }

        public int getTypeId()
        {
            return typeId;
        }

        public int getEntityId()
        {
            return entityId;
        }

        public UUID getUuid()
        {
            return uuid;
        }

        public double getPosX()
        {
            return posX;
        }

        public double getPosY()
        {
            return posY;
        }

        public double getPosZ()
        {
            return posZ;
        }

        public byte getPitch()
        {
            return pitch;
        }

        public byte getYaw()
        {
            return yaw;
        }

        public byte getHeadYaw()
        {
            return headYaw;
        }

        public int getVelX()
        {
            return velX;
        }

        public int getVelY()
        {
            return velY;
        }

        public int getVelZ()
        {
            return velZ;
        }

        public PacketBuffer getAdditionalData()
        {
            return buf;
        }
    }

    public static class OpenContainer
    {
        private final int id;
        private final int windowId;
        private final ITextComponent name;
        private final PacketBuffer additionalData;

        OpenContainer(ContainerType<?> id, int windowId, ITextComponent name, PacketBuffer additionalData)
        {
            this(Registry.field_218366_G.func_148757_b(id), windowId, name, additionalData);
        }

        private OpenContainer(int id, int windowId, ITextComponent name, PacketBuffer additionalData)
        {
            this.id = id;
            this.windowId = windowId;
            this.name = name;
            this.additionalData = additionalData;
        }

        public static void encode(OpenContainer msg, PacketBuffer buf)
        {
            buf.func_150787_b(msg.id);
            buf.func_150787_b(msg.windowId);
            buf.func_179256_a(msg.name);
            buf.func_179250_a(msg.additionalData.func_179251_a());
        }

        public static OpenContainer decode(PacketBuffer buf)
        {
            return new OpenContainer(buf.func_150792_a(), buf.func_150792_a(), buf.func_179258_d(), new PacketBuffer(Unpooled.wrappedBuffer(buf.func_189425_b(32600))));
        }

        public static void handle(OpenContainer msg, Supplier<NetworkEvent.Context> ctx)
        {
            ctx.get().enqueueWork(() -> {
                ScreenManager.getScreenFactory(msg.getType(), Minecraft.func_71410_x(), msg.getWindowId(), msg.getName())
                             .ifPresent(f -> {
                                 Container c = msg.getType().create(msg.getWindowId(), Minecraft.func_71410_x().field_71439_g.field_71071_by, msg.getAdditionalData());
                                 @SuppressWarnings("unchecked")
                                 Screen s = ((ScreenManager.IScreenFactory<Container, ?>)f).create(c, Minecraft.func_71410_x().field_71439_g.field_71071_by, msg.getName());
                                 Minecraft.func_71410_x().field_71439_g.field_71070_bA = ((IHasContainer<?>)s).func_212873_a_();
                                 Minecraft.func_71410_x().func_147108_a(s);
                             });
            });
            ctx.get().setPacketHandled(true);
        }

        public final ContainerType<?> getType() {
            return Registry.field_218366_G.func_148745_a(this.id);
        }

        public int getWindowId() {
            return windowId;
        }

        public ITextComponent getName() {
            return name;
        }

        public PacketBuffer getAdditionalData() {
            return additionalData;
        }
    }

    public static class SyncCustomTagTypes
    {
        private static final Logger LOGGER = LogManager.getLogger();
        private final Map<ResourceLocation, ITagCollection<?>> customTagTypeCollections;

        SyncCustomTagTypes(Map<ResourceLocation, ITagCollection<?>> customTagTypeCollections)
        {
            this.customTagTypeCollections = customTagTypeCollections;
        }

        public Map<ResourceLocation, ITagCollection<?>> getCustomTagTypes()
        {
            return customTagTypeCollections;
        }

        public static void encode(SyncCustomTagTypes msg, PacketBuffer buf)
        {
            buf.func_150787_b(msg.customTagTypeCollections.size());
            msg.customTagTypeCollections.forEach((registryName, modded) -> forgeTagCollectionWrite(buf, registryName, modded.func_241833_a()));
        }

        private static <T> void forgeTagCollectionWrite(PacketBuffer buf, ResourceLocation registryName, Map<ResourceLocation, ITag<T>> tags)
        {
            buf.func_192572_a(registryName);
            buf.func_150787_b(tags.size());
            tags.forEach((name, tag) -> {
                buf.func_192572_a(name);
                List<T> elements = tag.func_230236_b_();
                buf.func_150787_b(elements.size());
                for (T element : elements)
                {
                    buf.func_192572_a(((IForgeRegistryEntry<?>) element).getRegistryName());
                }
            });
        }

        public static SyncCustomTagTypes decode(PacketBuffer buf)
        {
            ImmutableMap.Builder<ResourceLocation, ITagCollection<?>> builder = ImmutableMap.builder();
            int size = buf.func_150792_a();
            for (int i = 0; i < size; i++)
            {
                ResourceLocation regName = buf.func_192575_l();
                IForgeRegistry<?> registry = RegistryManager.ACTIVE.getRegistry(regName);
                if (registry != null)
                {
                    builder.put(regName, readTagCollection(buf, registry));
                }
            }
            return new SyncCustomTagTypes(builder.build());
        }

        private static <T extends IForgeRegistryEntry<T>> ITagCollection<T> readTagCollection(PacketBuffer buf, IForgeRegistry<T> registry)
        {
            Map<ResourceLocation, ITag<T>> tags = Maps.newHashMap();
            int totalTags = buf.func_150792_a();
            for (int i = 0; i < totalTags; i++)
            {
                ImmutableSet.Builder<T> elementBuilder = ImmutableSet.builder();
                ResourceLocation name = buf.func_192575_l();
                int totalElements = buf.func_150792_a();
                for (int j = 0; j < totalElements; j++)
                {
                    T element = registry.getValue(buf.func_192575_l());
                    if (element != null)
                    {
                        elementBuilder.add(element);
                    }
                }
                tags.put(name, ITag.func_232946_a_(elementBuilder.build()));
            }
            return ITagCollection.func_242202_a(tags);
        }

        public static void handle(SyncCustomTagTypes msg, Supplier<NetworkEvent.Context> ctx)
        {
            ctx.get().enqueueWork(() -> {
                if (Minecraft.func_71410_x().field_71441_e != null)
                {
                    ITagCollectionSupplier tagCollectionSupplier = Minecraft.func_71410_x().field_71441_e.func_205772_D();
                    //Validate that all the tags exist using the tag type collections from the packet
                    // We mimic vanilla in that we validate before updating the actual stored tags so that it can gracefully fallback
                    // to the last working set of tags
                    //Note: We gracefully ignore any tag types the server may have that we don't as they won't be in our tag registry
                    // so they won't be validated
                    //Override and use the tags from the packet to test for validation before we actually set them
                    Multimap<ResourceLocation, ResourceLocation> missingTags = TagRegistryManager.func_242198_b(ForgeTagHandler.withSpecificCustom(tagCollectionSupplier, msg.customTagTypeCollections));
                    if (missingTags.isEmpty())
                    {
                        //If we have no missing tags, update the custom tag types
                        ForgeTagHandler.updateCustomTagTypes(msg);
                        if (!ctx.get().getNetworkManager().func_150731_c())
                        {
                            //And if everything hasn't already been set due to being in single player
                            // Fetch and update the custom tag types. We skip vanilla tag types as they have already been fetched
                            // And fire an event that the custom tag types have been updated
                            TagRegistryManager.fetchCustomTagTypes(tagCollectionSupplier);
                            MinecraftForge.EVENT_BUS.post(new TagsUpdatedEvent.CustomTagTypes(tagCollectionSupplier));
                        }
                    } else {
                        LOGGER.warn("Incomplete server tags, disconnecting. Missing: {}", missingTags);
                        ctx.get().getNetworkManager().func_150718_a(new TranslationTextComponent("multiplayer.disconnect.missing_tags"));
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
