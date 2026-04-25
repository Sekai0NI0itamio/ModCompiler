/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.datafixer.FixUtil;
import net.minecraft.datafixer.TypeReferences;

public class MapBannerBlockPosFormatFix
extends DataFix {
    public MapBannerBlockPosFormatFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Dynamic<T> update(Dynamic<T> dynamic) {
        return dynamic.update("banners", dynamic2 -> dynamic2.createList(dynamic2.asStream().map(dynamic -> dynamic.update("Pos", FixUtil::fixBlockPos))));
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("MapBannerBlockPosFormatFix", this.getInputSchema().getType(TypeReferences.SAVED_DATA_MAP_DATA), typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.update("data", MapBannerBlockPosFormatFix::update)));
    }
}

