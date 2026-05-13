package io.itamio.unlimited_fill;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Custom command /smartfill that performs an unlimited, optimized fill.
 * Usage: /smartfill <x1> <y1> <z1> <x2> <y2> <z2> <block> [data] [destroy|replace|keep|outline]
 */
public class CommandSmartFill extends CommandBase {

    @Override
    public String getName() {
        return "smartfill";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.smartfill.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 6) {
            throw new WrongUsageException("commands.smartfill.usage");
        }

        BlockPos from = parseBlockPos(sender, args, 0, false);
        BlockPos to = parseBlockPos(sender, args, 3, false);

        String blockName = args[5];
        int meta = args.length >= 7 ? parseInt(args[6], 0, 15) : 0;
        String mode = args.length >= 8 ? args[7].toLowerCase(Locale.ROOT) : "replace";

        if (!("replace".equals(mode) || "destroy".equals(mode) || "keep".equals(mode) || "outline".equals(mode))) {
            throw new WrongUsageException("commands.smartfill.usage");
        }

        // Use UnlimitedFillExecutor
        UnlimitedFillExecutor.executeFill(sender, args);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length <= 5) {
            return getTabCompletionCoordinate(args, 0, targetPos);
        }
        if (args.length == 6) {
            return getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
        }
        if (args.length == 8) {
            return getListOfStringsMatchingLastWord(args, "replace", "destroy", "keep", "outline");
        }
        return Collections.emptyList();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}