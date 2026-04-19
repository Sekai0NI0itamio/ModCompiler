package asd.itamio.keepequipment;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class KeepEquipmentKeyHandler {
    private static final String CATEGORY = "Keep Equipment";
    public static KeyBinding toggleKey;
    
    public static boolean keepEquipmentEnabled = true;

    public KeepEquipmentKeyHandler() {
        toggleKey = new KeyBinding("Toggle Keep Equipment", Keyboard.KEY_K, CATEGORY);
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            keepEquipmentEnabled = !keepEquipmentEnabled;
            
            if (keepEquipmentEnabled) {
                net.minecraft.client.Minecraft.getMinecraft().player.sendMessage(
                    new net.minecraft.util.text.TextComponentString("§aKeep Equipment: ENABLED"));
            } else {
                net.minecraft.client.Minecraft.getMinecraft().player.sendMessage(
                    new net.minecraft.util.text.TextComponentString("§cKeep Equipment: DISABLED"));
            }
        }
    }
}
