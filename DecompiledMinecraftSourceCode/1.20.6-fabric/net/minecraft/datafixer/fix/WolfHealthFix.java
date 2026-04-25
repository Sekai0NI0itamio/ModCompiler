/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.fix.ChoiceFix;
import net.minecraft.datafixer.schema.IdentifierNormalizingSchema;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class WolfHealthFix
extends ChoiceFix {
    private static final String WOLF_ENTITY_ID = "minecraft:wolf";
    private static final String MAX_HEALTH_ATTRIBUTE_ID = "minecraft:generic.max_health";

    public WolfHealthFix(Schema outputSchema) {
        super(outputSchema, false, "FixWolfHealth", TypeReferences.ENTITY, WOLF_ENTITY_ID);
    }

    @Override
    protected Typed<?> transform(Typed<?> inputType) {
        return inputType.update(DSL.remainderFinder(), dynamic2 -> {
            MutableBoolean mutableBoolean = new MutableBoolean(false);
            dynamic2 = dynamic2.update("Attributes", dynamic -> dynamic.createList(dynamic.asStream().map(dynamic2 -> {
                if (MAX_HEALTH_ATTRIBUTE_ID.equals(IdentifierNormalizingSchema.normalize(dynamic2.get("Name").asString("")))) {
                    return dynamic2.update("Base", dynamic -> {
                        if (dynamic.asDouble(0.0) == 20.0) {
                            mutableBoolean.setTrue();
                            return dynamic.createDouble(40.0);
                        }
                        return dynamic;
                    });
                }
                return dynamic2;
            })));
            if (mutableBoolean.isTrue()) {
                dynamic2 = dynamic2.update("Health", dynamic -> dynamic.createFloat(dynamic.asFloat(0.0f) * 2.0f));
            }
            return dynamic2;
        });
    }
}

