package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        EntityPlayer player = event.getPlayer();
        World world = event.world;
        BlockPos pos = event.pos;
        Block block = world.getBlockState(pos).getBlock();
        if (world.isRemote) return;
        if (player.capabilities.isCreativeMode) return;
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getTotalWorldTime();
            Long last = cooldowns.get(player.getUniqueID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUniqueID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool) {
            ItemStack held = player.getHeldItem();
            if (!isCorrectTool(block, held)) return;
        }
        Set<BlockPos> vein = findVein(world, pos, block, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, pos);
    }
    private boolean isVeinMineable(Block b) {
        if (VeinMinerMod.config.mineOres && (b==Blocks.coal_ore||b==Blocks.iron_ore||b==Blocks.gold_ore||b==Blocks.diamond_ore||b==Blocks.emerald_ore||b==Blocks.lapis_ore||b==Blocks.redstone_ore||b==Blocks.lit_redstone_ore||b==Blocks.quartz_ore)) return true;
        if (VeinMinerMod.config.mineLogs && (b==Blocks.log||b==Blocks.log2)) return true;
        if (VeinMinerMod.config.mineStone && (b==Blocks.stone||b==Blocks.cobblestone)) return true;
        if (VeinMinerMod.config.mineDirt && (b==Blocks.dirt||b==Blocks.grass)) return true;
        if (VeinMinerMod.config.mineGravel && b==Blocks.gravel) return true;
        if (VeinMinerMod.config.mineSand && b==Blocks.sand) return true;
        if (VeinMinerMod.config.mineClay && b==Blocks.clay) return true;
        if (VeinMinerMod.config.mineNetherrack && b==Blocks.netherrack) return true;
        if (VeinMinerMod.config.mineEndStone && b==Blocks.end_stone) return true;
        if (VeinMinerMod.config.mineGlowstone && b==Blocks.glowstone) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool == null || tool.getItem() == null) return false;
        String tc = tool.getItem().getToolClasses(tool).isEmpty() ? "" : (String)tool.getItem().getToolClasses(tool).iterator().next();
        if (b==Blocks.coal_ore||b==Blocks.iron_ore||b==Blocks.gold_ore||b==Blocks.diamond_ore||b==Blocks.emerald_ore||b==Blocks.lapis_ore||b==Blocks.redstone_ore||b==Blocks.lit_redstone_ore||b==Blocks.quartz_ore||b==Blocks.stone||b==Blocks.cobblestone||b==Blocks.netherrack||b==Blocks.end_stone||b==Blocks.glowstone) return tc.equals("pickaxe");
        if (b==Blocks.log||b==Blocks.log2) return tc.equals("axe");
        if (b==Blocks.dirt||b==Blocks.grass||b==Blocks.gravel||b==Blocks.sand||b==Blocks.clay) return tc.equals("shovel");
        return true;
    }
    private Set<BlockPos> findVein(World world, BlockPos start, Block target, int max) {
        Set<BlockPos> vein = new HashSet<BlockPos>();
        Queue<BlockPos> queue = new LinkedList<BlockPos>();
        queue.add(start); vein.add(start);
        int startMeta = world.getBlockState(start).getBlock().getMetaFromState(world.getBlockState(start));
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = new BlockPos(cur.getX()+dx, cur.getY()+dy, cur.getZ()+dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                Block nb_block = world.getBlockState(nb).getBlock();
                if (nb_block != target) continue;
                if (target==Blocks.log||target==Blocks.log2) {
                    int nbMeta = target.getMetaFromState(world.getBlockState(nb));
                    if ((nbMeta&3)!=(startMeta&3)) continue;
                }
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(World world, EntityPlayer player, Set<BlockPos> vein, BlockPos origin) {
        ItemStack tool = player.getHeldItem();
        List<ItemStack> allDrops = new ArrayList<ItemStack>();
        int mined = 0;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            List<ItemStack> drops = state.getBlock().getDrops(world, pos, state, player.experienceLevel);
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.setBlockToAir(pos); mined++;
            if (VeinMinerMod.config.consumeDurability && tool != null && tool.stackSize > 0) {
                tool.damageItem(1, player);
                if (tool.getItemDamage() >= tool.getMaxDamage()) { tool.stackSize = 0; break; }
            }
        }
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<String,ItemStack>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName()+":"+d.getItemDamage();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.stackSize+d.stackSize; ex.stackSize=Math.min(nc,ex.getMaxStackSize()); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.stackSize=nc-ex.getMaxStackSize();combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (s!=null&&s.stackSize>0) { EntityItem ei=new EntityItem(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickupDelay(); world.spawnEntityInWorld(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.addExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
