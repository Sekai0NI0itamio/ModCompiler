package immersive_aircraft.render.model;

import net.minecraft.client.model.ModelRenderer;

/**
 * Biplane model: stubby body, two stacked wings, tail, propeller.
 */
public class ModelBiplane extends ModelAircraft {
    public ModelBiplane() {
        super();

        body = new ModelRenderer(this, 0, 0);
        body.addBox(-8f, -4f, -3f, 16, 6, 6);
        body.setRotationPoint(0f, 4f, 0f);

        wingTop = new ModelRenderer(this, 0, 14);
        wingTop.addBox(-14f, -1f, -16f, 28, 2, 32);
        wingTop.setRotationPoint(0f, -1f, 0f);

        wingBottom = new ModelRenderer(this, 0, 14);
        wingBottom.addBox(-14f, -1f, -16f, 28, 2, 32);
        wingBottom.setRotationPoint(0f, 4f, 0f);

        tailFin = new ModelRenderer(this, 60, 0);
        tailFin.addBox(-1f, -6f, -1f, 2, 6, 2);
        tailFin.setRotationPoint(0f, -4f, 12f);

        tailWing = new ModelRenderer(this, 60, 10);
        tailWing.addBox(-6f, -1f, -1f, 12, 2, 2);
        tailWing.setRotationPoint(0f, 1f, 13f);

        propeller = new ModelRenderer(this, 80, 0);
        propeller.addBox(-0.5f, -8f, -1f, 1, 16, 2);
        propeller.setRotationPoint(-9f, 0f, 0f);
    }
}
