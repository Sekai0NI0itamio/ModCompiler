package asd.itamio.veinminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class VeinMinerKeyHandler {
    public static KeyBinding toggleKey;
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyBinding("Toggle Vein Miner", Keyboard.KEY_V, "Vein Miner");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            veinMinerEnabled = !veinMinerEnabled;
            String msg = veinMinerEnabled ? "\u00a7aVein Miner: ENABLED" : "\u00a7cVein Miner: DISABLED";
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(msg));
        }
    }
}
