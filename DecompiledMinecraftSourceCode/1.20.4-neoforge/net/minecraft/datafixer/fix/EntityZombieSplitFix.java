/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.datafixer.fix;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.function.Supplier;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.fix.EntityTransformFix;
import net.minecraft.util.Util;

public class EntityZombieSplitFix
extends EntityTransformFix {
    private final Supplier<Type<?>> field_51480 = Suppliers.memoize(() -> this.getOutputSchema().getChoiceType(TypeReferences.ENTITY, "ZombieVillager"));

    public EntityZombieSplitFix(Schema schema) {
        super("EntityZombieSplitFix", schema, true);
    }

    @Override
    protected Pair<String, Typed<?>> transform(String choice, Typed<?> typed) {
        String string;
        if (!choice.equals("Zombie")) {
            return Pair.of(choice, typed);
        }
        Dynamic<?> dynamic2 = typed.getOptional(DSL.remainderFinder()).orElseThrow();
        int i = dynamic2.get("ZombieType").asInt(0);
        return Pair.of(string, (switch (i) {
            default -> {
                string = "Zombie";
                yield typed;
            }
            case 1, 2, 3, 4, 5 -> {
                string = "ZombieVillager";
                yield this.method_59812(typed, i - 1);
            }
            case 6 -> {
                string = "Husk";
                yield typed;
            }
        }).update(DSL.remainderFinder(), dynamic -> dynamic.remove("ZombieType")));
    }

    private Typed<?> method_59812(Typed<?> typed, int i) {
        return Util.apply(typed, this.field_51480.get(), dynamic -> dynamic.set("Profession", dynamic.createInt(i)));
    }
}

