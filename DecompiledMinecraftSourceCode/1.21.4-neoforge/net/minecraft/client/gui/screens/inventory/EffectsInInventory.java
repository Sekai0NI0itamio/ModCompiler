package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EffectsInInventory {
    private static final ResourceLocation EFFECT_BACKGROUND_LARGE_SPRITE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_large");
    private static final ResourceLocation EFFECT_BACKGROUND_SMALL_SPRITE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_small");
    private final AbstractContainerScreen<?> screen;
    private final Minecraft minecraft;
    @Nullable
    private MobEffectInstance hoveredEffect;

    public EffectsInInventory(AbstractContainerScreen<?> p_367800_) {
        this.screen = p_367800_;
        this.minecraft = Minecraft.getInstance();
    }

    public boolean canSeeEffects() {
        int i = this.screen.leftPos + this.screen.imageWidth + 2;
        int j = this.screen.width - i;
        return j >= 32;
    }

    public void renderEffects(GuiGraphics p_362146_, int p_370153_, int p_365612_) {
        this.hoveredEffect = null;
        int i = this.screen.leftPos + this.screen.imageWidth + 2;
        int j = this.screen.width - i;
        Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
        if (!collection.isEmpty() && j >= 32) {
            boolean flag = j >= 120;
            var event = net.minecraftforge.client.event.ForgeEventFactoryClient.onScreenEffectSize(this.screen, j, !flag, i);
            if (event == null) return;
            flag = !event.isCompact();
            i = event.getHorizontalOffset();
            int k = 33;
            if (collection.size() > 5) {
                k = 132 / (collection.size() - 1);
            }

            Iterable<MobEffectInstance> iterable = collection.stream().filter(net.minecraftforge.client.ForgeHooksClient::shouldRenderEffect).sorted().toList();
            this.renderBackgrounds(p_362146_, i, k, iterable, flag);
            this.renderIcons(p_362146_, i, k, iterable, flag);
            if (flag) {
                this.renderLabels(p_362146_, i, k, iterable);
            } else if (p_370153_ >= i && p_370153_ <= i + 33) {
                int l = this.screen.topPos;

                for (MobEffectInstance mobeffectinstance : iterable) {
                    if (p_365612_ >= l && p_365612_ <= l + k) {
                        this.hoveredEffect = mobeffectinstance;
                    }

                    l += k;
                }
            }
        }
    }

    public void renderTooltip(GuiGraphics p_406784_, int p_408351_, int p_407679_) {
        if (this.hoveredEffect != null) {
            List<Component> list = List.of(
                this.getEffectName(this.hoveredEffect), MobEffectUtil.formatDuration(this.hoveredEffect, 1.0F, this.minecraft.level.tickRateManager().tickrate())
            );
            p_406784_.setTooltipForNextFrame(this.screen.getFont(), list, Optional.empty(), p_408351_, p_407679_);
        }
    }

    private void renderBackgrounds(GuiGraphics p_363087_, int p_362702_, int p_362968_, Iterable<MobEffectInstance> p_366617_, boolean p_366522_) {
        int i = this.screen.topPos;

        for (MobEffectInstance mobeffectinstance : p_366617_) {
            if (p_366522_) {
                p_363087_.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_LARGE_SPRITE, p_362702_, i, 120, 32);
            } else {
                p_363087_.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_SMALL_SPRITE, p_362702_, i, 32, 32);
            }

            i += p_362968_;
        }
    }

    private void renderIcons(GuiGraphics p_367085_, int p_367644_, int p_367522_, Iterable<MobEffectInstance> p_361981_, boolean p_368681_) {
        int i = this.screen.topPos;

        for (MobEffectInstance mobeffectinstance : p_361981_) {
            var renderer = net.minecraftforge.client.extensions.common.IClientMobEffectExtensions.of(mobeffectinstance);
            if (renderer.renderInventoryIcon(mobeffectinstance, this, p_367085_, p_367644_ + (p_368681_ ? 6 : 7), i, 0)) {
                i += p_367522_;
                continue;
            }
            Holder<MobEffect> holder = mobeffectinstance.getEffect();
            ResourceLocation resourcelocation = Gui.getMobEffectSprite(holder);
            p_367085_.blitSprite(RenderPipelines.GUI_TEXTURED, resourcelocation, p_367644_ + (p_368681_ ? 6 : 7), i + 7, 18, 18);
            i += p_367522_;
        }
    }

    private void renderLabels(GuiGraphics p_361851_, int p_367468_, int p_365556_, Iterable<MobEffectInstance> p_365480_) {
        int i = this.screen.topPos;

        for (MobEffectInstance mobeffectinstance : p_365480_) {
            var renderer = net.minecraftforge.client.extensions.common.IClientMobEffectExtensions.of(mobeffectinstance);
            if (renderer.renderInventoryText(mobeffectinstance, this, p_361851_, p_367468_, i, 0)) {
                i += p_365556_;
                continue;
            }
            Component component = this.getEffectName(mobeffectinstance);
            p_361851_.drawString(this.screen.getFont(), component, p_367468_ + 10 + 18, i + 6, -1);
            Component component1 = MobEffectUtil.formatDuration(mobeffectinstance, 1.0F, this.minecraft.level.tickRateManager().tickrate());
            p_361851_.drawString(this.screen.getFont(), component1, p_367468_ + 10 + 18, i + 6 + 10, -8421505);
            i += p_365556_;
        }
    }

    private Component getEffectName(MobEffectInstance p_368169_) {
        MutableComponent mutablecomponent = p_368169_.getEffect().value().getDisplayName().copy();
        if (p_368169_.getAmplifier() >= 1 && p_368169_.getAmplifier() <= 9) {
            mutablecomponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (p_368169_.getAmplifier() + 1)));
        }

        return mutablecomponent;
    }

    public AbstractContainerScreen<?> getScreen() {
        return this.screen;
    }
}
