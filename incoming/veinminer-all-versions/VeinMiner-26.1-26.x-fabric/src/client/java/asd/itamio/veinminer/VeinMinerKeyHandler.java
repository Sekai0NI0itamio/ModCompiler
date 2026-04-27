package asd.itamio.veinminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VeinMinerKeyHandler implements ClientModInitializer {
    public static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "Toggle Vein Miner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "Vein Miner"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                VeinMinerMod.veinMinerEnabled = !VeinMinerMod.veinMinerEnabled;
                String msg = VeinMinerMod.veinMinerEnabled ? "\u00a7aVein Miner: ENABLED" : "\u00a7cVein Miner: DISABLED";
                if (client.player != null) client.player.displayClientMessage(Component.literal(msg), false);
            }
        });
    }
}
