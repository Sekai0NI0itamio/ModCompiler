/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.player.EntityPlayer
 *  net.minecraft.entity.player.EntityPlayerMP
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.management.UserListBans
 *  net.minecraft.server.management.UserListBansEntry
 *  net.minecraft.server.management.UserListEntry
 *  net.minecraft.util.DamageSource
 *  net.minecraft.util.text.ITextComponent
 *  net.minecraft.util.text.TextComponentString
 *  net.minecraftforge.event.entity.living.LivingDeathEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$Clone
 *  net.minecraftforge.event.entity.player.PlayerEvent$LoadFromFile
 *  net.minecraftforge.event.entity.player.PlayerEvent$SaveToFile
 *  net.minecraftforge.fml.common.eventhandler.EventPriority
 *  net.minecraftforge.fml.common.eventhandler.SubscribeEvent
 */
package asd.itamio.heartsystem;

import asd.itamio.heartsystem.HeartData;
import asd.itamio.heartsystem.HeartStorage;
import asd.itamio.heartsystem.HeartSystemMod;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListBans;
import net.minecraft.server.management.UserListBansEntry;
import net.minecraft.server.management.UserListEntry;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class HeartEventHandler {
    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
            HeartSystemMod.logger.info("[HeartSystem] New player {} \u2014 assigned {} starting hearts.", (Object)uuidStr, (Object)start);
        } else {
            HeartSystemMod.logger.info("[HeartSystem] Loaded {} hearts for player {}.", (Object)loaded, (Object)uuidStr);
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) {
            hearts = HeartSystemMod.config.getStartHearts();
        }
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        EntityPlayer newPlayer = event.getEntityPlayer();
        if (newPlayer.field_70170_p.field_72995_K) {
            return;
        }
        if (!event.isWasDeath()) {
            return;
        }
        UUID uuid = newPlayer.func_110124_au();
        if (!HeartStorage.get().has(uuid)) {
            return;
        }
        if (newPlayer instanceof EntityPlayerMP) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((EntityPlayerMP)newPlayer);
        }
    }

    @SubscribeEvent(priority=EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().field_70170_p.field_72995_K) {
            return;
        }
        if (!(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP deadPlayer = (EntityPlayerMP)event.getEntity();
        UUID deadUUID = deadPlayer.func_110124_au();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) {
            hearts = HeartSystemMod.config.getStartHearts();
        }
        int min = HeartSystemMod.config.getMinHearts();
        HeartSystemMod.logger.info("[HeartSystem] Player {} died. Hearts: {} -> {}", (Object)deadPlayer.func_70005_c_(), (Object)(--hearts + 1), (Object)hearts);
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            this.broadcastMessage(deadPlayer.func_184102_h(), "\u00a7c[HeartSystem] " + deadPlayer.func_70005_c_() + " has been permanently banned (0 hearts).");
            HeartSystemMod.logger.info("[HeartSystem] Permadeath triggered for {}. Banning.", (Object)deadPlayer.func_70005_c_());
            UserListBans banList = deadPlayer.func_184102_h().func_184103_al().func_152608_h();
            UserListBansEntry banEntry = new UserListBansEntry(new GameProfile(deadUUID, deadPlayer.func_70005_c_()), null, null, null, "Permadeath: ran out of hearts");
            banList.func_152687_a((UserListEntry)banEntry);
            deadPlayer.field_71135_a.func_194028_b((ITextComponent)new TextComponentString("\u00a7cYou have been permanently banned.\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.func_145747_a((ITextComponent)new TextComponentString("\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.getSource();
        Entity killer = source.func_76346_g();
        if (killer instanceof EntityPlayerMP) {
            int max;
            EntityPlayerMP killerPlayer = (EntityPlayerMP)killer;
            UUID killerUUID = killerPlayer.func_110124_au();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) {
                killerHearts = HeartSystemMod.config.getStartHearts();
            }
            if (killerHearts < (max = HeartSystemMod.config.getMaxHearts())) {
                HeartStorage.get().setHearts(killerUUID, ++killerHearts);
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
                killerPlayer.func_145747_a((ITextComponent)new TextComponentString("\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
                HeartSystemMod.logger.info("[HeartSystem] Player {} gained a heart from kill. Hearts: {}", (Object)killerPlayer.func_70005_c_(), (Object)killerHearts);
            } else {
                killerPlayer.func_145747_a((ITextComponent)new TextComponentString("\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }

    private void broadcastMessage(MinecraftServer server, String msg) {
        if (server == null) {
            return;
        }
        server.func_184103_al().func_148539_a((ITextComponent)new TextComponentString(msg));
    }
}

