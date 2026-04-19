package asd.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class AutoFastXPHandler {
    private int tickCounter = 0;
    private boolean wasRightClickPressed = false;
    
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!AutoFastXPConfig.enabled) return;
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        
        // Check if player is holding right-click
        boolean isRightClickPressed = mc.gameSettings.keyBindUseItem.isKeyDown();
        
        // Check if player is holding an XP bottle
        EnumHand handWithBottle = null;
        ItemStack heldItem = mc.player.getHeldItemMainhand();
        
        if (!heldItem.isEmpty() && heldItem.getItem() == Items.EXPERIENCE_BOTTLE) {
            handWithBottle = EnumHand.MAIN_HAND;
        } else {
            // Check offhand too
            heldItem = mc.player.getHeldItemOffhand();
            if (!heldItem.isEmpty() && heldItem.getItem() == Items.EXPERIENCE_BOTTLE) {
                handWithBottle = EnumHand.OFF_HAND;
            }
        }
        
        // If right-click is pressed and holding XP bottle
        if (isRightClickPressed && handWithBottle != null) {
            tickCounter++;
            
            // Throw bottle at configured rate
            if (tickCounter >= AutoFastXPConfig.throwDelay) {
                throwXPBottle(mc, handWithBottle);
                tickCounter = 0;
            }
            
            wasRightClickPressed = true;
        } else {
            // Reset when not holding right-click
            if (wasRightClickPressed) {
                tickCounter = 0;
                wasRightClickPressed = false;
            }
        }
    }
    
    private void throwXPBottle(Minecraft mc, EnumHand hand) {
        if (mc.player == null || mc.world == null) return;
        if (mc.playerController == null) return;
        
        // Use the item properly through the player controller
        // This sends the packet to the server and handles everything correctly
        EnumActionResult result = mc.playerController.processRightClick(
            mc.player, 
            mc.world, 
            hand
        );
        
        // If successful, swing the arm
        if (result == EnumActionResult.SUCCESS) {
            mc.player.swingArm(hand);
        }
    }
}
