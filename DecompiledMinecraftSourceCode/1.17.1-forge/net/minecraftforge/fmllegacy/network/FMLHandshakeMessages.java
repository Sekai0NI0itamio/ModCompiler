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

package net.minecraftforge.fmllegacy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.Maps;

public class FMLHandshakeMessages
{
    static class LoginIndexedMessage implements IntSupplier
    {
        private int loginIndex;

        void setLoginIndex(final int loginIndex) {
            this.loginIndex = loginIndex;
        }

        int getLoginIndex() {
            return loginIndex;
        }

        @Override
        public int getAsInt() {
            return getLoginIndex();
        }
    }
    /**
     * Server to client "list of mods". Always first handshake message.
     */
    public static class S2CModList extends LoginIndexedMessage
    {
        private List<String> mods;
        private Map<ResourceLocation, String> channels;
        private List<ResourceLocation> registries;

        public S2CModList()
        {
            this.mods = ModList.get().getMods().stream().map(IModInfo::getModId).collect(Collectors.toList());
            this.channels = NetworkRegistry.buildChannelVersions();
            this.registries = RegistryManager.getRegistryNamesForSyncToClient();
        }

        private S2CModList(List<String> mods, Map<ResourceLocation, String> channels, List<ResourceLocation> registries)
        {
            this.mods = mods;
            this.channels = channels;
            this.registries = registries;
        }

        public static S2CModList decode(FriendlyByteBuf input)
        {
            List<String> mods = new ArrayList<>();
            int len = input.m_130242_();
            for (int x = 0; x < len; x++)
                mods.add(input.m_130136_(0x100));

            Map<ResourceLocation, String> channels = new HashMap<>();
            len = input.m_130242_();
            for (int x = 0; x < len; x++)
                channels.put(input.m_130281_(), input.m_130136_(0x100));

            List<ResourceLocation> registries = new ArrayList<>();
            len = input.m_130242_();
            for (int x = 0; x < len; x++)
                registries.add(input.m_130281_());

            return new S2CModList(mods, channels, registries);
        }

        public void encode(FriendlyByteBuf output)
        {
            output.m_130130_(mods.size());
            mods.forEach(m -> output.m_130072_(m, 0x100));

            output.m_130130_(channels.size());
            channels.forEach((k, v) -> {
                output.m_130085_(k);
                output.m_130072_(v, 0x100);
            });

            output.m_130130_(registries.size());
            registries.forEach(output::m_130085_);
        }

        public List<String> getModList() {
            return mods;
        }

        public List<ResourceLocation> getRegistries() {
            return this.registries;
        }

        public Map<ResourceLocation, String> getChannels() {
            return this.channels;
        }
    }

    public static class C2SModListReply extends LoginIndexedMessage
    {
        private List<String> mods;
        private Map<ResourceLocation, String> channels;
        private Map<ResourceLocation, String> registries;

        public C2SModListReply()
        {
            this.mods = ModList.get().getMods().stream().map(IModInfo::getModId).collect(Collectors.toList());
            this.channels = NetworkRegistry.buildChannelVersions();
            this.registries = Maps.newHashMap(); //TODO: Fill with known hashes, which requires keeping a file cache
        }

        private C2SModListReply(List<String> mods, Map<ResourceLocation, String> channels, Map<ResourceLocation, String> registries)
        {
            this.mods = mods;
            this.channels = channels;
            this.registries = registries;
        }

        public static C2SModListReply decode(FriendlyByteBuf input)
        {
            List<String> mods = new ArrayList<>();
            int len = input.m_130242_();
            for (int x = 0; x < len; x++)
                mods.add(input.m_130136_(0x100));

            Map<ResourceLocation, String> channels = new HashMap<>();
            len = input.m_130242_();
            for (int x = 0; x < len; x++)
                channels.put(input.m_130281_(), input.m_130136_(0x100));

            Map<ResourceLocation, String> registries = new HashMap<>();
            len = input.m_130242_();
            for (int x = 0; x < len; x++)
                registries.put(input.m_130281_(), input.m_130136_(0x100));

            return new C2SModListReply(mods, channels, registries);
        }

        public void encode(FriendlyByteBuf output)
        {
            output.m_130130_(mods.size());
            mods.forEach(m -> output.m_130072_(m, 0x100));

            output.m_130130_(channels.size());
            channels.forEach((k, v) -> {
                output.m_130085_(k);
                output.m_130072_(v, 0x100);
            });

            output.m_130130_(registries.size());
            registries.forEach((k, v) -> {
                output.m_130085_(k);
                output.m_130072_(v, 0x100);
            });
        }

        public List<String> getModList() {
            return mods;
        }

        public Map<ResourceLocation, String> getRegistries() {
            return this.registries;
        }

        public Map<ResourceLocation, String> getChannels() {
            return this.channels;
        }
    }

    public static class C2SAcknowledge extends LoginIndexedMessage {
        public void encode(FriendlyByteBuf buf) {

        }

        public static C2SAcknowledge decode(FriendlyByteBuf buf) {
            return new C2SAcknowledge();
        }
    }

    public static class S2CRegistry extends LoginIndexedMessage {
        private ResourceLocation registryName;
        @Nullable
        private ForgeRegistry.Snapshot snapshot;

        public S2CRegistry(final ResourceLocation name, @Nullable ForgeRegistry.Snapshot snapshot) {
            this.registryName = name;
            this.snapshot = snapshot;
        }

        void encode(final FriendlyByteBuf buffer) {
            buffer.m_130085_(registryName);
            buffer.writeBoolean(hasSnapshot());
            if (hasSnapshot())
                buffer.writeBytes(snapshot.getPacketData());
        }

        public static S2CRegistry decode(final FriendlyByteBuf buffer) {
            ResourceLocation name = buffer.m_130281_();
            ForgeRegistry.Snapshot snapshot = null;
            if (buffer.readBoolean())
                snapshot = ForgeRegistry.Snapshot.read(buffer);
            return new S2CRegistry(name, snapshot);
        }

        public ResourceLocation getRegistryName() {
            return registryName;
        }

        public boolean hasSnapshot() {
            return snapshot != null;
        }

        @Nullable
        public ForgeRegistry.Snapshot getSnapshot() {
            return snapshot;
        }
    }


    public static class S2CConfigData extends LoginIndexedMessage {
        private final String fileName;
        private final byte[] fileData;

        public S2CConfigData(final String configFileName, final byte[] configFileData) {
            this.fileName = configFileName;
            this.fileData = configFileData;
        }

        void encode(final FriendlyByteBuf buffer) {
            buffer.m_130070_(this.fileName);
            buffer.m_130087_(this.fileData);
        }

        public static S2CConfigData decode(final FriendlyByteBuf buffer) {
            return new S2CConfigData(buffer.m_130136_(32767), buffer.m_130052_());
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getBytes() {
            return fileData;
        }
    }
}
