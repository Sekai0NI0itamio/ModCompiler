package com.itamio.accountswitcher.mixin;

import com.itamio.accountswitcher.AccountSwitcherMod;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
            String prefix = "Account: ";
            String account = AccountSwitcherMod.getCurrentAccount();
            tr.drawWithShadow(matrices, prefix, 10, 10, 0xFFFFFF);
            tr.drawWithShadow(matrices, account, 10 + tr.getWidth(prefix), 10, 0x00FF00);
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Render error", e);
        }
    }
}
