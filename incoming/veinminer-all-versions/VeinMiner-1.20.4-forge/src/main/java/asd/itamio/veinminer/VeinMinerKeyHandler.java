package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public class VeinMinerKeyHandler {
    public static KeyMapping toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\u00a7aVein Miner: ENABLED" : "\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
