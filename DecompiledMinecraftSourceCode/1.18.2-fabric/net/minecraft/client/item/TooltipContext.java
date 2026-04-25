/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.item;

public interface TooltipContext {
    public boolean isAdvanced();

    public static enum Default implements TooltipContext
    {
        NORMAL(false),
        ADVANCED(true);

        private final boolean advanced;

        private Default(boolean advanced) {
            this.advanced = advanced;
        }

        @Override
        public boolean isAdvanced() {
            return this.advanced;
        }
    }
}

