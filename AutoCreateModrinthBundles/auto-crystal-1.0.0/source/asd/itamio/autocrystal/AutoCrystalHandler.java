package asd.itamio.autocrystal;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class AutoCrystalHandler {
    private List<EntityEnderCrystal> crystalsToAttack = new ArrayList<>();
    private boolean justPlacedCrystal = false;
    
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!AutoCrystalConfig.enabled) return;
        
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !player.world.isRemote) return;
        
        ItemStack heldItem = player.getHeldItem(event.getHand());
        if (heldItem.isEmpty() || heldItem.getItem() != Items.END_CRYSTAL) return;
        
        // Mark that we just placed a crystal
        justPlacedCrystal = true;
    }
    
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!AutoCrystalConfig.enabled) return;
        if (!event.getWorld().isRemote) return;
        
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityEnderCrystal)) return;
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        
        // If we just placed a crystal and this crystal is close to us, mark it for attack
        if (justPlacedCrystal) {
            EntityEnderCrystal crystal = (EntityEnderCrystal) entity;
            double distance = mc.player.getDistance(crystal);
            
            if (distance <= AutoCrystalConfig.attackRange) {
                crystalsToAttack.add(crystal);
                justPlacedCrystal = false;
            }
        }
    }
    
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!AutoCrystalConfig.enabled) return;
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        
        // Attack all crystals in the queue
        if (!crystalsToAttack.isEmpty()) {
            List<EntityEnderCrystal> toRemove = new ArrayList<>();
            
            for (EntityEnderCrystal crystal : crystalsToAttack) {
                if (crystal == null || crystal.isDead) {
                    toRemove.add(crystal);
                    continue;
                }
                
                double distance = mc.player.getDistance(crystal);
                if (distance <= AutoCrystalConfig.attackRange) {
                    // Attack the crystal
                    mc.playerController.attackEntity(mc.player, crystal);
                    mc.player.swingArm(EnumHand.MAIN_HAND);
                }
                
                toRemove.add(crystal);
            }
            
            crystalsToAttack.removeAll(toRemove);
        }
    }
}
