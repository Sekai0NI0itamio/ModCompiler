package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;

public class V4302 extends NamespacedSchema {
    public V4302(int p_397562_, Schema p_396494_) {
        super(p_397562_, p_396494_);
    }

    @Override
    public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema p_391267_) {
        Map<String, Supplier<TypeTemplate>> map = super.registerBlockEntities(p_391267_);
        p_391267_.registerSimple(map, "minecraft:test_block");
        p_391267_.registerSimple(map, "minecraft:test_instance_block");
        return map;
    }
}
