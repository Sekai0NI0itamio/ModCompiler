package asd.itamio.lifestealparrotmod;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "lifestealparrotmod.maxhealth";

    public static void applyMaxHealth(ServerPlayer player, int hearts) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            if (attr.getModifier(MODIFIER_UUID) != null) {
                attr.removeModifier(MODIFIER_UUID);
            }

            double delta = (double)hearts * 2.0 - 20.0;
            AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, "lifestealparrotmod.maxhealth", delta, Operation.ADD_VALUE);
            attr.addPermanentModifier(mod);
            float newMax = (float)(hearts * 2);
            if (player.getHealth() > newMax) {
                player.setHealth(newMax);
            }
        }
    }
}