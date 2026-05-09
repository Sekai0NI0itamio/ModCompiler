/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.SharedMonsterAttributes
 *  net.minecraft.entity.ai.attributes.AttributeModifier
 *  net.minecraft.entity.ai.attributes.IAttributeInstance
 *  net.minecraft.entity.player.EntityPlayerMP
 *  net.minecraft.nbt.NBTTagCompound
 */
package asd.itamio.heartsystem;

import java.util.UUID;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";
    private int hearts;

    public HeartData(int hearts) {
        this.hearts = hearts;
    }

    public int getHearts() {
        return this.hearts;
    }

    public void setHearts(int hearts) {
        this.hearts = hearts;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.func_74768_a("hearts", this.hearts);
        return tag;
    }

    public static HeartData fromNBT(NBTTagCompound tag) {
        int h = tag.func_74764_b("hearts") ? tag.func_74762_e("hearts") : -1;
        return new HeartData(h);
    }

    public void applyMaxHealth(EntityPlayerMP player) {
        IAttributeInstance attr = player.func_110148_a(SharedMonsterAttributes.field_111267_a);
        if (attr.func_111127_a(MODIFIER_UUID) != null) {
            attr.func_111124_b(attr.func_111127_a(MODIFIER_UUID));
        }
        double delta = (double)this.hearts * 2.0 - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, 0);
        attr.func_111121_a(mod);
        float newMax = this.hearts * 2;
        if (player.func_110143_aJ() > newMax) {
            player.func_70606_j(newMax);
        }
    }
}

