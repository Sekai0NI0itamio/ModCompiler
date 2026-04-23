package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public class VeinMinerKeyHandler {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyBinding("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\u00a7aVein Miner: ENABLED" : "\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(new TextComponent(msg), false);
        }
    }
}
