package asd.itamio.heartsystem;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class HeartData {
    private static final Identifier MODIFIER_ID =
        Identifier.fromNamespaceAndPath("heartsystem", "maxhealth");

    public static void applyMaxHealth(ServerPlayer player, int hearts) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MODIFIER_ID);
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
