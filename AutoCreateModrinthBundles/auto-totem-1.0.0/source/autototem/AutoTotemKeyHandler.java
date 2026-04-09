package asd.itamio.autototem;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class AutoTotemKeyHandler {
    
    private static KeyBinding toggleKey;
    
    public AutoTotemKeyHandler() {
        toggleKey = new KeyBinding("Toggle Auto Totem", Keyboard.KEY_O, "Auto Totem");
        ClientRegistry.registerKeyBinding(toggleKey);
    }
    
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            // Toggle the mod
            AutoTotemMod.config.enableAutoTotem = !AutoTotemMod.config.enableAutoTotem;
            
            // Get player
            EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().player;
            if (player != null) {
                String status = AutoTotemMod.config.enableAutoTotem ? "§aEnabled" : "§cDisabled";
                player.sendMessage(new TextComponentString("§6[Auto Totem] " + status));
            }
        }
    }
}
