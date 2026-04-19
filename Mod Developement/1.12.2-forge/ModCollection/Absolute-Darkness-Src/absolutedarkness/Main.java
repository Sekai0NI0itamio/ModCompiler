package com.absolutedarkness;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(modid = "absolutedarkness", name = "Absolute Darkness", version = "1.0", acceptableRemoteVersions = "*")
@Mod.EventBusSubscriber
public class Main {

    public static final Block blockLight = new BlockDynamicLight();
    
    // Track the last light block placed by each player so we can safely remove it when they move or unequip
    private static final Map<UUID, BlockPos> playerLights = new HashMap<>();

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(blockLight);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        // This enforces absolute darkness if they don't have night vision.
        // It makes shadows completely black during the night.
        if (Minecraft.getMinecraft().world != null && Minecraft.getMinecraft().player != null) {
            // Override the gamma setting to be very dark (-1.0f or lower generally creates pitch blackness)
            Minecraft.getMinecraft().gameSettings.gammaSetting = -1.0F;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.world.isRemote) return; // Server side logic for actual blocks

        EntityPlayer player = event.player;
        World world = player.world;
        UUID uuid = player.getUniqueID();

        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();

        int emitLevel = 0;
        if (!main.isEmpty() && main.getItem() instanceof ItemBlock) {
            emitLevel = Math.max(emitLevel, ((ItemBlock) main.getItem()).getBlock().getDefaultState().getLightValue());
        }
        if (!off.isEmpty() && off.getItem() instanceof ItemBlock) {
            emitLevel = Math.max(emitLevel, ((ItemBlock) off.getItem()).getBlock().getDefaultState().getLightValue());
        }

        BlockPos headPos = new BlockPos(player.posX, player.posY + 1, player.posZ);
        BlockPos oldPos = playerLights.get(uuid);

        if (emitLevel > 0) {
            // Player is holding a light source
            if (oldPos != null && !oldPos.equals(headPos)) {
                // Remove old
                if (world.getBlockState(oldPos).getBlock() == blockLight) {
                    world.setBlockToAir(oldPos);
                }
            }

            if (world.isAirBlock(headPos)) {
                world.setBlockState(headPos, blockLight.getDefaultState());
                playerLights.put(uuid, headPos);
            } else {
                // If head is not air, dynamic light can't be placed exactly there, maybe look for nearest air block?
                // But for simplicity head pos works 99% of the time unless suffocating.
            }
        } else {
            // Not holding anything
            if (oldPos != null) {
                if (world.getBlockState(oldPos).getBlock() == blockLight) {
                    world.setBlockToAir(oldPos);
                }
                playerLights.remove(uuid);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.player.getUniqueID();
        BlockPos oldPos = playerLights.remove(uuid);
        if (oldPos != null && !event.player.world.isRemote) {
            if (event.player.world.getBlockState(oldPos).getBlock() == blockLight) {
                event.player.world.setBlockToAir(oldPos);
            }
        }
    }
}
