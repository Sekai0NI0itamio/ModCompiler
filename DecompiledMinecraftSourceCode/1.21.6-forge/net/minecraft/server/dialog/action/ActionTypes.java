package net.minecraft.server.dialog.action;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.ResourceLocation;

public class ActionTypes {
    public static MapCodec<? extends Action> bootstrap(Registry<MapCodec<? extends Action>> p_406033_) {
        StaticAction.WRAPPED_CODECS.forEach((p_409648_, p_409316_) -> Registry.register(p_406033_, ResourceLocation.withDefaultNamespace(p_409648_.getSerializedName()), p_409316_));
        Registry.register(p_406033_, ResourceLocation.withDefaultNamespace("dynamic/run_command"), CommandTemplate.MAP_CODEC);
        return Registry.register(p_406033_, ResourceLocation.withDefaultNamespace("dynamic/custom"), CustomAll.MAP_CODEC);
    }
}
