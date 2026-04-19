package asd.itamio.antikb;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class AntiKBHandler {
    
    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if (!AntiKBConfig.enabled) return;
        
        // Only apply to the client player
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;
        if (event.getEntityLiving() != player) return;
        
        // Completely cancel all knockback
        event.setCanceled(true);
    }
}
