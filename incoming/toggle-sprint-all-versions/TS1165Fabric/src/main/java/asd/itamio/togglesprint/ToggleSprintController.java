package asd.itamio.togglesprint;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2561;
import net.minecraft.class_310;

@Environment(EnvType.CLIENT)
public final class ToggleSprintController {
    private boolean sprintLocked;
    private boolean sprintKeyWasDown;

    public void onClientTick(class_310 client) {
        if (client == null || client.field_1690 == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.field_1724 == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.method_1493()) {
            sprintKeyWasDown = client.field_1690.field_1867.method_1434();
            return;
        }
        boolean sprintKeyDown = client.field_1690.field_1867.method_1434();
        if (sprintKeyDown && !sprintKeyWasDown) {
            sprintLocked = !sprintLocked;
            client.field_1690.field_1867.method_23481(false);
            client.field_1724.method_5728(false);
            client.field_1724.method_7353(
                class_2561.method_43470("Toggle Sprint: " + (sprintLocked ? "ON" : "OFF")), true);
        }
        sprintKeyWasDown = sprintKeyDown;
        if (sprintLocked) {
            client.field_1724.method_5728(shouldKeepSprinting(client));
        }
    }

    private boolean shouldKeepSprinting(class_310 client) {
        if (client.field_1755 != null) return false;
        if (client.field_1724 == null) return false;
        if (client.field_1724.method_7325() || client.field_1724.method_5765()) return false;
        if (client.field_1724.method_5715() || client.field_1724.method_6115()) return false;
        return client.field_1690.field_1894.method_1434()
            && !client.field_1690.field_1881.method_1434();
    }
}
