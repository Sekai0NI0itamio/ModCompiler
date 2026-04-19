package com.bothelpers.client.render;

import com.bothelpers.entity.EntityBotHelper;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public class RenderBotHelper extends RenderBiped<EntityBotHelper> {

    private static final ResourceLocation[] SKINS = new ResourceLocation[] {
        new ResourceLocation("bothelpers:textures/entity/bot_skin_steve.png"),
        new ResourceLocation("bothelpers:textures/entity/bot_skin_alex.png")
    };

    public RenderBotHelper(RenderManager renderManager) {
        super(renderManager, new ModelPlayer(0.0f, false), 0.5f);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityBotHelper entity) {
        // Simple hash to assign one of the skins based on entity UUID or name
        int idx = Math.abs(entity.getUniqueID().hashCode()) % SKINS.length;
        return SKINS[idx];
    }
}
