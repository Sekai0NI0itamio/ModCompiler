package asd.itamio.bettersprinting;

import net.minecraftforge.common.config.Configuration;

public class BetterSprintingConfig {
    // Toggle sprint
    public boolean enableToggleSprint;
    public boolean sprintInAllDirections;
    public boolean doubleTapDisabled;
    
    // Toggle sneak
    public boolean enableToggleSneak;
    
    // Flying boost
    public boolean enableFlyBoost;
    public float flyBoostAmount;
    
    // HUD
    public boolean showSprintHUD;
    public boolean showSneakHUD;
    public int hudX;
    public int hudY;

    public BetterSprintingConfig(Configuration config) {
        config.load();

        // Toggle sprint
        enableToggleSprint = config.getBoolean("Enable Toggle Sprint", "sprint", true,
                "Enable toggle sprint (press once to start sprinting)");
        
        sprintInAllDirections = config.getBoolean("Sprint In All Directions", "sprint", true,
                "Allow sprinting in all directions (not just forward)");
        
        doubleTapDisabled = config.getBoolean("Disable Double Tap", "sprint", true,
                "Disable double-tap to sprint (only use toggle key)");
        
        // Toggle sneak
        enableToggleSneak = config.getBoolean("Enable Toggle Sneak", "sneak", true,
                "Enable toggle sneak (press once to start sneaking)");
        
        // Flying boost
        enableFlyBoost = config.getBoolean("Enable Fly Boost", "flying", true,
                "Enable sprint key to boost flying speed");
        
        flyBoostAmount = config.getFloat("Fly Boost Amount", "flying", 2.0f, 1.0f, 5.0f,
                "Flying speed multiplier when sprinting (1.0 = normal, 2.0 = double speed)");
        
        // HUD
        showSprintHUD = config.getBoolean("Show Sprint HUD", "hud", true,
                "Show sprint status on HUD");
        
        showSneakHUD = config.getBoolean("Show Sneak HUD", "hud", true,
                "Show sneak status on HUD");
        
        hudX = config.getInt("HUD X Position", "hud", 5, 0, 1000,
                "HUD X position (pixels from left)");
        
        hudY = config.getInt("HUD Y Position", "hud", 5, 0, 1000,
                "HUD Y position (pixels from top)");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
