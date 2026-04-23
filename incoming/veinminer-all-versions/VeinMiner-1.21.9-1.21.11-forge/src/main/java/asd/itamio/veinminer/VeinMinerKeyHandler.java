package asd.itamio.veinminer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
public class VeinMinerKeyHandler {
    public static final KeyMapping toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
    public static boolean veinMinerEnabled = true;
    public static void register(RegisterKeyMappingsEvent event) { event.register(toggleKey); }
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "§aVein Miner: ENABLED" : "§cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
