package asd.itamio.heartsystem;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    public static void applyMaxHealth(ServerPlayerEntity player, int hearts) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        double newMax = hearts * 2.0;
        attr.setBaseValue(newMax);
        float hp = (float)(hearts * 2);
        if (player.getHealth() > hp) player.setHealth(hp);
    }
}
