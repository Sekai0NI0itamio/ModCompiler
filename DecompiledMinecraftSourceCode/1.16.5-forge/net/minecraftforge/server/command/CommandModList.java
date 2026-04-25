/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.server.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.ModList;
import java.util.stream.Collectors;

public class CommandModList {
    static ArgumentBuilder<CommandSource, ?> register()
    {
        return Commands.func_197057_a("mods")
                .requires(cs->cs.func_197034_c(0)) //permission
                .executes(ctx -> {
                            ctx.getSource().func_197030_a(new TranslationTextComponent("commands.forge.mods.list",
                                    ModList.get().applyForEachModFile(modFile ->
                                            // locator - filename : firstmod (version) - numberofmods\n
                                            String.format("%s %s : %s (%s) - %d",
                                                    modFile.getLocator().name().replace(' ', '_'),
                                                    modFile.getFileName(),
                                                    modFile.getModInfos().get(0).getModId(),
                                                    modFile.getModInfos().get(0).getVersion(),
                                                    modFile.getModInfos().size())).
                                        collect(Collectors.joining("\n\u2022 ","\n\u2022 ", ""))),
                                    false);
                            return 0;
                        }
                );
    }

}
