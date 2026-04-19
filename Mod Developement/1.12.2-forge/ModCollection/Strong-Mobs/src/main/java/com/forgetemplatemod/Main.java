package com.forgetemplatemod;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = Main.MOD_ID, version = Main.VERSION, name = Main.NAME)
@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class Main {
    public static final String MOD_ID = "strong_mobs";
    public static final String VERSION = "1.0";
    public static final String NAME = "Strong Mobs";

    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("=================================================");
        logger.info("STRONG MOBS PRE-INIT! THE MOD IS ACTIVELY LOADED!");
        logger.info("=================================================");
        System.err.println("[Strong Mobs Debug] HARD-PRINT: MOD IS INITIALIZING!");
        
        // Let's forcefully register the event bus to guarantee it doesn't get skipped
        MinecraftForge.EVENT_BUS.register(Main.class);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        System.err.println("[Strong Mobs Debug] HARD-PRINT: INIT PHASE REACHED!");
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        // Changed to instanceof to ensure it catches all zombies even if they are slightly modified
        if (!event.getWorld().isRemote && event.getEntity() instanceof EntityZombie) {
            EntityZombie zombie = (EntityZombie) event.getEntity();
            
            // System.err prints bypass FML filters so it visibly pops up in Prism!
            System.err.println("[Strong Mobs Debug] Successfully injected into zombie ID: " + zombie.getEntityId());
            
            // Speed boost - normal is ~0.23D, sprint speeds are closer to 0.35D
            zombie.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35D);
            zombie.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(64.0D); // Give huge follow range
            
            // Instantly see and target player without extreme limitations
            zombie.targetTasks.addTask(1, new EntityAINearestAttackableTarget<>(zombie, EntityPlayer.class, true));

            // Overhaul custom block-solving AI (Mining and Pillar/Bridging)
            zombie.tasks.addTask(1, new AIZombieSiege(zombie));
        }
    }
    
    public static class AIZombieSiege extends EntityAIBase {
        private final EntityZombie zombie;
        private int breakingTime;
        private int previousBreakProgress = -1;
        private BlockPos breakingPos = null;
        private int blockCooldown = 0;

        public AIZombieSiege(EntityZombie zombie) {
            this.zombie = zombie;
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            return zombie.getAttackTarget() != null && zombie.getDistanceSq(zombie.getAttackTarget()) < 4096; // 64 blocks
        }

        @Override
        public void updateTask() {
            if (zombie.getAttackTarget() == null) return;

            EntityLivingBase target = zombie.getAttackTarget();
            World world = zombie.world;

            // Constantly path towards the player aggressively using MoveHelper to bypass PathNavigate getting stuck
            zombie.getMoveHelper().setMoveTo(target.posX, target.posY, target.posZ, 1.0D);
            // Look directly at target
            zombie.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
            
            if (blockCooldown > 0) {
                blockCooldown--;
            }
            
            double dX = target.posX - zombie.posX;
            double dZ = target.posZ - zombie.posZ;
            double dY = target.posY - zombie.posY;

            EnumFacing facing = EnumFacing.getFacingFromVector((float)dX, 0, (float)dZ);
            
            BlockPos currentPos = new BlockPos(zombie.posX, zombie.posY, zombie.posZ);
            BlockPos groundFront = currentPos.offset(facing);
            BlockPos eyeFront = currentPos.up().offset(facing);
            BlockPos gapBelowFront = currentPos.down().offset(facing);

            // 1. Evaluate bridging (If trying to cross a gap and no blocks are at equal foot level in front)
            if (zombie.onGround && world.isAirBlock(gapBelowFront) && world.isAirBlock(gapBelowFront.down()) && dY >= -3) {
                if (blockCooldown == 0 && zombie.getDistance(target) <= 16) { // Try bridging if within reasonable range
                    world.setBlockState(gapBelowFront, Blocks.COBBLESTONE.getDefaultState());
                    blockCooldown = 15; // Set short pause between bridge blocks
                    return; 
                }
            }

            // 2. Evaluate stepping up (if target is above us and wall is in front)
            if (zombie.collidedHorizontally && dY > 0.5 && blockCooldown == 0) {
                BlockPos stairPos = currentPos.offset(facing);
                if (world.isAirBlock(stairPos)) {
                    world.setBlockState(stairPos, Blocks.COBBLESTONE.getDefaultState());
                    zombie.getJumpHelper().setJumping();
                    blockCooldown = 20;
                    return;
                }
            }

            // 3. Evaluate breaking blocks in the way
            boolean isBlockedFront = !world.isAirBlock(eyeFront) || !world.isAirBlock(groundFront);
            
            if (zombie.collidedHorizontally || isBlockedFront) {
                // Check blocks blocking the way starting from top to bottom
                BlockPos targetToBreak = null;
                if (!world.isAirBlock(eyeFront) && world.getBlockState(eyeFront).getBlock() != Blocks.BEDROCK) {
                    targetToBreak = eyeFront;
                } else if (!world.isAirBlock(groundFront) && world.getBlockState(groundFront).getBlock() != Blocks.BEDROCK) {
                    targetToBreak = groundFront;
                }

                if (targetToBreak != null) {
                    IBlockState state = world.getBlockState(targetToBreak);
                    Block block = state.getBlock();
                    
                    if (state.getBlockHardness(world, targetToBreak) >= 0) {
                        breakBlockTick(world, targetToBreak, state);
                    } else {
                        resetMining(world);
                    }
                } else {
                    resetMining(world);
                }
            } else {
                resetMining(world);
            }
        }
        
        private void breakBlockTick(World world, BlockPos pos, IBlockState state) {
            if (breakingPos == null || !breakingPos.equals(pos)) {
                resetMining(world);
                breakingPos = pos;
                System.err.println("[Strong Mobs Debug] Zombie " + zombie.getEntityId() + " started mining block: " + state.getBlock().getLocalizedName() + " at " + pos);
            }

            breakingTime++;
            
            // Hardness simulated as hand breaking.
            float hardness = state.getBlockHardness(world, pos);
            int timeToBreak = (int) (hardness * 40.0F); 
            if (timeToBreak <= 0) timeToBreak = 10;
            
            if (breakingTime % 20 == 0) {
                System.err.println("[Strong Mobs Debug] Zombie " + zombie.getEntityId() + " mining progress: " + breakingTime + " / " + timeToBreak + " ticks");
                // make zombie swing arms
                zombie.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
            }
            
            int progress = (int) ((float) breakingTime / timeToBreak * 10.0F);
            
            if (progress != previousBreakProgress) {
                world.sendBlockBreakProgress(zombie.getEntityId(), pos, progress);
                previousBreakProgress = progress;
            }
            
            if (breakingTime >= timeToBreak) {
                System.err.println("[Strong Mobs Debug] Zombie " + zombie.getEntityId() + " successfully destroyed block at " + pos);
                world.destroyBlock(pos, true);
                resetMining(world);
            }
        }
        
        private void resetMining(World world) {
            if (breakingPos != null) {
                world.sendBlockBreakProgress(zombie.getEntityId(), breakingPos, -1);
                breakingPos = null;
            }
            breakingTime = 0;
            previousBreakProgress = -1;
        }

        @Override
        public void resetTask() {
            super.resetTask();
            resetMining(zombie.world);
        }
    }
}
