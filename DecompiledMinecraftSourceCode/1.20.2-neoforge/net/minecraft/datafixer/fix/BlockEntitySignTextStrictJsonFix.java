/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.fix.ChoiceFix;
import net.minecraft.datafixer.fix.TextFixes;

public class BlockEntitySignTextStrictJsonFix
extends ChoiceFix {
    public BlockEntitySignTextStrictJsonFix(Schema schema, boolean bl) {
        super(schema, bl, "BlockEntitySignTextStrictJsonFix", TypeReferences.BLOCK_ENTITY, "Sign");
    }

    private Dynamic<?> fix(Dynamic<?> dynamic, String lineName) {
        return dynamic.update(lineName, TextFixes::text);
    }

    @Override
    protected Typed<?> transform(Typed<?> inputType) {
        return inputType.update(DSL.remainderFinder(), dynamic -> {
            dynamic = this.fix((Dynamic<?>)dynamic, "Text1");
            dynamic = this.fix((Dynamic<?>)dynamic, "Text2");
            dynamic = this.fix((Dynamic<?>)dynamic, "Text3");
            dynamic = this.fix((Dynamic<?>)dynamic, "Text4");
            return dynamic;
        });
    }
}

