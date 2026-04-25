/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.structure;

import net.minecraft.structure.DesertVillageData;
import net.minecraft.structure.PlainsVillageData;
import net.minecraft.structure.SavannaVillageData;
import net.minecraft.structure.SnowyVillageData;
import net.minecraft.structure.TaigaVillageData;

public class VillageGenerator {
    public static void init() {
        PlainsVillageData.init();
        SnowyVillageData.init();
        SavannaVillageData.init();
        DesertVillageData.init();
        TaigaVillageData.init();
    }
}

