package asd.itamio.quickstack;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class QuickStackKeyHandler {
    
    private static final KeyBinding QUICK_STACK_KEY = new KeyBinding(
        "key.quickstack.stack",
        Keyboard.KEY_V,
        "key.categories.inventory"
    );
    
    public QuickStackKeyHandler() {
        ClientRegistry.registerKeyBinding(QUICK_STACK_KEY);
    }
    
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (QUICK_STACK_KEY.isPressed()) {
            QuickStackHandler.performQuickStack();
        }
    }
}
