package com.itamio.accountswitcher.mixin;

import com.itamio.accountswitcher.AccountSwitcherMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String prefix = "Account: ";
            String account = AccountSwitcherMod.getCurrentAccount();
            graphics.text(font, prefix, 10, 10, 0xFFFFFF);
            graphics.text(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Render error", e);
        }
    }
}
