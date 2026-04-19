package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ToggleSprintController {
    private boolean sprintLocked;
    private boolean sprintKeyWasDown;

    public void onClientTick(Minecraft client) {
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
            sprintKeyWasDown = client.options.keySprint.isDown();
            return;
        }
        boolean sprintKeyDown = client.options.keySprint.isDown();
        if (sprintKeyDown && !sprintKeyWasDown) {
            sprintLocked = !sprintLocked;
            client.options.keySprint.setDown(false);
            client.player.setSprinting(false);
            client.player.displayClientMessage(
                Component.literal("Toggle Sprint: " + (sprintLocked ? "ON" : "OFF")), true);
        }
        sprintKeyWasDown = sprintKeyDown;
        if (sprintLocked) {
            client.player.setSprinting(shouldKeepSprinting(client));
        }
    }

    private boolean shouldKeepSprinting(Minecraft client) {
        if (client.screen != null) return false;
        if (client.player == null) return false;
        if (client.player.isSpectator() || client.player.isPassenger()) return false;
        if (client.player.isShiftKeyDown() || client.player.isUsingItem()) return false;
        return client.options.keyUp.isDown() && !client.options.keyDown.isDown();
    }
}
