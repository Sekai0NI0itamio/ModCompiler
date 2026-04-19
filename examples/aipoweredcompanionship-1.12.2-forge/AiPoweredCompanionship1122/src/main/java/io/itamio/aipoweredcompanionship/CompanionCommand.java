package io.itamio.aipoweredcompanionship;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.*;

public final class CompanionCommand extends CommandBase {
    private final CompanionManager manager;
    private final CompanionBrainService brainService;

    public CompanionCommand(CompanionManager manager, CompanionBrainService brainService) {
        this.manager = manager;
        this.brainService = brainService;
    }

    @Override
    public String getName() { return "companion"; }

    @Override
    public int getRequiredPermissionLevel() { return 0; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/companion <spawn|remove|list|say|scan|terrain> [args...]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            throw new CommandException("Must be a player.");
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (args.length == 0) {
            throw new CommandException(getUsage(sender));
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "spawn": {
                if (args.length < 2) throw newCommandException("Usage: /companion spawn <name>");
                String name = args[1];
                if (manager.addCompanion(name, player)) {
                    tell(player, "Spawned companion '" + name + "'.");
                } else {
                    tell(player, "Failed to spawn (name taken?).");
                }
                break;
            }
            case "remove": {
                if (args.length < 2) throw newCommandException("Usage: /companion remove <name>");
                if (manager.removeCompanion(args[1])) {
                    tell(player, "Removed '" + args[1] + "'.");
                } else {
                    tell(player, "Not found.");
                }
                break;
            }
            case "list": {
                Collection<CompanionEntity> all = manager.getCompanions();
                if (all.isEmpty()) {
                    tell(player, "No companions.");
                } else {
                    StringBuilder sb = new StringBuilder("Companions:");
                    for (CompanionEntity c : all) sb.append(" ").append(c.getCompanionName());
                    tell(player, sb.toString());
                }
                break;
            }
            case "say": {
                if (args.length < 3) throw newCommandException("Usage: /companion say <name> <message>");
                String name = args[1];
                StringBuilder msg = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) msg.append(' ');
                    msg.append(args[i]);
                }
                manager.handleCommand(player, name, msg.toString());
                break;
            }
            case "scan": {
                if (args.length < 2) throw newCommandException("Usage: /companion scan <name>");
                CompanionEntity c = manager.getCompanionByName(args[1]);
                if (c == null) { tell(player, "Not found."); break; }
                tell(player, "Blocks:\n" + c.scanBlocks(CompanionConfig.data.scanRadius));
                tell(player, "Inventory:\n" + c.scanInventory());
                break;
            }
            case "terrain": {
                if (args.length < 2) throw newCommandException("Usage: /companion terrain <name> [radius]");
                CompanionEntity c = manager.getCompanionByName(args[1]);
                if (c == null) { tell(player, "Not found."); break; }
                int radius = args.length > 2 ? parseInt(args[2], 1, 32) : 10;
                tell(player, c.retrieveTerrain(radius));
                break;
            }
            default:
                throw newCommandException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "spawn", "remove", "list", "say", "scan", "terrain");
        return Collections.emptyList();
    }

    private static void tell(EntityPlayerMP player, String msg) {
        player.sendMessage(new net.minecraft.util.text.TextComponentString("[AIPC] " + msg));
    }

    private static CommandException newCommandException(String msg) {
        return new CommandException(msg);
    }
}
