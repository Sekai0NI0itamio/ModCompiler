package asd.itamio.bettersprinting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BetterSprintingHandler {
    
    private boolean sprintToggled = false;
    private boolean sneakToggled = false;
    private boolean wasSprintKeyDown = false;
    private boolean wasSneakKeyDown = false;
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        
        EntityPlayerSP player = mc.player;
        KeyBinding sprintKey = mc.gameSettings.keyBindSprint;
        KeyBinding sneakKey = mc.gameSettings.keyBindSneak;
        
        // Handle toggle sprint
        if (BetterSprintingMod.config.enableToggleSprint) {
            handleToggleSprint(player, sprintKey);
        }
        
        // Handle toggle sneak
        if (BetterSprintingMod.config.enableToggleSneak) {
            handleToggleSneak(player, sneakKey);
        }
        
        // Handle fly boost
        if (BetterSprintingMod.config.enableFlyBoost && player.capabilities.isFlying) {
            handleFlyBoost(player, sprintKey);
        }
    }
    
    private void handleToggleSprint(EntityPlayerSP player, KeyBinding sprintKey) {
        boolean sprintKeyDown = sprintKey.isKeyDown();
        
        // Detect key press (not held)
        if (sprintKeyDown && !wasSprintKeyDown) {
            sprintToggled = !sprintToggled;
        }
        
        wasSprintKeyDown = sprintKeyDown;
        
        // Apply sprint
        if (sprintToggled) {
            if (BetterSprintingMod.config.sprintInAllDirections) {
                // Sprint in any direction
                if (player.moveForward != 0 || player.moveStrafing != 0) {
                    player.setSprinting(true);
                }
            } else {
                // Vanilla: only sprint forward
                if (player.moveForward > 0) {
                    player.setSprinting(true);
                }
            }
        }
        
        // Disable double-tap sprint if configured
        if (BetterSprintingMod.config.doubleTapDisabled && !sprintToggled) {
            player.setSprinting(false);
        }
    }
    
    private void handleToggleSneak(EntityPlayerSP player, KeyBinding sneakKey) {
        boolean sneakKeyDown = sneakKey.isKeyDown();
        
        // Detect key press (not held)
        if (sneakKeyDown && !wasSneakKeyDown) {
            sneakToggled = !sneakToggled;
        }
        
        wasSneakKeyDown = sneakKeyDown;
        
        // Apply sneak
        if (sneakToggled) {
            player.movementInput.sneak = true;
        }
    }
    
    private void handleFlyBoost(EntityPlayerSP player, KeyBinding sprintKey) {
        if (sprintKey.isKeyDown() || sprintToggled) {
            // Boost flying speed
            float boost = BetterSprintingMod.config.flyBoostAmount;
            player.motionX *= boost;
            player.motionY *= boost;
            player.motionZ *= boost;
        }
    }
    
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Text event) {
        if (!BetterSprintingMod.config.showSprintHUD && !BetterSprintingMod.config.showSneakHUD) {
            return;
        }
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        
        FontRenderer fontRenderer = mc.fontRenderer;
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        
        int x = BetterSprintingMod.config.hudX;
        int y = BetterSprintingMod.config.hudY;
        
        // Show sprint status
        if (BetterSprintingMod.config.showSprintHUD && sprintToggled) {
            String text = "§a[Sprint: ON]";
            fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
            y += 10;
        }
        
        // Show sneak status
        if (BetterSprintingMod.config.showSneakHUD && sneakToggled) {
            String text = "§e[Sneak: ON]";
            fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
        }
    }
    
    public boolean isSprintToggled() {
        return sprintToggled;
    }
    
    public boolean isSneakToggled() {
        return sneakToggled;
    }
    
    public void setSprintToggled(boolean toggled) {
        this.sprintToggled = toggled;
    }
    
    public void setSneakToggled(boolean toggled) {
        this.sneakToggled = toggled;
    }
}
