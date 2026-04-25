package com.mojang.realmsclient.gui.screens.configuration;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.Ops;
import com.mojang.realmsclient.dto.PlayerInfo;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.screens.RealmsConfirmScreen;
import com.mojang.realmsclient.util.RealmsUtil;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
class RealmsPlayersTab extends GridLayoutTab implements RealmsConfigurationTab {
    static final Logger LOGGER = LogUtils.getLogger();
    static final Component TITLE = Component.translatable("mco.configure.world.players.title");
    static final Component QUESTION_TITLE = Component.translatable("mco.question");
    private static final int PADDING = 8;
    final RealmsConfigureWorldScreen configurationScreen;
    final Minecraft minecraft;
    RealmsServer serverData;
    private final RealmsPlayersTab.InvitedObjectSelectionList invitedList;

    RealmsPlayersTab(RealmsConfigureWorldScreen p_410606_, Minecraft p_409396_, RealmsServer p_410131_) {
        super(TITLE);
        this.configurationScreen = p_410606_;
        this.minecraft = p_409396_;
        this.serverData = p_410131_;
        GridLayout.RowHelper gridlayout$rowhelper = this.layout.spacing(8).createRowHelper(1);
        this.invitedList = gridlayout$rowhelper.addChild(
            new RealmsPlayersTab.InvitedObjectSelectionList(p_410606_.width, this.calculateListHeight()), LayoutSettings.defaults().alignVerticallyTop().alignHorizontallyCenter()
        );
        gridlayout$rowhelper.addChild(
            Button.builder(
                    Component.translatable("mco.configure.world.buttons.invite"), p_405944_ -> p_409396_.setScreen(new RealmsInviteScreen(p_410606_, p_410131_))
                )
                .build(),
            LayoutSettings.defaults().alignVerticallyBottom().alignHorizontallyCenter()
        );
        this.updateData(p_410131_);
    }

    public int calculateListHeight() {
        return this.configurationScreen.getContentHeight() - 20 - 16;
    }

    @Override
    public void doLayout(ScreenRectangle p_409000_) {
        this.invitedList.setSize(this.configurationScreen.width, this.calculateListHeight());
        super.doLayout(p_409000_);
    }

    @Override
    public void updateData(RealmsServer p_408665_) {
        this.serverData = p_408665_;
        this.invitedList.children().clear();

        for (PlayerInfo playerinfo : p_408665_.players) {
            this.invitedList.children().add(new RealmsPlayersTab.Entry(playerinfo));
        }
    }

    @OnlyIn(Dist.CLIENT)
    class Entry extends ContainerObjectSelectionList.Entry<RealmsPlayersTab.Entry> {
        protected static final int SKIN_FACE_SIZE = 32;
        private static final Component NORMAL_USER_TEXT = Component.translatable("mco.configure.world.invites.normal.tooltip");
        private static final Component OP_TEXT = Component.translatable("mco.configure.world.invites.ops.tooltip");
        private static final Component REMOVE_TEXT = Component.translatable("mco.configure.world.invites.remove.tooltip");
        private static final ResourceLocation MAKE_OP_SPRITE = ResourceLocation.withDefaultNamespace("player_list/make_operator");
        private static final ResourceLocation REMOVE_OP_SPRITE = ResourceLocation.withDefaultNamespace("player_list/remove_operator");
        private static final ResourceLocation REMOVE_PLAYER_SPRITE = ResourceLocation.withDefaultNamespace("player_list/remove_player");
        private static final int ICON_WIDTH = 8;
        private static final int ICON_HEIGHT = 7;
        private final PlayerInfo playerInfo;
        private final Button removeButton;
        private final Button makeOpButton;
        private final Button removeOpButton;

        public Entry(final PlayerInfo p_406992_) {
            this.playerInfo = p_406992_;
            int i = RealmsPlayersTab.this.serverData.players.indexOf(this.playerInfo);
            this.makeOpButton = SpriteIconButton.builder(NORMAL_USER_TEXT, p_407601_ -> this.op(i), false)
                .sprite(MAKE_OP_SPRITE, 8, 7)
                .width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(NORMAL_USER_TEXT))
                .narration(
                    p_407141_ -> CommonComponents.joinForNarration(
                        Component.translatable("mco.invited.player.narration", p_406992_.getName()),
                        p_407141_.get(),
                        Component.translatable("narration.cycle_button.usage.focused", OP_TEXT)
                    )
                )
                .build();
            this.removeOpButton = SpriteIconButton.builder(OP_TEXT, p_407054_ -> this.deop(i), false)
                .sprite(REMOVE_OP_SPRITE, 8, 7)
                .width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(OP_TEXT))
                .narration(
                    p_409750_ -> CommonComponents.joinForNarration(
                        Component.translatable("mco.invited.player.narration", p_406992_.getName()),
                        p_409750_.get(),
                        Component.translatable("narration.cycle_button.usage.focused", NORMAL_USER_TEXT)
                    )
                )
                .build();
            this.removeButton = SpriteIconButton.builder(REMOVE_TEXT, p_409521_ -> this.uninvite(i), false)
                .sprite(REMOVE_PLAYER_SPRITE, 8, 7)
                .width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(REMOVE_TEXT))
                .narration(p_406682_ -> CommonComponents.joinForNarration(Component.translatable("mco.invited.player.narration", p_406992_.getName()), p_406682_.get()))
                .build();
            this.updateOpButtons();
        }

        private void op(int p_406828_) {
            UUID uuid = RealmsPlayersTab.this.serverData.players.get(p_406828_).getUuid();
            RealmsUtil.<Ops>supplyAsync(
                    p_406803_ -> p_406803_.op(RealmsPlayersTab.this.serverData.id, uuid),
                    p_408042_ -> RealmsPlayersTab.LOGGER.error("Couldn't op the user", (Throwable)p_408042_)
                )
                .thenAcceptAsync(p_410719_ -> {
                    this.updateOps(p_410719_);
                    this.updateOpButtons();
                    this.setFocused(this.removeOpButton);
                }, RealmsPlayersTab.this.minecraft);
        }

        private void deop(int p_409714_) {
            UUID uuid = RealmsPlayersTab.this.serverData.players.get(p_409714_).getUuid();
            RealmsUtil.<Ops>supplyAsync(
                    p_409583_ -> p_409583_.deop(RealmsPlayersTab.this.serverData.id, uuid),
                    p_406309_ -> RealmsPlayersTab.LOGGER.error("Couldn't deop the user", (Throwable)p_406309_)
                )
                .thenAcceptAsync(p_407418_ -> {
                    this.updateOps(p_407418_);
                    this.updateOpButtons();
                    this.setFocused(this.makeOpButton);
                }, RealmsPlayersTab.this.minecraft);
        }

        private void uninvite(int p_407605_) {
            if (p_407605_ >= 0 && p_407605_ < RealmsPlayersTab.this.serverData.players.size()) {
                PlayerInfo playerinfo = RealmsPlayersTab.this.serverData.players.get(p_407605_);
                RealmsConfirmScreen realmsconfirmscreen = new RealmsConfirmScreen(
                    p_406484_ -> {
                        if (p_406484_) {
                            RealmsUtil.runAsync(
                                p_408184_ -> p_408184_.uninvite(RealmsPlayersTab.this.serverData.id, playerinfo.getUuid()),
                                p_409177_ -> RealmsPlayersTab.LOGGER.error("Couldn't uninvite user", (Throwable)p_409177_)
                            );
                            RealmsPlayersTab.this.serverData.players.remove(p_407605_);
                            RealmsPlayersTab.this.updateData(RealmsPlayersTab.this.serverData);
                        }

                        RealmsPlayersTab.this.minecraft.setScreen(RealmsPlayersTab.this.configurationScreen);
                    },
                    RealmsPlayersTab.QUESTION_TITLE,
                    Component.translatable("mco.configure.world.uninvite.player", playerinfo.getName())
                );
                RealmsPlayersTab.this.minecraft.setScreen(realmsconfirmscreen);
            }
        }

        private void updateOps(Ops p_409742_) {
            for (PlayerInfo playerinfo : RealmsPlayersTab.this.serverData.players) {
                playerinfo.setOperator(p_409742_.ops.contains(playerinfo.getName()));
            }
        }

        private void updateOpButtons() {
            this.makeOpButton.visible = !this.playerInfo.isOperator();
            this.removeOpButton.visible = !this.makeOpButton.visible;
        }

        private Button activeOpButton() {
            return this.makeOpButton.visible ? this.makeOpButton : this.removeOpButton;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.activeOpButton(), this.removeButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.activeOpButton(), this.removeButton);
        }

        @Override
        public void render(
            GuiGraphics p_409169_,
            int p_408504_,
            int p_407317_,
            int p_410552_,
            int p_406470_,
            int p_408412_,
            int p_409820_,
            int p_408001_,
            boolean p_410718_,
            float p_408309_
        ) {
            int i;
            if (!this.playerInfo.getAccepted()) {
                i = -6250336;
            } else if (this.playerInfo.getOnline()) {
                i = -16711936;
            } else {
                i = -1;
            }

            int j = p_407317_ + p_408412_ / 2 - 16;
            RealmsUtil.renderPlayerFace(p_409169_, p_410552_, j, 32, this.playerInfo.getUuid());
            int k = p_407317_ + p_408412_ / 2 - 9 / 2;
            p_409169_.drawString(RealmsPlayersTab.this.configurationScreen.getFont(), this.playerInfo.getName(), p_410552_ + 8 + 32, k, i);
            int l = p_407317_ + p_408412_ / 2 - 10;
            int i1 = p_410552_ + p_406470_ - this.removeButton.getWidth();
            this.removeButton.setPosition(i1, l);
            this.removeButton.render(p_409169_, p_409820_, p_408001_, p_408309_);
            int j1 = i1 - this.activeOpButton().getWidth() - 8;
            this.makeOpButton.setPosition(j1, l);
            this.makeOpButton.render(p_409169_, p_409820_, p_408001_, p_408309_);
            this.removeOpButton.setPosition(j1, l);
            this.removeOpButton.render(p_409169_, p_409820_, p_408001_, p_408309_);
        }
    }

    @OnlyIn(Dist.CLIENT)
    class InvitedObjectSelectionList extends ContainerObjectSelectionList<RealmsPlayersTab.Entry> {
        private static final int ITEM_HEIGHT = 36;

        public InvitedObjectSelectionList(final int p_406778_, final int p_406806_) {
            super(Minecraft.getInstance(), p_406778_, p_406806_, RealmsPlayersTab.this.configurationScreen.getHeaderHeight(), 36, (int)(9.0F * 1.5F));
        }

        @Override
        protected void renderHeader(GuiGraphics p_408719_, int p_408562_, int p_409176_) {
            String s = RealmsPlayersTab.this.serverData.players != null ? Integer.toString(RealmsPlayersTab.this.serverData.players.size()) : "0";
            Component component = Component.translatable("mco.configure.world.invited.number", s).withStyle(ChatFormatting.UNDERLINE);
            p_408719_.drawString(
                RealmsPlayersTab.this.configurationScreen.getFont(),
                component,
                p_408562_ + this.getRowWidth() / 2 - RealmsPlayersTab.this.configurationScreen.getFont().width(component) / 2,
                p_409176_,
                -1
            );
        }

        @Override
        protected void renderListBackground(GuiGraphics p_407111_) {
        }

        @Override
        protected void renderListSeparators(GuiGraphics p_410636_) {
        }

        @Override
        public int getRowWidth() {
            return 300;
        }
    }
}
