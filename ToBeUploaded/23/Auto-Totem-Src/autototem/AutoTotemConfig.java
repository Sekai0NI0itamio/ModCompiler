package asd.itamio.autototem;

import net.minecraftforge.common.config.Configuration;

public class AutoTotemConfig {
    public boolean enableAutoTotem;
    public boolean showMessages;

    public AutoTotemConfig(Configuration config) {
        config.load();

        enableAutoTotem = config.getBoolean("Enable Auto Totem", "general", true,
                "Enable or disable the auto totem feature");
        
        showMessages = config.getBoolean("Show Messages", "messages", true,
                "Show chat messages when totem is equipped");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
