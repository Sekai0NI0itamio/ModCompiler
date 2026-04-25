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

package net.minecraftforge.fmlclient.gui.screen;

import com.google.common.base.Strings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ErrorScreen;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraftforge.fmllegacy.ForgeI18n;
import net.minecraftforge.fml.LoadingFailedException;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fmlclient.ClientHooks;
import net.minecraftforge.fmlclient.gui.widget.ExtendedButton;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LoadingErrorScreen extends ErrorScreen {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path modsDir;
    private final Path logFile;
    private final List<ModLoadingException> modLoadErrors;
    private final List<ModLoadingWarning> modLoadWarnings;
    private final Path dumpedLocation;
    private LoadingEntryList entryList;
    private Component errorHeader;
    private Component warningHeader;

    public LoadingErrorScreen(LoadingFailedException loadingException, List<ModLoadingWarning> warnings, final File dumpedLocation)
    {
        super(new TextComponent("Loading Error"), null);
        this.modLoadWarnings = warnings;
        this.modLoadErrors = loadingException == null ? Collections.emptyList() : loadingException.getErrors();
        this.modsDir = FMLPaths.MODSDIR.get();
        this.logFile = FMLPaths.GAMEDIR.get().resolve(Paths.get("logs","latest.log"));
        this.dumpedLocation = dumpedLocation != null ? dumpedLocation.toPath() : null;
    }

    @Override
    public void m_7856_()
    {
        super.m_7856_();
        this.m_169413_();

        this.errorHeader = new TextComponent(ChatFormatting.RED + ForgeI18n.parseMessage("fml.loadingerrorscreen.errorheader", this.modLoadErrors.size()) + ChatFormatting.RESET);
        this.warningHeader = new TextComponent(ChatFormatting.YELLOW + ForgeI18n.parseMessage("fml.loadingerrorscreen.warningheader", this.modLoadErrors.size()) + ChatFormatting.RESET);

        int yOffset = 46;
        this.m_142416_(new ExtendedButton(50, this.f_96544_ - yOffset, this.f_96543_ / 2 - 55, 20, new TextComponent(ForgeI18n.parseMessage("fml.button.open.mods.folder")), b -> Util.m_137581_().m_137644_(modsDir.toFile())));
        this.m_142416_(new ExtendedButton(this.f_96543_ / 2 + 5, this.f_96544_ - yOffset, this.f_96543_ / 2 - 55, 20, new TextComponent(ForgeI18n.parseMessage("fml.button.open.file", logFile.getFileName())), b -> Util.m_137581_().m_137644_(logFile.toFile())));
        if (this.modLoadErrors.isEmpty()) {
            this.m_142416_(new ExtendedButton(this.f_96543_ / 4, this.f_96544_ - 24, this.f_96543_ / 2, 20, new TextComponent(ForgeI18n.parseMessage("fml.button.continue.launch")), b -> {
                this.f_96541_.m_91152_(null);
            }));
        } else {
            this.m_142416_(new ExtendedButton(this.f_96543_ / 4, this.f_96544_ - 24, this.f_96543_ / 2, 20, new TextComponent(ForgeI18n.parseMessage("fml.button.open.file", dumpedLocation.getFileName())), b -> Util.m_137581_().m_137644_(dumpedLocation.toFile())));
        }

        this.entryList = new LoadingEntryList(this, this.modLoadErrors, this.modLoadWarnings);
        this.m_7787_(this.entryList);
        this.m_7522_(this.entryList);
    }

    @Override
    public void m_6305_(PoseStack mStack, int mouseX, int mouseY, float partialTicks)
    {
        this.m_7333_(mStack);
        this.entryList.m_6305_(mStack, mouseX, mouseY, partialTicks);
        drawMultiLineCenteredString(mStack, f_96547_, this.modLoadErrors.isEmpty() ? warningHeader : errorHeader, this.f_96543_ / 2, 10);
        this.f_169369_.forEach(button -> button.m_6305_(mStack, mouseX, mouseY, partialTicks));
    }

    private void drawMultiLineCenteredString(PoseStack mStack, Font fr, Component str, int x, int y) {
        for (FormattedCharSequence s : fr.m_92923_(str, this.f_96543_)) {
            fr.m_92744_(mStack, s, (float) (x - fr.m_92724_(s) / 2.0), y, 0xFFFFFF);
            y+=fr.f_92710_;
        }
    }
    public static class LoadingEntryList extends ObjectSelectionList<LoadingEntryList.LoadingMessageEntry> {
        LoadingEntryList(final LoadingErrorScreen parent, final List<ModLoadingException> errors, final List<ModLoadingWarning> warnings) {
            super(parent.f_96541_, parent.f_96543_, parent.f_96544_, 35, parent.f_96544_ - 50, 2 * parent.f_96541_.f_91062_.f_92710_ + 8);
            boolean both = !errors.isEmpty() && !warnings.isEmpty();
            if (both)
                m_7085_(new LoadingMessageEntry(parent.errorHeader, true));
            errors.forEach(e->m_7085_(new LoadingMessageEntry(new TextComponent(e.formatToString()))));
            if (both) {
                int maxChars = (this.f_93388_ - 10) / parent.f_96541_.f_91062_.m_92895_("-");
                m_7085_(new LoadingMessageEntry(new TextComponent("\n" + Strings.repeat("-", maxChars) + "\n")));
                m_7085_(new LoadingMessageEntry(parent.warningHeader, true));
            }
            warnings.forEach(w->m_7085_(new LoadingMessageEntry(new TextComponent(w.formatToString()))));
        }

        @Override
        protected int m_5756_()
        {
            return this.getRight() - 6;
        }

        @Override
        public int m_5759_()
        {
            return this.f_93388_;
        }

        public class LoadingMessageEntry extends ObjectSelectionList.Entry<LoadingMessageEntry> {
            private final Component message;
            private final boolean center;

            LoadingMessageEntry(final Component message) {
                this(message, false);
            }

            LoadingMessageEntry(final Component message, final boolean center) {
                this.message = Objects.requireNonNull(message);
                this.center = center;
            }

            @Override
            public Component m_142172_() {
                return new TranslatableComponent("narrator.select", message);
            }

            @Override
            public void m_6311_(PoseStack pStack, int entryIdx, int top, int left, final int entryWidth, final int entryHeight, final int mouseX, final int mouseY, final boolean p_194999_5_, final float partialTicks) {
                Font font = Minecraft.m_91087_().f_91062_;
                final List<FormattedCharSequence> strings = font.m_92923_(message, LoadingEntryList.this.f_93388_);
                int y = top + 2;
                for (int i = 0; i < Math.min(strings.size(), 2); i++) {
                    if (center)
                        font.m_92877_(pStack, strings.get(i), left + (f_93388_) - font.m_92724_(strings.get(i)) / 2F, y, 0xFFFFFF);
                    else
                        font.m_92877_(pStack, strings.get(i), left + 5, y, 0xFFFFFF);
                    y += font.f_92710_;
                }
            }
        }

    }
}
