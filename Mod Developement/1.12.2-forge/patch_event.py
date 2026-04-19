code = """package com.bothelpers.events;

import com.bothelpers.data.NamedLocationsData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = "bothelpers")
public class BotHelpersEventHandler {

    @SubscribeEvent
    public static void onRightClickSign(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND) return;
        if (event.getItemStack().isEmpty()) { 
            IBlockState state = event.getWorld().getBlockState(event.getPos());
            if (state.getBlock() == Blocks.STANDING_SIGN || state.getBlock() == Blocks.WALL_SIGN) {
                TileEntity te = event.getWorld().getTileEntity(event.getPos());
                if (te instanceof TileEntitySign) {
                    TileEntitySign sign = (TileEntitySign) te;
                    
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 4; i++) {
                        if (sign.signText[i] != null) {
                            String s = sign.signText[i].getUnformattedText().trim();
                            if (!s.isEmpty()) {
                                if (sb.length() > 0) sb.append(" ");
                                sb.append(s);
                            }
                        }
                    }
                    
                    String name = sb.toString().trim();
                    if (!name.isEmpty()) {
                        BlockPos targetPos = event.getPos().down(); 
                        if (state.getBlock() == Blocks.WALL_SIGN) {
                            EnumFacing facing = state.getValue(net.minecraft.block.BlockWallSign.FACING);
                            targetPos = event.getPos().offset(facing.getOpposite());
                        }
                        
                        IBlockState targetState = event.getWorld().getBlockState(targetPos);
                        String blockTypeName = targetState.getBlock().getLocalizedName();

                        NamedLocationsData data = NamedLocationsData.get(event.getWorld());
                        if (data != null) {
                            data.addLocation(name, targetPos, blockTypeName);
                            event.getEntityPlayer().sendMessage(new TextComponentString("Bot Helpers: Registered location '" + name + "' at (" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ") on " + blockTypeName));
                        }
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onBreakSign(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) return;
        IBlockState state = event.getState();
        if (state.getBlock() == Blocks.STANDING_SIGN || state.getBlock() == Blocks.WALL_SIGN) {
            TileEntity te = event.getWorld().getTileEntity(event.getPos());
            if (te instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) te;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    if (sign.signText[i] != null) {
                        String s = sign.signText[i].getUnformattedText().trim();
                        if (!s.isEmpty()) {
                            if (sb.length() > 0) sb.append(" ");
                            sb.append(s);
                        }
                    }
                }
                String name = sb.toString().trim();
                if (!name.isEmpty()) {
                    NamedLocationsData data = NamedLocationsData.get(event.getWorld());
                    if (data != null) {
                        data.removeLocation(name);
                        event.getPlayer().sendMessage(new TextComponentString("Bot Helpers: Unregistered location '" + name + "'"));
                    }
                }
            }
        }
    }
}
"""
with open("src/main/java/com/bothelpers/events/BotHelpersEventHandler.java", "w") as f:
    f.write(code)
