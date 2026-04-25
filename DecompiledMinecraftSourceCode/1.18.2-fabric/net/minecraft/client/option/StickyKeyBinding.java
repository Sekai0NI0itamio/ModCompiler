/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.option;

import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

@Environment(value=EnvType.CLIENT)
public class StickyKeyBinding
extends KeyBinding {
    private final BooleanSupplier toggleGetter;

    public StickyKeyBinding(String id, int code, String category, BooleanSupplier toggleGetter) {
        super(id, InputUtil.Type.KEYSYM, code, category);
        this.toggleGetter = toggleGetter;
    }

    @Override
    public void setPressed(boolean pressed) {
        if (this.toggleGetter.getAsBoolean()) {
            if (pressed) {
                super.setPressed(!this.isPressed());
            }
        } else {
            super.setPressed(pressed);
        }
    }
}

