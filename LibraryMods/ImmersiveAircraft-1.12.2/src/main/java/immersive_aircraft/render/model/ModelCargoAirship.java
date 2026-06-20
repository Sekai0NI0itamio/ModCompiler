package immersive_aircraft.render.model;

import net.minecraft.client.model.ModelRenderer;

/**
 * Cargo airship - a wider body to suggest cargo capacity.
 */
public class ModelCargoAirship extends ModelAircraft {
    public ModelCargoAirship() {
        super();

        body = new ModelRenderer(this, 0, 0);
        body.addBox(-12f, -10f, -22f, 24, 18, 44);
        body.setRotationPoint(0f, 0f, 0f);

        wingBottom = new ModelRenderer(this, 0, 32);
        wingBottom.addBox(-8f, 0f, -12f, 16, 5, 24);
        wingBottom.setRotationPoint(0f, 8f, 0f);

        tailFin = new ModelRenderer(this, 60, 0);
        tailFin.addBox(-0.5f, -7f, -1f, 1, 7, 2);
        tailFin.setRotationPoint(0f, -8f, 22f);

        tailWing = new ModelRenderer(this, 60, 10);
        tailWing.addBox(-5f, -0.5f, -1f, 10, 1, 2);
        tailWing.setRotationPoint(0f, -3f, 23f);

        propeller = new ModelRenderer(this, 80, 0);
        propeller.addBox(-0.5f, -6f, -0.5f, 1, 12, 1);
        propeller.setRotationPoint(-12f, 0f, 20f);
    }
}
