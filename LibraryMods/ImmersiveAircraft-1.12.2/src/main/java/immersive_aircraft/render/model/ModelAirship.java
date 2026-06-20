package immersive_aircraft.render.model;

import net.minecraft.client.model.ModelRenderer;

/**
 * Airship model: long, oval balloon body with a gondola underneath and tail
 * fins. No spinning prop - we use a single rear propeller.
 */
public class ModelAirship extends ModelAircraft {
    public ModelAirship() {
        super();

        body = new ModelRenderer(this, 0, 0);
        body.addBox(-10f, -8f, -20f, 20, 14, 40);
        body.setRotationPoint(0f, 0f, 0f);

        // Lower gondola - rendered via wingBottom slot
        wingBottom = new ModelRenderer(this, 0, 32);
        wingBottom.addBox(-6f, 0f, -10f, 12, 4, 20);
        wingBottom.setRotationPoint(0f, 6f, 0f);

        tailFin = new ModelRenderer(this, 60, 0);
        tailFin.addBox(-0.5f, -6f, -1f, 1, 6, 2);
        tailFin.setRotationPoint(0f, -7f, 19f);

        tailWing = new ModelRenderer(this, 60, 10);
        tailWing.addBox(-4f, -0.5f, -1f, 8, 1, 2);
        tailWing.setRotationPoint(0f, -3f, 20f);

        propeller = new ModelRenderer(this, 80, 0);
        propeller.addBox(-0.5f, -5f, -0.5f, 1, 10, 1);
        propeller.setRotationPoint(-10f, 0f, 18f);
    }
}
