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

package net.minecraftforge.fmllegacy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.Multimap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * @author cpw
 *
 */
public final class FMLWorldPersistenceHook implements WorldPersistenceHooks.WorldPersistenceHook
{

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker WORLDPERSISTENCE = MarkerManager.getMarker("WP");

    @Override
    public String getModId()
    {
        return "fml";
    }

    @Override
    public CompoundTag getDataForWriting(LevelStorageSource.LevelStorageAccess levelSave, WorldData serverInfo)
    {
        CompoundTag fmlData = new CompoundTag();
        ListTag modList = new ListTag();
        ModList.get().getMods().forEach(mi->
        {
            final CompoundTag mod = new CompoundTag();
            mod.m_128359_("ModId", mi.getModId());
            mod.m_128359_("ModVersion", MavenVersionStringHelper.artifactVersionToString(mi.getVersion()));
            modList.add(mod);
        });
        fmlData.m_128365_("LoadingModList", modList);

        CompoundTag registries = new CompoundTag();
        fmlData.m_128365_("Registries", registries);
        LOGGER.debug(WORLDPERSISTENCE,"Gathering id map for writing to world save {}", serverInfo.m_5462_());

        for (Map.Entry<ResourceLocation, ForgeRegistry.Snapshot> e : RegistryManager.ACTIVE.takeSnapshot(true).entrySet())
        {
            registries.m_128365_(e.getKey().toString(), e.getValue().write());
        }
        LOGGER.debug(WORLDPERSISTENCE,"ID Map collection complete {}", serverInfo.m_5462_());
        return fmlData;
    }

    @Override
    public void readData(LevelStorageSource.LevelStorageAccess levelSave, WorldData serverInfo, CompoundTag tag)
    {
        if (tag.m_128441_("LoadingModList"))
        {
            ListTag modList = tag.m_128437_("LoadingModList", (byte)10);
            for (int i = 0; i < modList.size(); i++)
            {
                CompoundTag mod = modList.m_128728_(i);
                String modId = mod.m_128461_("ModId");
                if (Objects.equals("minecraft",  modId)) {
                    continue;
                }
                String modVersion = mod.m_128461_("ModVersion");
                Optional<? extends ModContainer> container = ModList.get().getModContainerById(modId);
                if (!container.isPresent())
                {
                    LOGGER.error(WORLDPERSISTENCE,"This world was saved with mod {} which appears to be missing, things may not work well", modId);
                    continue;
                }
                if (!Objects.equals(modVersion, MavenVersionStringHelper.artifactVersionToString(container.get().getModInfo().getVersion())))
                {
                    LOGGER.warn(WORLDPERSISTENCE,"This world was saved with mod {} version {} and it is now at version {}, things may not work well", modId, modVersion, MavenVersionStringHelper.artifactVersionToString(container.get().getModInfo().getVersion()));
                }
            }
        }

        Multimap<ResourceLocation, ResourceLocation> failedElements = null;

        if (tag.m_128441_("Registries")) // 1.8, genericed out the 'registries' list
        {
            Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot = new HashMap<>();
            CompoundTag regs = tag.m_128469_("Registries");
            for (String key : regs.m_128431_())
            {
                snapshot.put(new ResourceLocation(key), ForgeRegistry.Snapshot.read(regs.m_128469_(key)));
            }
            failedElements = GameData.injectSnapshot(snapshot, true, true);
        }

        if (failedElements != null && !failedElements.isEmpty())
        {
            StringBuilder buf = new StringBuilder();
            buf.append("Forge Mod Loader could not load this save.\n\n")
               .append("There are ").append(failedElements.size()).append(" unassigned registry entries in this save.\n")
               .append("You will not be able to load until they are present again.\n\n");

            failedElements.asMap().forEach((name, entries) ->
            {
                buf.append("Missing ").append(name).append(":\n");
                entries.forEach(rl -> buf.append("    ").append(rl).append("\n"));
            });
        }
    }
}
