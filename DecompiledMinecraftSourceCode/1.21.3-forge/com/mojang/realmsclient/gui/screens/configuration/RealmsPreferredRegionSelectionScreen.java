package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.realmsclient.dto.RealmsRegion;
import com.mojang.realmsclient.dto.RegionSelectionPreference;
import com.mojang.realmsclient.dto.ServiceQuality;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsPreferredRegionSelectionScreen extends Screen {
    private static final Component REGION_SELECTION_LABEL = Component.translatable("mco.configure.world.region_preference.title");
    private static final int SPACING = 8;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final Screen parent;
    private final BiConsumer<RegionSelectionPreference, RealmsRegion> applySettings;
    final Map<RealmsRegion, ServiceQuality> regionServiceQuality;
    @Nullable
    private RealmsPreferredRegionSelectionScreen.RegionSelectionList list;
    RealmsSettingsTab.RegionSelection selection;
    @Nullable
    private Button doneButton;

    public RealmsPreferredRegionSelectionScreen(
        Screen p_406007_,
        BiConsumer<RegionSelectionPreference, RealmsRegion> p_410236_,
        Map<RealmsRegion, ServiceQuality> p_409174_,
        RealmsSettingsTab.RegionSelection p_410387_
    ) {
        super(REGION_SELECTION_LABEL);
        this.parent = p_406007_;
        this.applySettings = p_410236_;
        this.regionServiceQuality = p_409174_;
        this.selection = p_410387_;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    protected void init() {
        LinearLayout linearlayout = this.layout.addToHeader(LinearLayout.vertical().spacing(8));
        linearlayout.defaultCellSetting().alignHorizontallyCenter();
        linearlayout.addChild(new StringWidget(this.getTitle(), this.font));
        this.list = this.layout.addToContents(new RealmsPreferredRegionSelectionScreen.RegionSelectionList());
        LinearLayout linearlayout1 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.doneButton = linearlayout1.addChild(Button.builder(CommonComponents.GUI_DONE, p_410343_ -> {
            this.applySettings.accept(this.selection.preference(), this.selection.region());
            this.onClose();
        }).build());
        linearlayout1.addChild(Button.builder(CommonComponents.GUI_CANCEL, p_409859_ -> this.onClose()).build());
        this.list
            .setSelected(this.list.children().stream().filter(p_409558_ -> Objects.equals(p_409558_.regionSelection, this.selection)).findFirst().orElse(null));
        this.layout.visitWidgets(p_409709_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_409709_);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.list.updateSize(this.width, this.layout);
    }

    void updateButtonValidity() {
        this.doneButton.active = this.list.getSelected() != null;
    }

    @OnlyIn(Dist.CLIENT)
    class RegionSelectionList extends ObjectSelectionList<RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry> {
        RegionSelectionList() {
            super(
                RealmsPreferredRegionSelectionScreen.this.minecraft,
                RealmsPreferredRegionSelectionScreen.this.width,
                RealmsPreferredRegionSelectionScreen.this.height - 77,
                40,
                16
            );
            this.addEntry(new RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry(RegionSelectionPreference.AUTOMATIC_PLAYER, null));
            this.addEntry(new RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry(RegionSelectionPreference.AUTOMATIC_OWNER, null));
            RealmsPreferredRegionSelectionScreen.this.regionServiceQuality
                .keySet()
                .stream()
                .map(p_410173_ -> new RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry(RegionSelectionPreference.MANUAL, p_410173_))
                .forEach(p_406997_ -> this.addEntry(p_406997_));
        }

        public void setSelected(@Nullable RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry p_407101_) {
            super.setSelected(p_407101_);
            if (p_407101_ != null) {
                RealmsPreferredRegionSelectionScreen.this.selection = p_407101_.regionSelection;
            }

            RealmsPreferredRegionSelectionScreen.this.updateButtonValidity();
        }

        @OnlyIn(Dist.CLIENT)
        class Entry extends ObjectSelectionList.Entry<RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry> {
            final RealmsSettingsTab.RegionSelection regionSelection;
            private final Component name;

            public Entry(final RegionSelectionPreference p_409748_, @Nullable final RealmsRegion p_407499_) {
                this(new RealmsSettingsTab.RegionSelection(p_409748_, p_407499_));
            }

            public Entry(final RealmsSettingsTab.RegionSelection p_406876_) {
                this.regionSelection = p_406876_;
                if (p_406876_.preference() == RegionSelectionPreference.MANUAL) {
                    if (p_406876_.region() != null) {
                        this.name = Component.translatable(p_406876_.region().translationKey);
                    } else {
                        this.name = Component.empty();
                    }
                } else {
                    this.name = Component.translatable(p_406876_.preference().translationKey);
                }
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", this.name);
            }

            @Override
            public void render(
                GuiGraphics p_407830_,
                int p_407191_,
                int p_407184_,
                int p_406708_,
                int p_410741_,
                int p_406733_,
                int p_410600_,
                int p_407216_,
                boolean p_409098_,
                float p_406693_
            ) {
                p_407830_.drawString(RealmsPreferredRegionSelectionScreen.this.font, this.name, p_406708_ + 5, p_407184_ + 2, -1);
                if (this.regionSelection.region() != null && RealmsPreferredRegionSelectionScreen.this.regionServiceQuality.containsKey(this.regionSelection.region())) {
                    ServiceQuality servicequality = RealmsPreferredRegionSelectionScreen.this.regionServiceQuality
                        .getOrDefault(this.regionSelection.region(), ServiceQuality.UNKNOWN);
                    p_407830_.blitSprite(RenderPipelines.GUI_TEXTURED, servicequality.getIcon(), p_406708_ + p_410741_ - 18, p_407184_ + 2, 10, 8);
                }
            }

            @Override
            public boolean mouseClicked(double p_408514_, double p_407661_, int p_406178_) {
                RegionSelectionList.this.setSelected(this);
                return super.mouseClicked(p_408514_, p_407661_, p_406178_);
            }
        }
    }
}
