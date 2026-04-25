/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.Multimap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraft.world.storage.SaveFormat;
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
    public CompoundNBT getDataForWriting(SaveFormat.LevelSave levelSave, IServerConfiguration serverInfo)
    {
        CompoundNBT fmlData = new CompoundNBT();
        ListNBT modList = new ListNBT();
        ModList.get().getMods().forEach(mi->
        {
            final CompoundNBT mod = new CompoundNBT();
            mod.func_74778_a("ModId", mi.getModId());
            mod.func_74778_a("ModVersion", MavenVersionStringHelper.artifactVersionToString(mi.getVersion()));
            modList.add(mod);
        });
        fmlData.func_218657_a("LoadingModList", modList);

        CompoundNBT registries = new CompoundNBT();
        fmlData.func_218657_a("Registries", registries);
        LOGGER.debug(WORLDPERSISTENCE,"Gathering id map for writing to world save {}", serverInfo.func_76065_j());

        for (Map.Entry<ResourceLocation, ForgeRegistry.Snapshot> e : RegistryManager.ACTIVE.takeSnapshot(true).entrySet())
        {
            registries.func_218657_a(e.getKey().toString(), e.getValue().write());
        }
        LOGGER.debug(WORLDPERSISTENCE,"ID Map collection complete {}", serverInfo.func_76065_j());
        return fmlData;
    }

    @Override
    public void readData(SaveFormat.LevelSave levelSave, IServerConfiguration serverInfo, CompoundNBT tag)
    {
        if (tag.func_74764_b("LoadingModList"))
        {
            ListNBT modList = tag.func_150295_c("LoadingModList", (byte)10);
            for (int i = 0; i < modList.size(); i++)
            {
                CompoundNBT mod = modList.func_150305_b(i);
                String modId = mod.func_74779_i("ModId");
                if (Objects.equals("minecraft",  modId)) {
                    continue;
                }
                String modVersion = mod.func_74779_i("ModVersion");
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

        if (tag.func_74764_b("Registries")) // 1.8, genericed out the 'registries' list
        {
            Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot = new HashMap<>();
            CompoundNBT regs = tag.func_74775_l("Registries");
            for (String key : regs.func_150296_c())
            {
                snapshot.put(new ResourceLocation(key), ForgeRegistry.Snapshot.read(regs.func_74775_l(key)));
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
