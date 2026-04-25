/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.util;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.OrderedText;

@Environment(value=EnvType.CLIENT)
public interface OrderableTooltip {
    public List<OrderedText> getOrderedTooltip();
}

