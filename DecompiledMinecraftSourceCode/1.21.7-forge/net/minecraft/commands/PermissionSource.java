package net.minecraft.commands;

import net.minecraft.server.commands.PermissionCheck;

public interface PermissionSource {
    boolean hasPermission(int p_405893_);

    default boolean allowsSelectors() {
        return this.hasPermission(2);
    }

    public record Check<T extends PermissionSource>(int requiredLevel) implements PermissionCheck<T> {
        public boolean test(T p_406166_) {
            return p_406166_.hasPermission(this.requiredLevel);
        }

        @Override
        public int requiredLevel() {
            return this.requiredLevel;
        }
    }
}
