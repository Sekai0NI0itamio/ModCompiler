package asd.itamio.veinminer;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class VeinMinerKeyHandler {
    private static final String CATEGORY = "Vein Miner";
    public static KeyBinding toggleKey;
    
    public static boolean veinMinerEnabled = true;

    public VeinMinerKeyHandler() {
        toggleKey = new KeyBinding("Toggle Vein Miner", Keyboard.KEY_V, CATEGORY);
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            veinMinerEnabled = !veinMinerEnabled;
            
            if (veinMinerEnabled) {
                net.minecraft.client.Minecraft.getMinecraft().player.sendMessage(
                    new net.minecraft.util.text.TextComponentString("§aVein Miner: ENABLED"));
            } else {
                net.minecraft.client.Minecraft.getMinecraft().player.sendMessage(
                    new net.minecraft.util.text.TextComponentString("§cVein Miner: DISABLED"));
            }
        }
    }
}
