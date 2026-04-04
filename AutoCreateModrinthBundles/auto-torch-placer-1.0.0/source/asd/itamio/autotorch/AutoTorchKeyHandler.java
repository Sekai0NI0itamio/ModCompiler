package asd.itamio.autotorch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class AutoTorchKeyHandler {
    
    private static final KeyBinding TOGGLE_KEY = new KeyBinding(
        "key.autotorch.toggle",
        Keyboard.KEY_K,
        "key.categories.gameplay"
    );
    
    public AutoTorchKeyHandler() {
        ClientRegistry.registerKeyBinding(TOGGLE_KEY);
    }
    
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (TOGGLE_KEY.isPressed()) {
            toggleAutoTorch();
        }
    }
    
    private void toggleAutoTorch() {
        AutoTorchMod.config.enableAutoTorch = !AutoTorchMod.config.enableAutoTorch;
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            String status = AutoTorchMod.config.enableAutoTorch ? 
                TextFormatting.GREEN + "ON" : 
                TextFormatting.RED + "OFF";
            
            mc.player.sendMessage(
                new TextComponentString(
                    TextFormatting.YELLOW + "Auto Torch Placer: " + status
                )
            );
        }
    }
}
