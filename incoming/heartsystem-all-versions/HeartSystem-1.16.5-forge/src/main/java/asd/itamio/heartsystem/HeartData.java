package asd.itamio.heartsystem;

import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    public static void applyMaxHealth(ServerPlayerEntity player, int hearts) {
        ModifiableAttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) attr.removeModifier(MODIFIER_UUID);
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
