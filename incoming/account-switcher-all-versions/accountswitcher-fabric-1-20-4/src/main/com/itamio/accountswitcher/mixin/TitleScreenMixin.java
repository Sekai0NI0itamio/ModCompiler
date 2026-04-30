package com.itamio.accountswitcher.mixin;

import com.itamio.accountswitcher.AccountSwitcherMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
            String prefix = "Account: ";
            String account = AccountSwitcherMod.getCurrentAccount();
            context.drawText(tr, prefix, 10, 10, 0xFFFFFF, true);
            context.drawText(tr, account, 10 + tr.getWidth(prefix), 10, 0x00FF00, true);
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Render error", e);
        }
    }
}
