package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.Backup;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen;
import com.mojang.realmsclient.gui.screens.RealmsPopups;
import com.mojang.realmsclient.util.RealmsUtil;
import com.mojang.realmsclient.util.task.DownloadTask;
import com.mojang.realmsclient.util.task.RestoreTask;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsBackupScreen extends RealmsScreen {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.configure.world.backup");
    static final Component RESTORE_TOOLTIP = Component.translatable("mco.backup.button.restore");
    static final Component HAS_CHANGES_TOOLTIP = Component.translatable("mco.backup.changes.tooltip");
    private static final Component NO_BACKUPS_LABEL = Component.translatable("mco.backup.nobackups");
    private static final Component DOWNLOAD_LATEST = Component.translatable("mco.backup.button.download");
    private static final String UPLOADED_KEY = "uploaded";
    private static final int PADDING = 8;
    final RealmsConfigureWorldScreen lastScreen;
    List<Backup> backups = Collections.emptyList();
    @Nullable
    RealmsBackupScreen.BackupObjectSelectionList backupList;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final int slotId;
    @Nullable
    Button downloadButton;
    final RealmsServer serverData;
    boolean noBackups = false;

    public RealmsBackupScreen(RealmsConfigureWorldScreen p_407560_, RealmsServer p_408887_, int p_407165_) {
        super(TITLE);
        this.lastScreen = p_407560_;
        this.serverData = p_408887_;
        this.slotId = p_407165_;
    }

    @Override
    public void init() {
        this.layout.addTitleHeader(TITLE, this.font);
        this.backupList = this.layout.addToContents(new RealmsBackupScreen.BackupObjectSelectionList());
        LinearLayout linearlayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.downloadButton = linearlayout.addChild(Button.builder(DOWNLOAD_LATEST, p_409070_ -> this.downloadClicked()).build());
        this.downloadButton.active = false;
        linearlayout.addChild(Button.builder(CommonComponents.GUI_BACK, p_408429_ -> this.onClose()).build());
        this.layout.visitWidgets(p_406978_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_406978_);
        });
        this.repositionElements();
        this.fetchRealmsBackups();
    }

    @Override
    public void render(GuiGraphics p_409181_, int p_407612_, int p_406342_, float p_407477_) {
        super.render(p_409181_, p_407612_, p_406342_, p_407477_);
        if (this.noBackups && this.backupList != null) {
            p_409181_.drawString(
                this.font,
                NO_BACKUPS_LABEL,
                this.width / 2 - this.font.width(NO_BACKUPS_LABEL) / 2,
                this.backupList.getY() + this.backupList.getHeight() / 2 - 9 / 2,
                -1
            );
        }
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.backupList != null) {
            this.backupList.updateSize(this.width, this.layout);
        }
    }

    private void fetchRealmsBackups() {
        (new Thread("Realms-fetch-backups") {
                @Override
                public void run() {
                    RealmsClient realmsclient = RealmsClient.getOrCreate();

                    try {
                        List<Backup> list = realmsclient.backupsFor(RealmsBackupScreen.this.serverData.id).backups;
                        RealmsBackupScreen.this.minecraft
                            .execute(
                                () -> {
                                    RealmsBackupScreen.this.backups = list;
                                    RealmsBackupScreen.this.noBackups = RealmsBackupScreen.this.backups.isEmpty();
                                    if (!RealmsBackupScreen.this.noBackups && RealmsBackupScreen.this.downloadButton != null) {
                                        RealmsBackupScreen.this.downloadButton.active = true;
                                    }

                                    if (RealmsBackupScreen.this.backupList != null) {
                                        RealmsBackupScreen.this.backupList
                                            .replaceEntries(
                                                RealmsBackupScreen.this.backups
                                                    .stream()
                                                    .map(p_409880_ -> RealmsBackupScreen.this.new Entry(p_409880_))
                                                    .toList()
                                            );
                                    }
                                }
                            );
                    } catch (RealmsServiceException realmsserviceexception) {
                        RealmsBackupScreen.LOGGER.error("Couldn't request backups", (Throwable)realmsserviceexception);
                    }
                }
            })
            .start();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private void downloadClicked() {
        this.minecraft
            .setScreen(
                RealmsPopups.infoPopupScreen(
                    this,
                    Component.translatable("mco.configure.world.restore.download.question.line1"),
                    p_408338_ -> this.minecraft
                        .setScreen(
                            new RealmsLongRunningMcoTaskScreen(
                                this.lastScreen.getNewScreen(),
                                new DownloadTask(
                                    this.serverData.id,
                                    this.slotId,
                                    Objects.requireNonNullElse(this.serverData.name, "")
                                        + " ("
                                        + this.serverData.slots.get(this.serverData.activeSlot).options.getSlotName(this.serverData.activeSlot)
                                        + ")",
                                    this
                                )
                            )
                        )
                )
            );
    }

    @OnlyIn(Dist.CLIENT)
    class BackupObjectSelectionList extends ContainerObjectSelectionList<RealmsBackupScreen.Entry> {
        private static final int ITEM_HEIGHT = 36;

        public BackupObjectSelectionList() {
            super(
                Minecraft.getInstance(),
                RealmsBackupScreen.this.width,
                RealmsBackupScreen.this.layout.getContentHeight(),
                RealmsBackupScreen.this.layout.getHeaderHeight(),
                36
            );
        }

        @Override
        public int getRowWidth() {
            return 300;
        }
    }

    @OnlyIn(Dist.CLIENT)
    class Entry extends ContainerObjectSelectionList.Entry<RealmsBackupScreen.Entry> {
        private static final int Y_PADDING = 2;
        private final Backup backup;
        @Nullable
        private Button restoreButton;
        @Nullable
        private Button changesButton;
        private final List<AbstractWidget> children = new ArrayList<>();

        public Entry(final Backup p_410748_) {
            this.backup = p_410748_;
            this.populateChangeList(p_410748_);
            if (!p_410748_.changeList.isEmpty()) {
                this.changesButton = Button.builder(
                        RealmsBackupScreen.HAS_CHANGES_TOOLTIP,
                        p_410731_ -> RealmsBackupScreen.this.minecraft.setScreen(new RealmsBackupInfoScreen(RealmsBackupScreen.this, this.backup))
                    )
                    .width(8 + RealmsBackupScreen.this.font.width(RealmsBackupScreen.HAS_CHANGES_TOOLTIP))
                    .createNarration(p_406849_ -> CommonComponents.joinForNarration(Component.translatable("mco.backup.narration", this.getShortBackupDate()), p_406849_.get()))
                    .build();
                this.children.add(this.changesButton);
            }

            if (!RealmsBackupScreen.this.serverData.expired) {
                this.restoreButton = Button.builder(RealmsBackupScreen.RESTORE_TOOLTIP, p_406942_ -> this.restoreClicked())
                    .width(8 + RealmsBackupScreen.this.font.width(RealmsBackupScreen.HAS_CHANGES_TOOLTIP))
                    .createNarration(p_406732_ -> CommonComponents.joinForNarration(Component.translatable("mco.backup.narration", this.getShortBackupDate()), p_406732_.get()))
                    .build();
                this.children.add(this.restoreButton);
            }
        }

        private void populateChangeList(Backup p_407265_) {
            int i = RealmsBackupScreen.this.backups.indexOf(p_407265_);
            if (i != RealmsBackupScreen.this.backups.size() - 1) {
                Backup backup = RealmsBackupScreen.this.backups.get(i + 1);

                for (String s : p_407265_.metadata.keySet()) {
                    if (!s.contains("uploaded") && backup.metadata.containsKey(s)) {
                        if (!p_407265_.metadata.get(s).equals(backup.metadata.get(s))) {
                            this.addToChangeList(s);
                        }
                    } else {
                        this.addToChangeList(s);
                    }
                }
            }
        }

        private void addToChangeList(String p_409547_) {
            if (p_409547_.contains("uploaded")) {
                String s = DateFormat.getDateTimeInstance(3, 3).format(this.backup.lastModifiedDate);
                this.backup.changeList.put(p_409547_, s);
                this.backup.setUploadedVersion(true);
            } else {
                this.backup.changeList.put(p_409547_, this.backup.metadata.get(p_409547_));
            }
        }

        private String getShortBackupDate() {
            return DateFormat.getDateTimeInstance(3, 3).format(this.backup.lastModifiedDate);
        }

        private void restoreClicked() {
            Component component = RealmsUtil.convertToAgePresentationFromInstant(this.backup.lastModifiedDate);
            Component component1 = Component.translatable("mco.configure.world.restore.question.line1", this.getShortBackupDate(), component);
            RealmsBackupScreen.this.minecraft
                .setScreen(
                    RealmsPopups.warningPopupScreen(
                        RealmsBackupScreen.this,
                        component1,
                        p_407367_ -> {
                            RealmsConfigureWorldScreen realmsconfigureworldscreen = RealmsBackupScreen.this.lastScreen.getNewScreen();
                            RealmsBackupScreen.this.minecraft
                                .setScreen(
                                    new RealmsLongRunningMcoTaskScreen(
                                        realmsconfigureworldscreen,
                                        new RestoreTask(this.backup, RealmsBackupScreen.this.serverData.id, realmsconfigureworldscreen)
                                    )
                                );
                        }
                    )
                );
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.children;
        }

        @Override
        public void render(
            GuiGraphics p_406532_,
            int p_410492_,
            int p_409261_,
            int p_410222_,
            int p_408576_,
            int p_408283_,
            int p_409672_,
            int p_407122_,
            boolean p_408842_,
            float p_409008_
        ) {
            int i = p_409261_ + p_408283_ / 2;
            int j = i - 9 - 2;
            int k = i + 2;
            int l = this.backup.isUploadedVersion() ? -8388737 : -1;
            p_406532_.drawString(
                RealmsBackupScreen.this.font, Component.translatable("mco.backup.entry", RealmsUtil.convertToAgePresentationFromInstant(this.backup.lastModifiedDate)), p_410222_, j, l
            );
            p_406532_.drawString(RealmsBackupScreen.this.font, this.getMediumDatePresentation(this.backup.lastModifiedDate), p_410222_, k, -11776948);
            int i1 = 0;
            int j1 = p_409261_ + p_408283_ / 2 - 10;
            if (this.restoreButton != null) {
                i1 += this.restoreButton.getWidth() + 8;
                this.restoreButton.setX(p_410222_ + p_408576_ - i1);
                this.restoreButton.setY(j1);
                this.restoreButton.render(p_406532_, p_409672_, p_407122_, p_409008_);
            }

            if (this.changesButton != null) {
                i1 += this.changesButton.getWidth() + 8;
                this.changesButton.setX(p_410222_ + p_408576_ - i1);
                this.changesButton.setY(j1);
                this.changesButton.render(p_406532_, p_409672_, p_407122_, p_409008_);
            }
        }

        private String getMediumDatePresentation(Date p_409044_) {
            return DateFormat.getDateTimeInstance(3, 3).format(p_409044_);
        }
    }
}
