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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.fmlclient.gui.GuiUtils;
import net.minecraftforge.fmlclient.gui.widget.ModListWidget;
import net.minecraftforge.resource.PathResourcePack;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ComparableVersion;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraftforge.client.gui.ScrollPanel;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.Size2i;
import net.minecraftforge.fmllegacy.ForgeI18n;
import net.minecraftforge.fmllegacy.MavenVersionStringHelper;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fmlclient.ConfigGuiHandler;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.fmllegacy.packs.ResourcePackLoader;
import net.minecraftforge.forgespi.language.IModInfo;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TranslatableComponent;

import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;

public class ModListScreen extends Screen
{
    private static String stripControlCodes(String value) { return net.minecraft.util.StringUtil.m_14406_(value); }
    private static final Logger LOGGER = LogManager.getLogger();
    private enum SortType implements Comparator<IModInfo>
    {
        NORMAL,
        A_TO_Z{ @Override protected int compare(String name1, String name2){ return name1.compareTo(name2); }},
        Z_TO_A{ @Override protected int compare(String name1, String name2){ return name2.compareTo(name1); }};

        Button button;
        protected int compare(String name1, String name2){ return 0; }
        @Override
        public int compare(IModInfo o1, IModInfo o2) {
            String name1 = StringUtils.toLowerCase(stripControlCodes(o1.getDisplayName()));
            String name2 = StringUtils.toLowerCase(stripControlCodes(o2.getDisplayName()));
            return compare(name1, name2);
        }

        Component getButtonText() {
            return new TranslatableComponent("fml.menu.mods." + StringUtils.toLowerCase(name()));
        }
    }

    private static final int PADDING = 6;

    private Screen parentScreen;

    private ModListWidget modList;
    private InfoPanel modInfo;
    private ModListWidget.ModEntry selected = null;
    private int listWidth;
    private List<IModInfo> mods;
    private final List<IModInfo> unsortedMods;
    private Button configButton, openModsFolderButton, doneButton;

    private int buttonMargin = 1;
    private int numButtons = SortType.values().length;
    private String lastFilterText = "";

    private EditBox search;

    private boolean sorted = false;
    private SortType sortType = SortType.NORMAL;

    /**
     * @param parentScreen
     */
    public ModListScreen(Screen parentScreen)
    {
        super(new TranslatableComponent("fml.menu.mods.title"));
        this.parentScreen = parentScreen;
        this.mods = Collections.unmodifiableList(ModList.get().getMods());
        this.unsortedMods = Collections.unmodifiableList(this.mods);
    }

    class InfoPanel extends ScrollPanel {
        private ResourceLocation logoPath;
        private Size2i logoDims = new Size2i(0, 0);
        private List<FormattedCharSequence> lines = Collections.emptyList();

        InfoPanel(Minecraft mcIn, int widthIn, int heightIn, int topIn)
        {
            super(mcIn, widthIn, heightIn, topIn, modList.getRight() + PADDING);
        }

        void setInfo(List<String> lines, ResourceLocation logoPath, Size2i logoDims)
        {
            this.logoPath = logoPath;
            this.logoDims = logoDims;
            this.lines = resizeContent(lines);
        }

        void clearInfo()
        {
            this.logoPath = null;
            this.logoDims = new Size2i(0, 0);
            this.lines = Collections.emptyList();
        }

        private List<FormattedCharSequence> resizeContent(List<String> lines)
        {
            List<FormattedCharSequence> ret = new ArrayList<>();
            for (String line : lines)
            {
                if (line == null)
                {
                    ret.add(null);
                    continue;
                }

                Component chat = ForgeHooks.newChatWithLinks(line, false);
                int maxTextLength = this.width - 12;
                if (maxTextLength >= 0)
                {
                    ret.addAll(Language.m_128107_().m_128112_(f_96547_.m_92865_().m_92414_(chat, maxTextLength, Style.f_131099_)));
                }
            }
            return ret;
        }

        @Override
        public int getContentHeight()
        {
            int height = 50;
            height += (lines.size() * f_96547_.f_92710_);
            if (height < this.bottom - this.top - 8)
                height = this.bottom - this.top - 8;
            return height;
        }

        @Override
        protected int getScrollAmount()
        {
            return f_96547_.f_92710_ * 3;
        }

        @Override
        protected void drawPanel(PoseStack mStack, int entryRight, int relativeY, Tesselator tess, int mouseX, int mouseY)
        {
            if (logoPath != null) {
                RenderSystem.m_157427_(GameRenderer::m_172817_);
                RenderSystem.m_69478_();
                RenderSystem.m_157429_(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.m_157456_(0, logoPath);
                // Draw the logo image inscribed in a rectangle with width entryWidth (minus some padding) and height 50
                int headerHeight = 50;
                GuiUtils.drawInscribedRect(mStack, left + PADDING, relativeY, width - (PADDING * 2), headerHeight, logoDims.width, logoDims.height, false, true);
                relativeY += headerHeight + PADDING;
            }

            for (FormattedCharSequence line : lines)
            {
                if (line != null)
                {
                    RenderSystem.m_69478_();
                    ModListScreen.this.f_96547_.m_92744_(mStack, line, left + PADDING, relativeY, 0xFFFFFF);
                    RenderSystem.m_69461_();
                }
                relativeY += f_96547_.f_92710_;
            }

            final Style component = findTextLine(mouseX, mouseY);
            if (component!=null) {
                ModListScreen.this.m_96570_(mStack, component, mouseX, mouseY);
            }
        }

        private Style findTextLine(final int mouseX, final int mouseY) {
            if (!m_5953_(mouseX, mouseY))
                return null;

            double offset = (mouseY - top) + border + scrollDistance + 1;
            if (logoPath != null) {
                offset -= 50;
            }
            if (offset <= 0)
                return null;

            int lineIdx = (int) (offset / f_96547_.f_92710_);
            if (lineIdx >= lines.size() || lineIdx < 1)
                return null;

            FormattedCharSequence line = lines.get(lineIdx-1);
            if (line != null)
            {
                return f_96547_.m_92865_().m_92338_(line, mouseX - left - border);
            }
            return null;
        }

        @Override
        public boolean m_6375_(final double mouseX, final double mouseY, final int button) {
            final Style component = findTextLine((int) mouseX, (int) mouseY);
            if (component != null) {
                ModListScreen.this.m_5561_(component);
                return true;
            }
            return super.m_6375_(mouseX, mouseY, button);
        }

        @Override
        protected void drawBackground() {
        }

        @Override
        public NarrationPriority m_142684_() {
            return NarrationPriority.NONE;
        }

        @Override
        public void m_142291_(NarrationElementOutput p_169152_) {
        }
    }

    @Override
    public void m_7856_()
    {
        for (IModInfo mod : mods)
        {
            listWidth = Math.max(listWidth,getFontRenderer().m_92895_(mod.getDisplayName()) + 10);
            listWidth = Math.max(listWidth,getFontRenderer().m_92895_(MavenVersionStringHelper.artifactVersionToString(mod.getVersion())) + 5);
        }
        listWidth = Math.max(Math.min(listWidth, f_96543_/3), 100);
        listWidth += listWidth % numButtons != 0 ? (numButtons - listWidth % numButtons) : 0;

        int modInfoWidth = this.f_96543_ - this.listWidth - (PADDING * 3);
        int doneButtonWidth = Math.min(modInfoWidth, 200);
        int y = this.f_96544_ - 20 - PADDING;
        int fullButtonHeight = PADDING + 20 + PADDING;

        doneButton = new Button(((listWidth + PADDING + this.f_96543_ - doneButtonWidth) / 2), y, doneButtonWidth, 20, new TranslatableComponent("gui.done"), b -> ModListScreen.this.m_7379_());
        openModsFolderButton = new Button(6, y, this.listWidth, 20, new TranslatableComponent("fml.menu.mods.openmodsfolder"), b -> Util.m_137581_().m_137644_(FMLPaths.MODSDIR.get().toFile()));
        y -= 20 + PADDING;
        configButton = new Button(6, y, this.listWidth, 20, new TranslatableComponent("fml.menu.mods.config"), b -> ModListScreen.this.displayModConfig());
        y -= 14 + PADDING;
        search = new EditBox(getFontRenderer(), PADDING + 1, y, listWidth - 2, 14, new TranslatableComponent("fml.menu.mods.search"));

        this.modList = new ModListWidget(this, listWidth, fullButtonHeight, search.f_93621_ - getFontRenderer().f_92710_ - PADDING);
        this.modList.m_93507_(6);
        this.modInfo = new InfoPanel(this.f_96541_, modInfoWidth, this.f_96544_ - PADDING - fullButtonHeight, PADDING);

        this.m_142416_(modList);
        this.m_142416_(modInfo);
        this.m_142416_(search);
        this.m_142416_(doneButton);
        this.m_142416_(configButton);
        this.m_142416_(openModsFolderButton);

        search.m_94178_(false);
        search.m_94190_(true);
        configButton.f_93623_ = false;

        final int width = listWidth / numButtons;
        int x = PADDING;
        m_142416_(SortType.NORMAL.button = new Button(x, PADDING, width - buttonMargin, 20, SortType.NORMAL.getButtonText(), b -> resortMods(SortType.NORMAL)));
        x += width + buttonMargin;
        m_142416_(SortType.A_TO_Z.button = new Button(x, PADDING, width - buttonMargin, 20, SortType.A_TO_Z.getButtonText(), b -> resortMods(SortType.A_TO_Z)));
        x += width + buttonMargin;
        m_142416_(SortType.Z_TO_A.button = new Button(x, PADDING, width - buttonMargin, 20, SortType.Z_TO_A.getButtonText(), b -> resortMods(SortType.Z_TO_A)));
        resortMods(SortType.NORMAL);
        updateCache();
    }

    private void displayModConfig()
    {
        if (selected == null) return;
        try
        {
            ConfigGuiHandler.getGuiFactoryFor(selected.getInfo()).map(f->f.apply(this.f_96541_, this)).ifPresent(newScreen -> this.f_96541_.m_91152_(newScreen));
        }
        catch (final Exception e)
        {
            LOGGER.error("There was a critical issue trying to build the config GUI for {}", selected.getInfo().getModId(), e);
        }
    }

    @Override
    public void m_96624_()
    {
        search.m_94120_();
        modList.m_6987_(selected);

        if (!search.m_94155_().equals(lastFilterText))
        {
            reloadMods();
            sorted = false;
        }

        if (!sorted)
        {
            reloadMods();
            mods.sort(sortType);
            modList.refreshList();
            if (selected != null)
            {
                selected = modList.m_6702_().stream().filter(e -> e.getInfo() == selected.getInfo()).findFirst().orElse(null);
                updateCache();
            }
            sorted = true;
        }
    }

    public <T extends ObjectSelectionList.Entry<T>> void buildModList(Consumer<T> modListViewConsumer, Function<IModInfo, T> newEntry)
    {
        mods.forEach(mod->modListViewConsumer.accept(newEntry.apply(mod)));
    }

    private void reloadMods()
    {
        this.mods = this.unsortedMods.stream().
                filter(mi->StringUtils.toLowerCase(stripControlCodes(mi.getDisplayName())).contains(StringUtils.toLowerCase(search.m_94155_()))).collect(Collectors.toList());
        lastFilterText = search.m_94155_();
    }

    private void resortMods(SortType newSort)
    {
        this.sortType = newSort;

        for (SortType sort : SortType.values())
        {
            if (sort.button != null)
                sort.button.f_93623_ = sortType != sort;
        }
        sorted = false;
    }

    @Override
    public void m_6305_(PoseStack mStack, int mouseX, int mouseY, float partialTicks)
    {
        this.modList.m_6305_(mStack, mouseX, mouseY, partialTicks);
        if (this.modInfo != null)
            this.modInfo.m_6305_(mStack, mouseX, mouseY, partialTicks);

        Component text = new TranslatableComponent("fml.menu.mods.search");
        int x = modList.getLeft() + ((modList.getRight() - modList.getLeft()) / 2) - (getFontRenderer().m_92852_(text) / 2);
        this.search.m_6305_(mStack, mouseX , mouseY, partialTicks);
        super.m_6305_(mStack, mouseX, mouseY, partialTicks);
        getFontRenderer().m_92877_(mStack, text.m_7532_(), x, search.f_93621_ - getFontRenderer().f_92710_, 0xFFFFFF);
    }

    public Minecraft getMinecraftInstance()
    {
        return f_96541_;
    }

    public Font getFontRenderer()
    {
        return f_96547_;
    }

    public void setSelected(ModListWidget.ModEntry entry)
    {
        this.selected = entry == this.selected ? null : entry;
        updateCache();
    }

    private void updateCache()
    {
        if (selected == null) {
            this.configButton.f_93623_ = false;
            this.modInfo.clearInfo();
            return;
        }
        IModInfo selectedMod = selected.getInfo();
        this.configButton.f_93623_ = ConfigGuiHandler.getGuiFactoryFor(selectedMod).isPresent();
        List<String> lines = new ArrayList<>();
        VersionChecker.CheckResult vercheck = VersionChecker.getResult(selectedMod);

        @SuppressWarnings("resource")
        Pair<ResourceLocation, Size2i> logoData = selectedMod.getLogoFile().map(logoFile->
        {
            TextureManager tm = this.f_96541_.m_91097_();
            final PathResourcePack resourcePack = ResourcePackLoader.getPackFor(selectedMod.getModId())
                    .orElse(ResourcePackLoader.getPackFor("forge").
                            orElseThrow(()->new RuntimeException("Can't find forge, WHAT!")));
            try
            {
                NativeImage logo = null;
                InputStream logoResource = resourcePack.m_5542_(logoFile);
                if (logoResource != null)
                    logo = NativeImage.m_85058_(logoResource);
                if (logo != null)
                {

                    return Pair.of(tm.m_118490_("modlogo", new DynamicTexture(logo) {

                        @Override
                        public void m_117985_() {
                            this.m_117966_();
                            NativeImage td = this.m_117991_();
                            // Use custom "blur" value which controls texture filtering (nearest-neighbor vs linear)
                            this.m_117991_().m_85013_(0, 0, 0, 0, 0, td.m_84982_(), td.m_85084_(), selectedMod.getLogoBlur(), false, false, false);
                        }
                    }), new Size2i(logo.m_84982_(), logo.m_85084_()));
                }
            }
            catch (IOException e) { }
            return Pair.<ResourceLocation, Size2i>of(null, new Size2i(0, 0));
        }).orElse(Pair.of(null, new Size2i(0, 0)));

        lines.add(selectedMod.getDisplayName());
        lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.version", MavenVersionStringHelper.artifactVersionToString(selectedMod.getVersion())));
        lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.idstate", selectedMod.getModId(), ModList.get().getModContainerById(selectedMod.getModId()).
                map(ModContainer::getCurrentState).map(Object::toString).orElse("NONE")));

        selectedMod.getConfig().getConfigElement("credits").ifPresent(credits->
                lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.credits", credits)));
        selectedMod.getConfig().getConfigElement("authors").ifPresent(authors ->
                lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.authors", authors)));
        selectedMod.getConfig().getConfigElement("displayURL").ifPresent(displayURL ->
                lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.displayurl", displayURL)));
        if (selectedMod.getOwningFile() == null || selectedMod.getOwningFile().getMods().size()==1)
            lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.nochildmods"));
        else
            lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.childmods", selectedMod.getOwningFile().getMods().stream().map(IModInfo::getDisplayName).collect(Collectors.joining(","))));

        if (vercheck.status() == VersionChecker.Status.OUTDATED || vercheck.status() == VersionChecker.Status.BETA_OUTDATED)
            lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.updateavailable", vercheck.url() == null ? "" : vercheck.url()));
        lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.license", selectedMod.getOwningFile().getLicense()));
        lines.add(null);
        lines.add(selectedMod.getDescription());

        /* Removed because people bitched that this information was misleading.
        lines.add(null);
        if (FMLEnvironment.secureJarsEnabled) {
            lines.add(ForgeI18getOwningFile().getFile().n.parseMessage("fml.menu.mods.info.signature", selectedMod.getOwningFile().getCodeSigningFingerprint().orElse(ForgeI18n.parseMessage("fml.menu.mods.info.signature.unsigned"))));
            lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.trust", selectedMod.getOwningFile().getTrustData().orElse(ForgeI18n.parseMessage("fml.menu.mods.info.trust.noauthority"))));
        } else {
            lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.securejardisabled"));
        }
        */

        if ((vercheck.status() == VersionChecker.Status.OUTDATED || vercheck.status() == VersionChecker.Status.BETA_OUTDATED) && vercheck.changes().size() > 0)
        {
            lines.add(null);
            lines.add(ForgeI18n.parseMessage("fml.menu.mods.info.changelogheader"));
            for (Entry<ComparableVersion, String> entry : vercheck.changes().entrySet())
            {
                lines.add("  " + entry.getKey() + ":");
                lines.add(entry.getValue());
                lines.add(null);
            }
        }

        modInfo.setInfo(lines, logoData.getLeft(), logoData.getRight());
    }

    @Override
    public void m_6574_(Minecraft mc, int width, int height)
    {
        String s = this.search.m_94155_();
        SortType sort = this.sortType;
        ModListWidget.ModEntry selected = this.selected;
        this.m_6575_(mc, width, height);
        this.search.m_94144_(s);
        this.selected = selected;
        if (!this.search.m_94155_().isEmpty())
            reloadMods();
        if (sort != SortType.NORMAL)
            resortMods(sort);
        updateCache();
    }

    @Override
    public void m_7379_()
    {
        this.f_96541_.m_91152_(this.parentScreen);
    }
}
