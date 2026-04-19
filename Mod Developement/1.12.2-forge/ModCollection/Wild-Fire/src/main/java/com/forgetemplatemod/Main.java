package com.forgetemplatemod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = Main.MOD_ID, version = Main.VERSION, name = Main.NAME)
@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class Main {
    public static final String MOD_ID = "wild_fire";
    public static final String VERSION = "1.0";
    public static final String NAME = "Wild Fire";

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        // Register our custom fire that cleanly overrides vanilla behaviors
        event.getRegistry().register(new BlockWildFire());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Register newly "burnable" blocks based on the instructions
        Blocks.FIRE.setFireInfo(Blocks.GRASS, 60, 100);
        Blocks.FIRE.setFireInfo(Blocks.TALLGRASS, 60, 100);
        Blocks.FIRE.setFireInfo(Blocks.DOUBLE_PLANT, 60, 100);
        Blocks.FIRE.setFireInfo(Blocks.YELLOW_FLOWER, 60, 100);
        Blocks.FIRE.setFireInfo(Blocks.RED_FLOWER, 60, 100);
        Blocks.FIRE.setFireInfo(Blocks.LEAVES, 30, 60);
        Blocks.FIRE.setFireInfo(Blocks.LEAVES2, 30, 60);
        Blocks.FIRE.setFireInfo(Blocks.CHEST, 5, 20);
        Blocks.FIRE.setFireInfo(Blocks.TRAPPED_CHEST, 5, 20);
        Blocks.FIRE.setFireInfo(Blocks.CRAFTING_TABLE, 5, 20);
    }

    @SubscribeEvent
    public static void onArrowHit(ProjectileImpactEvent.Arrow event) {
        EntityArrow arrow = event.getArrow();
        World world = arrow.world;
        
        // Arrow extinguishes perfectly in water/rain due to vanilla engine, but we ensure it catches other things
        if (!world.isRemote && arrow.isBurning() && event.getRayTraceResult() != null && event.getRayTraceResult().typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            
            // if arrow lands in rain, extinguish immediately
            BlockPos hitPos = event.getRayTraceResult().getBlockPos();
            if (world.isRainingAt(hitPos)) {
            	arrow.extinguish();
            	return;
            }
            
            EnumFacing sideHit = event.getRayTraceResult().sideHit;
            BlockPos firePos = hitPos.offset(sideHit);
            
            // If hitting a burnable block and the space next to it is air
            if (world.isAirBlock(firePos)) {
                world.setBlockState(firePos, Blocks.FIRE.getDefaultState());
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        // Arrow rain extinguish is handled per-tick natively, but we can enforce it strictly here if needed
        if (event.phase == TickEvent.Phase.END) {
            for (net.minecraft.entity.Entity entity : event.world.loadedEntityList) {
                if (entity instanceof EntityArrow) {
                    EntityArrow arrow = (EntityArrow) entity;
                    if (arrow.isBurning() && event.world.isRainingAt(arrow.getPosition())) {
                        arrow.extinguish();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerPunchFire(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        
        // When punching the fire specifically or putting it out
        if (!world.isRemote) {
            BlockPos offset = pos.offset(event.getFace());
            if (world.getBlockState(offset).getBlock() == Blocks.FIRE) {
                if (player.getHeldItemMainhand().isEmpty()) {
                    player.attackEntityFrom(DamageSource.IN_FIRE, 2.0F); // 1 heart damage
                    player.setFire(3); // Sets them on fire briefly
                } else {
                    // Tool put out = no hurt, loss of dur
                    player.getHeldItemMainhand().damageItem(1, player);
                }
            }
        }
    }
}
