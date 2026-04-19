package asd.itamio.autocrystal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class AutoCrystalKeyHandler {
    private static final String KEY_CATEGORY = "Auto Crystal";
    private KeyBinding toggleKey;
    
    public AutoCrystalKeyHandler() {
        toggleKey = new KeyBinding("Toggle Auto Crystal", Keyboard.KEY_C, KEY_CATEGORY);
        ClientRegistry.registerKeyBinding(toggleKey);
    }
    
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            AutoCrystalConfig.enabled = !AutoCrystalConfig.enabled;
            AutoCrystalMod.config.save();
            
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                String status = AutoCrystalConfig.enabled ? 
                    TextFormatting.GREEN + "ON" : 
                    TextFormatting.RED + "OFF";
                mc.player.sendMessage(new TextComponentString(
                    TextFormatting.GOLD + "[Auto Crystal] " + 
                    TextFormatting.RESET + status
                ));
            }
        }
    }
}
