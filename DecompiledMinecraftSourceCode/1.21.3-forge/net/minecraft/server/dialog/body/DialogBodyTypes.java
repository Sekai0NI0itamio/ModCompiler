package net.minecraft.server.dialog.body;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class DialogBodyTypes {
    public static MapCodec<? extends DialogBody> bootstrap(Registry<MapCodec<? extends DialogBody>> p_409551_) {
        Registry.register(p_409551_, ResourceLocation.withDefaultNamespace("item"), ItemBody.MAP_CODEC);
        return Registry.register(p_409551_, ResourceLocation.withDefaultNamespace("plain_message"), PlainMessage.MAP_CODEC);
    }
}
