package net.minecraft.client.gui.components;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SplashRenderer {
    public static final SplashRenderer CHRISTMAS = new SplashRenderer("Merry X-mas!");
    public static final SplashRenderer NEW_YEAR = new SplashRenderer("Happy new year!");
    public static final SplashRenderer HALLOWEEN = new SplashRenderer("OOoooOOOoooo! Spooky!");
    private static final int WIDTH_OFFSET = 123;
    private static final int HEIGH_OFFSET = 69;
    private final String splash;

    public SplashRenderer(String p_283500_) {
        this.splash = p_283500_;
    }

    public void render(GuiGraphics p_282218_, int p_281824_, Font p_281962_, float p_410206_) {
        p_282218_.pose().pushMatrix();
        p_282218_.pose().translate(p_281824_ / 2.0F + 123.0F, 69.0F);
        p_282218_.pose().rotate((float) (-Math.PI / 9));
        float f = 1.8F - Mth.abs(Mth.sin((float)(Util.getMillis() % 1000L) / 1000.0F * (float) (Math.PI * 2)) * 0.1F);
        f = f * 100.0F / (p_281962_.width(this.splash) + 32);
        p_282218_.pose().scale(f, f);
        p_282218_.drawCenteredString(p_281962_, this.splash, 0, -8, ARGB.color(p_410206_, -256));
        p_282218_.pose().popMatrix();
    }
}
