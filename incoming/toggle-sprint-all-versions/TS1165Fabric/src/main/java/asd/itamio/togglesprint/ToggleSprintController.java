package asd.itamio.togglesprint;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class ToggleSprintController {
    private boolean sprintLocked;
    private boolean sprintKeyWasDown;

    public void onClientTick(MinecraftClient client) {
        if (client == null || client.options == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.player == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.isPaused()) {
            sprintKeyWasDown = client.options.keySprint.isPressed();
            return;
        }
        boolean sprintKeyDown = client.options.keySprint.isPressed();
        if (sprintKeyDown && !sprintKeyWasDown) {
            sprintLocked = !sprintLocked;
            client.player.setSprinting(false);
            client.player.sendMessage(
                Text.of("Toggle Sprint: " + (sprintLocked ? "ON" : "OFF")), true);
        }
        sprintKeyWasDown = sprintKeyDown;
        if (sprintLocked) {
            client.player.setSprinting(shouldKeepSprinting(client));
        }
    }

    private boolean shouldKeepSprinting(MinecraftClient client) {
        if (client.currentScreen != null) return false;
        if (client.player == null) return false;
        if (client.player.isSpectator() || client.player.hasVehicle()) return false;
        if (client.player.isSneaking() || client.player.isUsingItem()) return false;
        return client.options.keyForward.isPressed()
            && !client.options.keyBack.isPressed();
    }
}
