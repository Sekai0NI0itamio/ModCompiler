package asd.itamio.heartsystem;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";
    private int hearts;

    public HeartData(int hearts) { this.hearts = hearts; }
    public int getHearts() { return hearts; }
    public void setHearts(int hearts) { this.hearts = hearts; }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("hearts", hearts);
        return tag;
    }

    public static HeartData fromNBT(NBTTagCompound tag) {
        int h = tag.hasKey("hearts") ? tag.getInteger("hearts") : -1;
        return new HeartData(h);
    }

    public void applyMaxHealth(EntityPlayerMP player) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(attr.getModifier(MODIFIER_UUID));
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, 0);
        attr.applyModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
