/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.datafixer.fix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.schema.IdentifierNormalizingSchema;

public abstract class ItemNbtFix
extends DataFix {
    private final String name;
    private final Predicate<String> itemIdPredicate;

    public ItemNbtFix(Schema outputSchema, String name, Predicate<String> itemIdPredicate) {
        super(outputSchema, false);
        this.name = name;
        this.itemIdPredicate = itemIdPredicate;
    }

    @Override
    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(TypeReferences.ITEM_STACK);
        return this.fixTypeEverywhereTyped(this.name, type, ItemNbtFix.method_56971(type, this.itemIdPredicate, this::fixNbt));
    }

    public static UnaryOperator<Typed<?>> method_56971(Type<?> type, Predicate<String> predicate, UnaryOperator<Dynamic<?>> unaryOperator) {
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(TypeReferences.ITEM_NAME.typeName(), IdentifierNormalizingSchema.getIdentifierType()));
        OpticFinder<?> opticFinder2 = type.findField("tag");
        return typed2 -> {
            Optional optional = typed2.getOptional(opticFinder);
            if (optional.isPresent() && predicate.test((String)((Pair)optional.get()).getSecond())) {
                return typed2.updateTyped(opticFinder2, typed -> typed.update(DSL.remainderFinder(), unaryOperator));
            }
            return typed2;
        };
    }

    protected abstract <T> Dynamic<T> fixNbt(Dynamic<T> var1);
}

