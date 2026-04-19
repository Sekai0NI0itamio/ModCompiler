package asd.itamio.autototem;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class AutoTotemHandler {
    
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!AutoTotemMod.config.enableAutoTotem) return;
        
        EntityPlayer player = event.player;
        if (player.world.isRemote) return; // Server-side only
        
        // Check if offhand has a totem
        ItemStack offhand = player.getHeldItemOffhand();
        
        // If no totem in offhand, try to equip one
        if (offhand.isEmpty() || offhand.getItem() != Items.TOTEM_OF_UNDYING) {
            equipTotemIfAvailable(player);
        }
    }
    
    private void equipTotemIfAvailable(EntityPlayer player) {
        // Search inventory for totem
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                // Found a totem! Swap it to offhand
                ItemStack oldOffhand = player.getHeldItemOffhand().copy();
                
                // Set totem to offhand
                player.inventory.offHandInventory.set(0, stack.copy());
                
                // Put old offhand item back in the slot where totem was
                if (!oldOffhand.isEmpty()) {
                    player.inventory.setInventorySlotContents(i, oldOffhand);
                } else {
                    player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                }
                
                // Show message
                if (AutoTotemMod.config.showMessages) {
                    player.sendMessage(new TextComponentString("§6[Auto Totem] §aTotem equipped"));
                }
                
                return;
            }
        }
    }
}
