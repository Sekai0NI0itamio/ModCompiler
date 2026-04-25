/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.fix.EntityTransformFix;
import net.minecraft.util.Util;

public class EntityHorseSplitFix
extends EntityTransformFix {
    public EntityHorseSplitFix(Schema schema, boolean bl) {
        super("EntityHorseSplitFix", schema, bl);
    }

    @Override
    protected Pair<String, Typed<?>> transform(String choice, Typed<?> typed) {
        if (Objects.equals("EntityHorse", choice)) {
            Dynamic<?> dynamic2 = typed.get(DSL.remainderFinder());
            int i = dynamic2.get("Type").asInt(0);
            String string = switch (i) {
                default -> "Horse";
                case 1 -> "Donkey";
                case 2 -> "Mule";
                case 3 -> "ZombieHorse";
                case 4 -> "SkeletonHorse";
            };
            Type<?> type = this.getOutputSchema().findChoiceType(TypeReferences.ENTITY).types().get(string);
            return Pair.of(string, Util.apply(typed, type, dynamic -> dynamic.remove("Type")));
        }
        return Pair.of(choice, typed);
    }
}

