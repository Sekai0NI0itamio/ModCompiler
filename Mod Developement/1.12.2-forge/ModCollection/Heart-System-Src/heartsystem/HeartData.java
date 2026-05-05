package asd.itamio.heartsystem;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * Manages heart counts for a single player.
 * Hearts are stored as an integer (1 heart = 2 HP max health).
 * Persistence is handled via NBT files through PlayerEvent.SaveToFile / LoadFromFile.
 */
public class HeartData {

    // Attribute modifier UUID — stable, unique to this mod
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    private int hearts;

    public HeartData(int hearts) {
        this.hearts = hearts;
    }

    public int getHearts() {
        return hearts;
    }

    public void setHearts(int hearts) {
        this.hearts = hearts;
    }

    // -----------------------------------------------------------------------
    // NBT serialization
    // -----------------------------------------------------------------------

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("hearts", hearts);
        return tag;
    }

    public static HeartData fromNBT(NBTTagCompound tag) {
        int h = tag.hasKey("hearts") ? tag.getInteger("hearts") : -1;
        return new HeartData(h);
    }

    // -----------------------------------------------------------------------
    // Apply max-health to the player entity
    // -----------------------------------------------------------------------

    /**
     * Applies the stored heart count as the player's max health.
     * Removes any previous modifier from this mod first to avoid stacking.
     */
    public void applyMaxHealth(EntityPlayerMP player) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);

        // Remove old modifier if present
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(attr.getModifier(MODIFIER_UUID));
        }

        // Base max health is 20 (10 hearts). We set it to hearts*2 via a modifier.
        // Operation 0 = add to base. Base is 20, so delta = (hearts*2) - 20.
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, 0);
        attr.applyModifier(mod);

        // Clamp current health to new max
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
