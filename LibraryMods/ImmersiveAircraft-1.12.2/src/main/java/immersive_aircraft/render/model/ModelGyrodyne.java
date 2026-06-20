package immersive_aircraft.render.model;

import net.minecraft.client.model.ModelRenderer;

/**
 * Gyrodyne: small body with an overhead rotor (modeled as the propeller slot).
 */
public class ModelGyrodyne extends ModelAircraft {
    public ModelGyrodyne() {
        super();

        body = new ModelRenderer(this, 0, 0);
        body.addBox(-6f, -3f, -6f, 12, 5, 12);
        body.setRotationPoint(0f, 4f, 0f);

        tailFin = new ModelRenderer(this, 60, 0);
        tailFin.addBox(-0.5f, -5f, -0.5f, 1, 5, 1);
        tailFin.setRotationPoint(0f, -2f, 5f);

        tailWing = new ModelRenderer(this, 60, 10);
        tailWing.addBox(-3f, -0.5f, -0.5f, 6, 1, 1);
        tailWing.setRotationPoint(0f, 2f, 5f);

        // "Propeller" is a horizontal rotor above the body in this design.
        propeller = new ModelRenderer(this, 80, 0);
        propeller.addBox(-10f, -0.5f, -0.5f, 20, 1, 1);
        propeller.setRotationPoint(0f, -3f, 0f);
    }
}
