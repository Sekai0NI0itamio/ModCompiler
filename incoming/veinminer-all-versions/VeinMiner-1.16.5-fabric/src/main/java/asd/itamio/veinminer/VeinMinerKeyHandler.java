package asd.itamio.veinminer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;
@Environment(EnvType.CLIENT)
public class VeinMinerKeyHandler implements ClientModInitializer {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;
    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Toggle Vein Miner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "Vein Miner"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                veinMinerEnabled = !veinMinerEnabled;
                String msg = veinMinerEnabled ? "\u00a7aVein Miner: ENABLED" : "\u00a7cVein Miner: DISABLED";
                if (client.player != null) client.player.sendMessage(new LiteralText(msg), false);
            }
        });
    }
}
