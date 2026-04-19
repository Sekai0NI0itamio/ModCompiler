package asd.itamio.bettersprinting;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class BetterSprintingKeyHandler {
    
    private static KeyBinding toggleSprintKey;
    private static KeyBinding toggleSneakKey;
    
    public BetterSprintingKeyHandler() {
        toggleSprintKey = new KeyBinding("Toggle Sprint", Keyboard.KEY_R, "Better Sprinting");
        toggleSneakKey = new KeyBinding("Toggle Sneak", Keyboard.KEY_G, "Better Sprinting");
        
        ClientRegistry.registerKeyBinding(toggleSprintKey);
        ClientRegistry.registerKeyBinding(toggleSneakKey);
    }
    
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        // These keys provide manual toggle in addition to using the vanilla keys
        if (toggleSprintKey.isPressed()) {
            // Toggle sprint manually
            BetterSprintingMod.logger.info("Toggle Sprint key pressed");
        }
        
        if (toggleSneakKey.isPressed()) {
            // Toggle sneak manually
            BetterSprintingMod.logger.info("Toggle Sneak key pressed");
        }
    }
}
