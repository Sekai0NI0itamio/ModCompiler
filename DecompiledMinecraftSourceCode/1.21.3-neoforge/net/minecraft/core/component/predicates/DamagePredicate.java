package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;

public record DamagePredicate(MinMaxBounds.Ints durability, MinMaxBounds.Ints damage) implements DataComponentPredicate {
    public static final Codec<DamagePredicate> CODEC = RecordCodecBuilder.create(
        p_397826_ -> p_397826_.group(
                MinMaxBounds.Ints.CODEC.optionalFieldOf("durability", MinMaxBounds.Ints.ANY).forGetter(DamagePredicate::durability),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("damage", MinMaxBounds.Ints.ANY).forGetter(DamagePredicate::damage)
            )
            .apply(p_397826_, DamagePredicate::new)
    );

    @Override
    public boolean matches(DataComponentGetter p_391581_) {
        Integer integer = p_391581_.get(DataComponents.DAMAGE);
        if (integer == null) {
            return false;
        } else {
            int i = p_391581_.getOrDefault(DataComponents.MAX_DAMAGE, 0);
            return !this.durability.matches(i - integer) ? false : this.damage.matches(integer);
        }
    }

    public static DamagePredicate durability(MinMaxBounds.Ints p_392932_) {
        return new DamagePredicate(p_392932_, MinMaxBounds.Ints.ANY);
    }
}
