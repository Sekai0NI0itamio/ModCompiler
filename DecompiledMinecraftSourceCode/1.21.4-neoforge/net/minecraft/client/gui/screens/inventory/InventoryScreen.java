package net.minecraft.client.gui.screens.inventory;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class InventoryScreen extends AbstractRecipeBookScreen<InventoryMenu> {
    private float xMouse;
    private float yMouse;
    private boolean buttonClicked;
    private final EffectsInInventory effects;

    public InventoryScreen(Player p_98839_) {
        super(p_98839_.inventoryMenu, new CraftingRecipeBookComponent(p_98839_.inventoryMenu), p_98839_.getInventory(), Component.translatable("container.crafting"));
        this.titleLabelX = 97;
        this.effects = new EffectsInInventory(this);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.minecraft.player.hasInfiniteMaterials()) {
            this.minecraft
                .setScreen(
                    new CreativeModeInventoryScreen(
                        this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), this.minecraft.options.operatorItemsTab().get()
                    )
                );
        }
    }

    @Override
    protected void init() {
        if (this.minecraft.player.hasInfiniteMaterials()) {
            this.minecraft
                .setScreen(
                    new CreativeModeInventoryScreen(
                        this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), this.minecraft.options.operatorItemsTab().get()
                    )
                );
        } else {
            super.init();
        }
    }

    @Override
    protected ScreenPosition getRecipeBookButtonPosition() {
        return new ScreenPosition(this.leftPos + 104, this.height / 2 - 22);
    }

    @Override
    protected void onRecipeBookButtonClick() {
        this.buttonClicked = true;
    }

    @Override
    protected void renderLabels(GuiGraphics p_281654_, int p_283517_, int p_283464_) {
        p_281654_.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, -12566464, false);
    }

    @Override
    public void render(GuiGraphics p_283246_, int p_98876_, int p_98877_, float p_98878_) {
        this.effects.renderEffects(p_283246_, p_98876_, p_98877_);
        super.render(p_283246_, p_98876_, p_98877_, p_98878_);
        this.effects.renderTooltip(p_283246_, p_98876_, p_98877_);
        this.xMouse = p_98876_;
        this.yMouse = p_98877_;
    }

    @Override
    public boolean showsActiveEffects() {
        return this.effects.canSeeEffects();
    }

    @Override
    protected boolean isBiggerResultSlot() {
        return false;
    }

    @Override
    protected void renderBg(GuiGraphics p_281500_, float p_281299_, int p_283481_, int p_281831_) {
        int i = this.leftPos;
        int j = this.topPos;
        p_281500_.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, i, j, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        renderEntityInInventoryFollowsMouse(p_281500_, i + 26, j + 8, i + 75, j + 78, 30, 0.0625F, this.xMouse, this.yMouse, this.minecraft.player);
    }

    public static void renderEntityInInventoryFollowsMouse(
        GuiGraphics p_282802_,
        int p_275688_,
        int p_275245_,
        int p_275535_,
        int p_301381_,
        int p_299741_,
        float p_275604_,
        float p_275546_,
        float p_300682_,
        LivingEntity p_275689_
    ) {
        float f = (p_275688_ + p_275535_) / 2.0F;
        float f1 = (p_275245_ + p_301381_) / 2.0F;
        p_282802_.enableScissor(p_275688_, p_275245_, p_275535_, p_301381_);
        float f2 = (float)Math.atan((f - p_275546_) / 40.0F);
        float f3 = (float)Math.atan((f1 - p_300682_) / 40.0F);
        Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf quaternionf1 = new Quaternionf().rotateX(f3 * 20.0F * (float) (Math.PI / 180.0));
        quaternionf.mul(quaternionf1);
        float f4 = p_275689_.yBodyRot;
        float f5 = p_275689_.getYRot();
        float f6 = p_275689_.getXRot();
        float f7 = p_275689_.yHeadRotO;
        float f8 = p_275689_.yHeadRot;
        p_275689_.yBodyRot = 180.0F + f2 * 20.0F;
        p_275689_.setYRot(180.0F + f2 * 40.0F);
        p_275689_.setXRot(-f3 * 20.0F);
        p_275689_.yHeadRot = p_275689_.getYRot();
        p_275689_.yHeadRotO = p_275689_.getYRot();
        float f9 = p_275689_.getScale();
        Vector3f vector3f = new Vector3f(0.0F, p_275689_.getBbHeight() / 2.0F + p_275604_ * f9, 0.0F);
        float f10 = p_299741_ / f9;
        renderEntityInInventory(p_282802_, p_275688_, p_275245_, p_275535_, p_301381_, f10, vector3f, quaternionf, quaternionf1, p_275689_);
        p_275689_.yBodyRot = f4;
        p_275689_.setYRot(f5);
        p_275689_.setXRot(f6);
        p_275689_.yHeadRotO = f7;
        p_275689_.yHeadRot = f8;
        p_282802_.disableScissor();
    }

    public static void renderEntityInInventory(
        GuiGraphics p_282665_,
        int p_407731_,
        int p_410143_,
        int p_407838_,
        int p_406338_,
        float p_300023_,
        Vector3f p_298037_,
        Quaternionf p_281880_,
        @Nullable Quaternionf p_282882_,
        LivingEntity p_282466_
    ) {
        EntityRenderDispatcher entityrenderdispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> entityrenderer = entityrenderdispatcher.getRenderer(p_282466_);
        EntityRenderState entityrenderstate = entityrenderer.createRenderState(p_282466_, 1.0F);
        entityrenderstate.hitboxesRenderState = null;
        p_282665_.submitEntityRenderState(entityrenderstate, p_300023_, p_298037_, p_281880_, p_282882_, p_407731_, p_410143_, p_407838_, p_406338_);
    }

    @Override
    public boolean mouseReleased(double p_98893_, double p_98894_, int p_98895_) {
        if (this.buttonClicked) {
            this.buttonClicked = false;
            return true;
        } else {
            return super.mouseReleased(p_98893_, p_98894_, p_98895_);
        }
    }
}
