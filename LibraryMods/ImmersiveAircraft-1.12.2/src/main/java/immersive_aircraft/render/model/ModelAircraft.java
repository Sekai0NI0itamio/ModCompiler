package immersive_aircraft.render.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Base aircraft model that draws the body, wings, tail, and a propeller. The
 * propeller rotates based on the entity's engine power.
 */
@SideOnly(Side.CLIENT)
public class ModelAircraft extends ModelBase {
    public ModelRenderer body;
    public ModelRenderer wingTop;
    public ModelRenderer wingBottom;
    public ModelRenderer tailFin;
    public ModelRenderer tailWing;
    public ModelRenderer propeller;

    public float propellerRotation = 0.0f;

    public ModelAircraft() {
        this.textureWidth = 128;
        this.textureHeight = 64;
    }

    @Override
    public void render(Entity entityIn, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entityIn);

        if (body != null) body.render(scale);
        if (wingTop != null) wingTop.render(scale);
        if (wingBottom != null) wingBottom.render(scale);
        if (tailFin != null) tailFin.render(scale);
        if (tailWing != null) tailWing.render(scale);
        if (propeller != null) {
            propeller.rotateAngleZ = (float) Math.toRadians(propellerRotation);
            propeller.render(scale);
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale, Entity entityIn) {
        // Subclasses can animate roll/rotation if desired
    }
}
