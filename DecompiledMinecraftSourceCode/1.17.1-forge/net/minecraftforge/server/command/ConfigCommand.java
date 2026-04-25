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

package net.minecraftforge.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.io.File;

public class ConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.m_82127_("config").
                        then(ShowFile.register())
        );
    }

    public static class ShowFile {
        static ArgumentBuilder<CommandSourceStack, ?> register() {
            return Commands.m_82127_("showfile").
                    requires(cs->cs.m_6761_(0)).
                    then(Commands.m_82129_("mod", ModIdArgument.modIdArgument()).
                        then(Commands.m_82129_("type", EnumArgument.enumArgument(ModConfig.Type.class)).
                            executes(ShowFile::showFile)
                        )
                    );
        }

        private static int showFile(final CommandContext<CommandSourceStack> context) {
            final String modId = context.getArgument("mod", String.class);
            final ModConfig.Type type = context.getArgument("type", ModConfig.Type.class);
            final String configFileName = ConfigTracker.INSTANCE.getConfigFileName(modId, type);
            if (configFileName != null) {
                File f = new File(configFileName);
                context.getSource().m_81354_(new TranslatableComponent("commands.config.getwithtype",
                        modId, type,
                        new TextComponent(f.getName()).m_130940_(ChatFormatting.UNDERLINE).
                        m_130938_((style) -> style.m_131142_(new ClickEvent(ClickEvent.Action.OPEN_FILE, f.getAbsolutePath())))
                ), true);
            } else {
                context.getSource().m_81354_(new TranslatableComponent("commands.config.noconfig", modId, type),
                        true);
            }
            return 0;
        }
    }
}
