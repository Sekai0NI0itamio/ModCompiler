package io.itamio.unlimited_fill;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;

public class UnlimitedFillExecutor {

    public static void executeFill(ICommandSender sender, String[] args) throws CommandException {
        // Parse arguments similar to CommandFill
        if (args.length < 6) {
            throw new WrongUsageException("commands.fill.usage");
        }

        BlockPos from = CommandBase.parseBlockPos(sender, args, 0, false);
        BlockPos to = CommandBase.parseBlockPos(sender, args, 3, false);

        // Determine block and metadata
        Block block = CommandBase.getBlockByText(sender, args[5]);
        int meta = args.length >= 7 ? CommandBase.parseInt(args[6], 0, 15) : 0;

        // Parse mode
        String mode = args.length >= 8 ? args[7] : "replace";
        if (!Arrays.asList("replace", "destroy", "outline", "keep").contains(mode.toLowerCase(Locale.ROOT))) {
            throw new WrongUsageException("commands.fill.usage");
        }

        // Determine block state
        IBlockState state;
        try {
            state = block.getStateFromMeta(meta);
        } catch (Exception e) {
            throw new WrongUsageException("commands.fill.usage");
        }

        // Create the fill task
        WorldServer world = (WorldServer) sender.getEntityWorld();
        FillTask task = new FillTask(world, from, to, state, mode);
        world.addScheduledTask(task);
    }
}