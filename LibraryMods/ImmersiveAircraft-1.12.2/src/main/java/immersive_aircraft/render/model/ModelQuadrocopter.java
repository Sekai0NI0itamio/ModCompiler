package immersive_aircraft.render.model;

import net.minecraft.client.model.ModelRenderer;

/**
 * Quadrocopter: stubby body with four short rotor arms (modelled as a plus
 * sign via the wingTop/wingBottom slots).
 */
public class ModelQuadrocopter extends ModelAircraft {
    public ModelQuadrocopter() {
        super();

        body = new ModelRenderer(this, 0, 0);
        body.addBox(-4f, -2f, -4f, 8, 4, 8);
        body.setRotationPoint(0f, 4f, 0f);

        // Rotor arms - one slot represents the +X arm, the other the -X arm
        wingTop = new ModelRenderer(this, 30, 0);
        wingTop.addBox(-6f, -0.5f, -0.5f, 6, 1, 1);
        wingTop.setRotationPoint(4f, 4f, 0f);

        wingBottom = new ModelRenderer(this, 30, 0);
        wingBottom.addBox(0f, -0.5f, -0.5f, 6, 1, 1);
        wingBottom.setRotationPoint(-4f, 4f, 0f);

        // Tail arm - represented via the tailFin slot
        tailFin = new ModelRenderer(this, 30, 0);
        tailFin.addBox(-0.5f, -0.5f, 0f, 1, 1, 6);
        tailFin.setRotationPoint(0f, 4f, 4f);

        // Spinning disc - used as the "propeller" model slot
        propeller = new ModelRenderer(this, 80, 0);
        propeller.addBox(-6f, -0.5f, -0.5f, 12, 1, 1);
        propeller.setRotationPoint(0f, 2f, 0f);
    }
}
