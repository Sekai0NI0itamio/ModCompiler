package net.minecraft.advancements.critereon;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;

public interface SingleComponentItemPredicate<T> extends DataComponentPredicate {
    @Override
    default boolean matches(DataComponentGetter p_393608_) {
        T t = p_393608_.get(this.componentType());
        return t != null && this.matches(t);
    }

    DataComponentType<T> componentType();

    boolean matches(T p_333057_);
}
