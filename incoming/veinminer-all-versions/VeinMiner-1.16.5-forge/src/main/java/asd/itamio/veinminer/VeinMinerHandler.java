package asd.itamio.veinminer;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
public class VeinMinerHandler {
    private Map<UUID, Long> cooldowns = new HashMap<>();
    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) return;
        if (!VeinMinerKeyHandler.veinMinerEnabled) return;
        PlayerEntity player = event.getPlayer();
        World world = (World) event.getWorld();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        if (VeinMinerMod.config.requireSneak && !player.isCrouching()) return;
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long now = world.getGameTime();
            Long last = cooldowns.get(player.getUUID());
            if (last != null && now - last < VeinMinerMod.config.cooldownTicks) return;
            cooldowns.put(player.getUUID(), now);
        }
        if (!isVeinMineable(block)) return;
        if (VeinMinerMod.config.limitToCorrectTool && !isCorrectTool(block, player.getMainHandItem())) return;
        Set<BlockPos> vein = findVein(world, pos, block, state, VeinMinerMod.config.maxBlocks);
        if (vein.size() > 1) mineVein(world, player, vein, state, pos);
    }
    private boolean isVeinMineable(Block b) {
        String n = b.getRegistryName()==null?"":b.getRegistryName().toString();
        if (VeinMinerMod.config.mineOres && (n.equals("minecraft:coal_ore")||n.equals("minecraft:iron_ore")||n.equals("minecraft:gold_ore")||n.equals("minecraft:diamond_ore")||n.equals("minecraft:emerald_ore")||n.equals("minecraft:lapis_ore")||n.equals("minecraft:redstone_ore")||n.equals("minecraft:nether_quartz_ore"))) return true;
        if (VeinMinerMod.config.mineLogs && (n.contains("_log")||n.contains("_wood"))) return true;
        if (VeinMinerMod.config.mineStone && (n.equals("minecraft:stone")||n.equals("minecraft:cobblestone"))) return true;
        if (VeinMinerMod.config.mineDirt && (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block"))) return true;
        if (VeinMinerMod.config.mineGravel && n.equals("minecraft:gravel")) return true;
        if (VeinMinerMod.config.mineSand && n.equals("minecraft:sand")) return true;
        if (VeinMinerMod.config.mineClay && n.equals("minecraft:clay")) return true;
        if (VeinMinerMod.config.mineNetherrack && n.equals("minecraft:netherrack")) return true;
        if (VeinMinerMod.config.mineEndStone && n.equals("minecraft:end_stone")) return true;
        if (VeinMinerMod.config.mineGlowstone && n.equals("minecraft:glowstone")) return true;
        return false;
    }
    private boolean isCorrectTool(Block b, ItemStack tool) {
        if (tool.isEmpty()) return false;
        String n = b.getRegistryName()==null?"":b.getRegistryName().toString();
        if (n.contains("_ore")||n.equals("minecraft:stone")||n.equals("minecraft:cobblestone")||n.equals("minecraft:netherrack")||n.equals("minecraft:end_stone")||n.equals("minecraft:glowstone")) return tool.getItem() instanceof net.minecraft.item.PickaxeItem;
        if (n.contains("_log")||n.contains("_wood")) return tool.getItem() instanceof net.minecraft.item.AxeItem;
        if (n.equals("minecraft:dirt")||n.equals("minecraft:grass_block")||n.equals("minecraft:gravel")||n.equals("minecraft:sand")||n.equals("minecraft:clay")) return tool.getItem() instanceof net.minecraft.item.ShovelItem;
        return true;
    }
    private Set<BlockPos> findVein(World world, BlockPos start, Block target, BlockState startState, int max) {
        Set<BlockPos> vein = new HashSet<>(); Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start); vein.add(start);
        String sn = target.getRegistryName()==null?"":target.getRegistryName().toString();
        boolean isLog = sn.contains("_log")||sn.contains("_wood");
        while (!queue.isEmpty()&&vein.size()<max) {
            BlockPos cur = queue.poll();
            for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dy==0&&dz==0) continue;
                BlockPos nb = cur.offset(dx,dy,dz);
                if (vein.contains(nb)||vein.size()>=max) continue;
                BlockState nbs = world.getBlockState(nb);
                if (nbs.getBlock()!=target) continue;
                if (isLog&&nbs!=startState) continue;
                vein.add(nb); queue.add(nb);
            }
        }
        return vein;
    }
    private void mineVein(World world, PlayerEntity player, Set<BlockPos> vein, BlockState origState, BlockPos origin) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> allDrops = new ArrayList<>(); int mined = 0;
        net.minecraft.world.server.ServerWorld sl = (net.minecraft.world.server.ServerWorld) world;
        for (BlockPos pos : vein) {
            if (pos.equals(origin)) continue;
            BlockState state = world.getBlockState(pos);
            List<ItemStack> drops = net.minecraft.block.Block.getDrops(state, sl, pos, world.getBlockEntity(pos));
            for (ItemStack d : drops) allDrops.add(d.copy());
            world.removeBlock(pos, false); mined++;
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.hurtAndBreak(1, player, p -> {});
                if (tool.isEmpty()) break;
            }
        }
        if (!VeinMinerMod.config.disableSound) world.playSound(null, origin, origState.getSoundType().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
        if (VeinMinerMod.config.dropAtOneLocation) {
            Map<String,ItemStack> combined = new HashMap<>();
            for (ItemStack d : allDrops) {
                String key = d.getItem().getRegistryName()+":"+d.getDamageValue();
                if (combined.containsKey(key)) { ItemStack ex=combined.get(key); int nc=ex.getCount()+d.getCount(); ex.setCount(Math.min(nc,ex.getMaxStackSize())); if(nc>ex.getMaxStackSize()){ItemStack ov=d.copy();ov.setCount(nc-ex.getMaxStackSize());combined.put(key+"_"+combined.size(),ov);} } else combined.put(key,d.copy());
            }
            for (ItemStack s : combined.values()) if (!s.isEmpty()) { ItemEntity ei=new ItemEntity(world,origin.getX()+0.5,origin.getY()+0.5,origin.getZ()+0.5,s); ei.setDefaultPickUpDelay(); world.addFreshEntity(ei); }
        }
        if (VeinMinerMod.config.consumeHunger) player.causeFoodExhaustion(0.005f*mined*VeinMinerMod.config.hungerMultiplier);
    }
}
