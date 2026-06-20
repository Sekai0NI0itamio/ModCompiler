package immersive_aircraft.render.renderer;

import immersive_aircraft.Main;
import immersive_aircraft.entity.AbstractAircraftEntity;
import immersive_aircraft.render.model.ModelAircraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Generic renderer that draws any of the {@link ModelAircraft} subclasses and
 * a per-aircraft texture. 1.12.2 Forge uses
 * {@link net.minecraftforge.fml.client.registry.RenderingRegistry} for
 * registration, which we do in {@link immersive_aircraft.proxy.ClientProxy}.
 */
@SideOnly(Side.CLIENT)
public class RenderAircraft extends Render<AbstractAircraftEntity> {
    private final ModelAircraft model;
    private final ResourceLocation texture;

    public RenderAircraft(RenderManager renderManager, ModelAircraft model, String textureName) {
        super(renderManager);
        this.model = model;
        this.texture = new ResourceLocation(Main.MODID, "textures/entity/" + textureName + ".png");
        this.shadowSize = 0.6f;
    }

    @Override
    public void doRender(AbstractAircraftEntity entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        // Match entity orientation. Yaw is inverted because ModelRenderer geometry
        // is drawn in "front facing" coordinates.
        GlStateManager.rotate(-entityYaw, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate((float) entity.getRoll(partialTicks), 0.0f, 0.0f, 1.0f);

        // Slight scale-down - 16px = 1 metre in ModelRenderer space
        float scale = 1.0f / 16.0f;
        GlStateManager.scale(scale, scale, scale);

        this.bindEntityTexture(entity);

        // Pass propeller rotation in
        model.propellerRotation = entity.getPropellerRotation(partialTicks);

        model.render(entity, 0f, 0f, 0f, 0f, 0f, 1.0f);

        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(AbstractAircraftEntity entity) {
        return texture;
    }
}
