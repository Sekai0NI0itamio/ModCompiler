package asd.itamio.veinminer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
@OnlyIn(Dist.CLIENT)
public class VeinMinerKeyHandler {
    public static final KeyMapping toggleKey = new KeyMapping("Toggle Vein Miner", GLFW.GLFW_KEY_V, "Vein Miner");
    public static boolean veinMinerEnabled = true;
    public static void register(RegisterKeyMappingsEvent event) { event.register(toggleKey); }
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (toggleKey.consumeClick()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\u00a7aVein Miner: ENABLED" : "\u00a7cVein Miner: DISABLED";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
