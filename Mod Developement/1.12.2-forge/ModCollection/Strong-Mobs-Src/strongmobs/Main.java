package com.strongmobs;

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
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

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
        MinecraftForge.EVENT_BUS.register(Main.class);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote && event.getEntity() instanceof EntityZombie) {
            EntityZombie zombie = (EntityZombie) event.getEntity();
            
            zombie.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.28D);
            zombie.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(256.0D); 
            zombie.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);

            net.minecraft.scoreboard.Scoreboard board = event.getWorld().getScoreboard();
            net.minecraft.scoreboard.ScorePlayerTeam team = board.getTeam("strong_zombies");
            if (team == null) {
                team = board.createTeam("strong_zombies");
                team.setCollisionRule(net.minecraft.scoreboard.Team.CollisionRule.NEVER);
            }
            board.addPlayerToTeam(zombie.getUniqueID().toString(), team.getName());
            
            zombie.targetTasks.addTask(0, new EntityAINearestAttackableTarget<>(zombie, EntityPlayer.class, false));
            zombie.tasks.addTask(1, new AIZombieSiege(zombie));
        }
    }
    
    public static class SiegeManager {
        public static BlockPos lastPlayerPos = null;
        public static long lastCalcTime = 0;
        
        public static class SiegePath {
            public EnumFacing dir;
            public List<BlockPos> walkingBlocks = new ArrayList<>();
            public Set<BlockPos> protectedBlocks = new HashSet<>();
            public List<Integer> workers = new ArrayList<>();
        }
        
        public static SiegePath[] paths = new SiegePath[4];
        
        public static void updatePaths(World world, EntityPlayer target) {
            BlockPos currentPos = target.getPosition();
            long currentTime = System.currentTimeMillis();
            
            if (lastPlayerPos != null && 
                lastPlayerPos.getX() == currentPos.getX() && 
                lastPlayerPos.getY() == currentPos.getY() && 
                lastPlayerPos.getZ() == currentPos.getZ()) {
                return;
            }
            if (currentTime - lastCalcTime < 3000) {
                return;
            }
            
            lastPlayerPos = currentPos;
            lastCalcTime = currentTime;
            
            List<Integer>[] oldWorkers = new List[4];
            for(int i=0; i<4; i++) {
                oldWorkers[i] = paths[i] != null ? paths[i].workers : new ArrayList<Integer>();
            }

            paths[0] = calcPath(world, currentPos, EnumFacing.EAST);
            paths[1] = calcPath(world, currentPos, EnumFacing.WEST);
            paths[2] = calcPath(world, currentPos, EnumFacing.SOUTH);
            paths[3] = calcPath(world, currentPos, EnumFacing.NORTH);
            
            for(int i=0; i<4; i++) {
                paths[i].workers = oldWorkers[i];
            }
            System.err.println("[Strong Mobs Debug] Siege paths calculated!");
        }
        
        private static SiegePath calcPath(World world, BlockPos target, EnumFacing dir) {
            SiegePath path = new SiegePath();
            path.dir = dir;
            
            int length = 48; // Build distance outward
            BlockPos endTarget = target.offset(dir, length);
            
            int endY = world.getHeight(endTarget.getX(), endTarget.getZ());
            if (endY <= 0) endY = target.getY();
            
            double targetWalkY = target.getY() - 1;
            double endWalkY = endY - 1;
            double yStep = (endWalkY - targetWalkY) / length;
            
            for (int i = 0; i <= length; i++) {
                int curX = target.getX() + dir.getDirectionVec().getX() * i;
                int curZ = target.getZ() + dir.getDirectionVec().getZ() * i;
                int curY = (int)Math.round(targetWalkY + yStep * i);
                
                BlockPos walkPos = new BlockPos(curX, curY, curZ);
                path.walkingBlocks.add(walkPos);
                
                path.protectedBlocks.add(walkPos);
                path.protectedBlocks.add(walkPos.down());
                path.protectedBlocks.add(walkPos.down(2));
            }
            return path;
        }
        
        public static int getBestPathIndex(int zombieId) {
            for (int i = 0; i < 4; i++) {
                if (paths[i] != null && paths[i].workers.contains(zombieId)) {
                    return i;
                }
            }
            if (paths[0] == null) return -1;
            
            int minWorkers = Integer.MAX_VALUE;
            int bestIndex = 0;
            for (int i = 0; i < 4; i++) {
                if (paths[i].workers.size() < minWorkers) {
                    minWorkers = paths[i].workers.size();
                    bestIndex = i;
                }
            }
            paths[bestIndex].workers.add(zombieId);
            return bestIndex;
        }
    }
    
    public static class AIZombieSiege extends EntityAIBase {
        private final EntityZombie zombie;
        private int breakingTime;
        private int previousBreakProgress = -1;
        private BlockPos breakingPos = null;
        private int attackTick = 0;
        private List<IBlockState> blockInventory = new ArrayList<>();
        private int buildCooldown = 0;

        public AIZombieSiege(EntityZombie zombie) {
            this.zombie = zombie;
            this.setMutexBits(3);
            
            Random rand = zombie.world.rand;
            IBlockState[] starterBlocks = new IBlockState[]{
                Blocks.DIRT.getDefaultState(),
                Blocks.PLANKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState(),
                Blocks.MOSSY_COBBLESTONE.getDefaultState()
            };
            for(int i = 0; i < 5; i++) {
                blockInventory.add(starterBlocks[rand.nextInt(starterBlocks.length)]);
            }
        }

        @Override
        public boolean shouldExecute() {
            return zombie.getAttackTarget() != null && zombie.getDistanceSq(zombie.getAttackTarget()) < 65536; 
        }

        @Override
        public void updateTask() {
            if (zombie.getAttackTarget() == null) return;

            EntityLivingBase target = zombie.getAttackTarget();
            World world = zombie.world;

            if (target instanceof EntityPlayer) {
                SiegeManager.updatePaths(world, (EntityPlayer) target);
            }

            int pathIndex = SiegeManager.getBestPathIndex(zombie.getEntityId());
            SiegeManager.SiegePath myPath = (pathIndex != -1) ? SiegeManager.paths[pathIndex] : null;

            double moveX = target.posX;
            double moveY = target.posY;
            double moveZ = target.posZ;
            
            BlockPos targetToBreak = null;
            BlockPos targetToBuild = null;

            if (myPath != null && !myPath.walkingBlocks.isEmpty()) {
                int closestIndex = -1;
                double minDist = Double.MAX_VALUE;
                for (int i = 0; i < myPath.walkingBlocks.size(); i++) {
                    BlockPos p = myPath.walkingBlocks.get(i);
                    double d = p.distanceSq(zombie.posX, zombie.posY, zombie.posZ);
                    if (d < minDist) {
                        minDist = d;
                        closestIndex = i;
                    }
                }
                
                int targetIndex = Math.max(0, closestIndex - 1);
                BlockPos pathTarget = myPath.walkingBlocks.get(targetIndex);
                moveX = pathTarget.getX() + 0.5;
                moveY = pathTarget.getY() + 1; 
                moveZ = pathTarget.getZ() + 0.5;

                double offset = ((zombie.getEntityId() % 5) - 2.0) * 0.2; 
                if (myPath.dir.getAxis() == EnumFacing.Axis.Z) {
                    moveX += offset; 
                } else {
                    moveZ += offset;
                }

                for (int i = closestIndex; i >= Math.max(0, closestIndex - 4); i--) {
                    BlockPos floor = myPath.walkingBlocks.get(i);
                    BlockPos body = floor.up();
                    BlockPos head = floor.up(2);
                    
                    boolean foundWork = false;

                    IBlockState bodyState = world.getBlockState(body);
                    if (!world.isAirBlock(body) && bodyState.getBlockHardness(world, body) >= 0 && !myPath.protectedBlocks.contains(body)) {
                        targetToBreak = body;
                        foundWork = true;
                    } else {
                        IBlockState headState = world.getBlockState(head);
                        if (!world.isAirBlock(head) && headState.getBlockHardness(world, head) >= 0 && !myPath.protectedBlocks.contains(head)) {
                            targetToBreak = head;
                            foundWork = true;
                        }
                    }
                    
                    if (foundWork) break;

                    if ((world.isAirBlock(floor) || world.getBlockState(floor).getMaterial().isReplaceable())) {
                        targetToBuild = floor;
                        break; 
                    }
                }
            }

            boolean hasPath = zombie.getNavigator().tryMoveToXYZ(moveX, moveY, moveZ, 1.0D);
            if (!hasPath || zombie.collidedHorizontally || zombie.getNavigator().noPath()) {
                zombie.getMoveHelper().setMoveTo(moveX, moveY, moveZ, 1.0D);
            }
            
            zombie.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
            
            this.attackTick = Math.max(this.attackTick - 1, 0);
            double distSq = zombie.getDistanceSq(target.posX, target.getEntityBoundingBox().minY, target.posZ);
            double reachSq = (double)(zombie.width * 2.0F * zombie.width * 2.0F + target.width);
            
            if (distSq <= reachSq && this.attackTick <= 0 && zombie.getEntitySenses().canSee(target)) {
                this.attackTick = 20;
                zombie.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
                zombie.attackEntityAsMob(target);
            }
            
            if (breakingPos != null) {
                if (myPath != null && myPath.protectedBlocks.contains(breakingPos)) {
                    resetMining(world);
                } else {
                    IBlockState breakingState = world.getBlockState(breakingPos);
                    if (!world.isAirBlock(breakingPos) && breakingState != null && breakingState.getBlockHardness(world, breakingPos) >= 0) {
                        breakBlockTick(world, breakingPos, breakingState);
                        if (breakingPos != null) {
                            zombie.getMoveHelper().setMoveTo(breakingPos.getX() + 0.5D, breakingPos.getY(), breakingPos.getZ() + 0.5D, 1.0D);
                        }
                        return; 
                    } else {
                        resetMining(world); 
                    }
                }
            }

            if (targetToBreak != null) {
                IBlockState state = world.getBlockState(targetToBreak);
                if (state.getBlockHardness(world, targetToBreak) >= 0) {
                    if (zombie.getDistanceSq(targetToBreak.getX() + 0.5, targetToBreak.getY() + 0.5, targetToBreak.getZ() + 0.5) < 25.0) {
                        breakBlockTick(world, targetToBreak, state);
                        return;
                    }
                }
            } else {
                resetMining(world);
            }
            
            if (buildCooldown > 0) buildCooldown--;
            
            if (targetToBuild != null && buildCooldown <= 0 && !blockInventory.isEmpty() && targetToBreak == null && breakingPos == null) {
                if (zombie.getDistanceSq(targetToBuild.getX() + 0.5, targetToBuild.getY() + 0.5, targetToBuild.getZ() + 0.5) < 16.0) {
                    IBlockState placeState = blockInventory.remove(blockInventory.size() - 1);
                    world.setBlockState(targetToBuild, placeState);
                    world.playEvent(2001, targetToBuild, Block.getStateId(placeState));
                    zombie.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
                    buildCooldown = 15;
                    
                    BlockPos currentPos = new BlockPos(zombie.posX, zombie.posY, zombie.posZ);
                    if (currentPos.equals(targetToBuild)) {
                        zombie.setPositionAndUpdate(zombie.posX, targetToBuild.getY() + 1.0, zombie.posZ);
                    }
                    return;
                }
            }
            
            if (zombie.collidedHorizontally && zombie.onGround) {
                zombie.getJumpHelper().setJumping();
            }
        }
        
        private void breakBlockTick(World world, BlockPos pos, IBlockState state) {
            if (breakingPos == null || !breakingPos.equals(pos)) {
                resetMining(world);
                breakingPos = pos;
            }

            breakingTime++;
            float hardness = state.getBlockHardness(world, pos);
            int timeToBreak = (int) (hardness * 40.0F); 
            if (timeToBreak <= 0) timeToBreak = 10;
            
            if (breakingTime % 20 == 0) {
                zombie.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
            }
            
            int progress = (int) ((float) breakingTime / timeToBreak * 10.0F);
            
            if (progress != previousBreakProgress) {
                world.sendBlockBreakProgress(zombie.getEntityId(), pos, progress);
                previousBreakProgress = progress;
            }
            
            if (breakingTime >= timeToBreak) {
                blockInventory.add(state); 
                world.destroyBlock(pos, false); 
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
