package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.realmsclient.dto.Backup;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsBackupInfoScreen extends RealmsScreen {
    private static final Component TITLE = Component.translatable("mco.backup.info.title");
    private static final Component UNKNOWN = Component.translatable("mco.backup.unknown");
    private final Screen lastScreen;
    final Backup backup;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private RealmsBackupInfoScreen.BackupInfoList backupInfoList;

    public RealmsBackupInfoScreen(Screen p_406449_, Backup p_409285_) {
        super(TITLE);
        this.lastScreen = p_406449_;
        this.backup = p_409285_;
    }

    @Override
    public void init() {
        this.layout.addTitleHeader(TITLE, this.font);
        this.backupInfoList = this.layout.addToContents(new RealmsBackupInfoScreen.BackupInfoList(this.minecraft));
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, p_408203_ -> this.onClose()).build());
        this.repositionElements();
        this.layout.visitWidgets(p_408227_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_408227_);
        });
    }

    @Override
    protected void repositionElements() {
        this.backupInfoList.setSize(this.width, this.layout.getContentHeight());
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    Component checkForSpecificMetadata(String p_406384_, String p_408935_) {
        String s = p_406384_.toLowerCase(Locale.ROOT);
        if (s.contains("game") && s.contains("mode")) {
            return this.gameModeMetadata(p_408935_);
        } else {
            return (Component)(s.contains("game") && s.contains("difficulty") ? this.gameDifficultyMetadata(p_408935_) : Component.literal(p_408935_));
        }
    }

    private Component gameDifficultyMetadata(String p_407114_) {
        try {
            return RealmsSlotOptionsScreen.DIFFICULTIES.get(Integer.parseInt(p_407114_)).getDisplayName();
        } catch (Exception exception) {
            return UNKNOWN;
        }
    }

    private Component gameModeMetadata(String p_406287_) {
        try {
            return RealmsSlotOptionsScreen.GAME_MODES.get(Integer.parseInt(p_406287_)).getShortDisplayName();
        } catch (Exception exception) {
            return UNKNOWN;
        }
    }

    @OnlyIn(Dist.CLIENT)
    class BackupInfoList extends ObjectSelectionList<RealmsBackupInfoScreen.BackupInfoListEntry> {
        public BackupInfoList(final Minecraft p_407391_) {
            super(
                p_407391_,
                RealmsBackupInfoScreen.this.width,
                RealmsBackupInfoScreen.this.layout.getContentHeight(),
                RealmsBackupInfoScreen.this.layout.getHeaderHeight(),
                36
            );
            if (RealmsBackupInfoScreen.this.backup.changeList != null) {
                RealmsBackupInfoScreen.this.backup
                    .changeList
                    .forEach((p_408494_, p_408350_) -> this.addEntry(RealmsBackupInfoScreen.this.new BackupInfoListEntry(p_408494_, p_408350_)));
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    class BackupInfoListEntry extends ObjectSelectionList.Entry<RealmsBackupInfoScreen.BackupInfoListEntry> {
        private static final Component TEMPLATE_NAME = Component.translatable("mco.backup.entry.templateName");
        private static final Component GAME_DIFFICULTY = Component.translatable("mco.backup.entry.gameDifficulty");
        private static final Component NAME = Component.translatable("mco.backup.entry.name");
        private static final Component GAME_SERVER_VERSION = Component.translatable("mco.backup.entry.gameServerVersion");
        private static final Component UPLOADED = Component.translatable("mco.backup.entry.uploaded");
        private static final Component ENABLED_PACK = Component.translatable("mco.backup.entry.enabledPack");
        private static final Component DESCRIPTION = Component.translatable("mco.backup.entry.description");
        private static final Component GAME_MODE = Component.translatable("mco.backup.entry.gameMode");
        private static final Component SEED = Component.translatable("mco.backup.entry.seed");
        private static final Component WORLD_TYPE = Component.translatable("mco.backup.entry.worldType");
        private static final Component UNDEFINED = Component.translatable("mco.backup.entry.undefined");
        private final String key;
        private final String value;

        public BackupInfoListEntry(final String p_406405_, final String p_409432_) {
            this.key = p_406405_;
            this.value = p_409432_;
        }

        @Override
        public void render(
            GuiGraphics p_410505_,
            int p_406862_,
            int p_406092_,
            int p_410158_,
            int p_409386_,
            int p_410684_,
            int p_409089_,
            int p_407829_,
            boolean p_407206_,
            float p_407799_
        ) {
            p_410505_.drawString(RealmsBackupInfoScreen.this.font, this.translateKey(this.key), p_410158_, p_406092_, -6250336);
            p_410505_.drawString(
                RealmsBackupInfoScreen.this.font, RealmsBackupInfoScreen.this.checkForSpecificMetadata(this.key, this.value), p_410158_, p_406092_ + 12, -1
            );
        }

        private Component translateKey(String p_406977_) {
            return switch (p_406977_) {
                case "template_name" -> TEMPLATE_NAME;
                case "game_difficulty" -> GAME_DIFFICULTY;
                case "name" -> NAME;
                case "game_server_version" -> GAME_SERVER_VERSION;
                case "uploaded" -> UPLOADED;
                case "enabled_packs" -> ENABLED_PACK;
                case "description" -> DESCRIPTION;
                case "game_mode" -> GAME_MODE;
                case "seed" -> SEED;
                case "world_type" -> WORLD_TYPE;
                default -> UNDEFINED;
            };
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", this.key + " " + this.value);
        }
    }
}
