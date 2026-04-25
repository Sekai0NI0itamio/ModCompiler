/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.datafixer.TypeReferences;

public class RenameEnchantmentFix
extends DataFix {
    final String name;
    final Map<String, String> oldToNewIds;

    public RenameEnchantmentFix(Schema outputSchema, String name, Map<String, String> oldToNewIds) {
        super(outputSchema, false);
        this.name = name;
        this.oldToNewIds = oldToNewIds;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(TypeReferences.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(this.name, type, typed2 -> typed2.updateTyped(opticFinder, typed -> typed.update(DSL.remainderFinder(), this::fixIds)));
    }

    private Dynamic<?> fixIds(Dynamic<?> data) {
        data = this.fixIds(data, "Enchantments");
        data = this.fixIds(data, "StoredEnchantments");
        return data;
    }

    private Dynamic<?> fixIds(Dynamic<?> data, String key) {
        return data.update(key, dynamic -> dynamic.asStreamOpt().map(stream -> stream.map(dynamic -> dynamic.update("id", dynamic2 -> dynamic2.asString().map(oldId -> dynamic.createString(this.oldToNewIds.getOrDefault(oldId, (String)oldId))).mapOrElse(Function.identity(), error -> dynamic2)))).map(dynamic::createList).mapOrElse(Function.identity(), error -> dynamic));
    }
}

