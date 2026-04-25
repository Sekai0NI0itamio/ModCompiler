package net.minecraft.client.gui.components;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlayerSkinWidget extends AbstractWidget {
    private static final float MODEL_HEIGHT = 2.125F;
    private static final float FIT_SCALE = 0.97F;
    private static final float ROTATION_SENSITIVITY = 2.5F;
    private static final float DEFAULT_ROTATION_X = -5.0F;
    private static final float DEFAULT_ROTATION_Y = 30.0F;
    private static final float ROTATION_X_LIMIT = 50.0F;
    private final PlayerModel wideModel;
    private final PlayerModel slimModel;
    private final Supplier<PlayerSkin> skin;
    private float rotationX = -5.0F;
    private float rotationY = 30.0F;

    public PlayerSkinWidget(int p_299990_, int p_297411_, EntityModelSet p_298438_, Supplier<PlayerSkin> p_299497_) {
        super(0, 0, p_299990_, p_297411_, CommonComponents.EMPTY);
        this.wideModel = new PlayerModel(p_298438_.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel(p_298438_.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.skin = p_299497_;
    }

    @Override
    protected void renderWidget(GuiGraphics p_298610_, int p_299860_, int p_299420_, float p_300463_) {
        float f = 0.97F * this.getHeight() / 2.125F;
        float f1 = -1.0625F;
        PlayerSkin playerskin = this.skin.get();
        PlayerModel playermodel = playerskin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.wideModel;
        p_298610_.submitSkinRenderState(
            playermodel,
            playerskin.texture(),
            f,
            this.rotationX,
            this.rotationY,
            -1.0625F,
            this.getX(),
            this.getY(),
            this.getRight(),
            this.getBottom()
        );
    }

    @Override
    protected void onDrag(double p_301243_, double p_297441_, double p_301242_, double p_297777_) {
        this.rotationX = Mth.clamp(this.rotationX - (float)p_297777_ * 2.5F, -50.0F, 50.0F);
        this.rotationY += (float)p_301242_ * 2.5F;
    }

    @Override
    public void playDownSound(SoundManager p_299795_) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput p_298811_) {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent p_300388_) {
        return null;
    }
}
