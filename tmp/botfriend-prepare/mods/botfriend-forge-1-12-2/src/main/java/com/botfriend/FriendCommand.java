package com.botfriend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public final class FriendCommand extends CommandBase {
    private final FriendManager friendManager;
    private final FriendBrainService brainService;

    public FriendCommand(FriendManager friendManager, FriendBrainService brainService) {
        this.friendManager = friendManager;
        this.brainService = brainService;
    }

    @Override
    public String getName() {
        return "friend";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/friend <add|remove|list|say|stop|reloadconfig> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }

        String sub = args[0].toLowerCase();
        if ("reloadconfig".equals(sub)) {
            BotFriendConfig.load(BotFriendMod.LOGGER);
            brainService.reloadConfig();
            notifyCommandListener(sender, this, "BotFriend config reloaded.");
            return;
        }

        EntityPlayerMP player = requirePlayer(sender);
        if ("add".equals(sub)) {
            if (args.length != 2) {
                throw new WrongUsageException("/friend add <name>");
            }
            String result = friendManager.createFriend(player, args[1]);
            notifyCommandListener(sender, this, result);
            return;
        }
        if ("remove".equals(sub)) {
            if (args.length != 2) {
                throw new WrongUsageException("/friend remove <name>");
            }
            String result = friendManager.removeFriend(player, args[1]);
            notifyCommandListener(sender, this, result);
            return;
        }
        if ("list".equals(sub)) {
            List<String> names = friendManager.listOwnedFriendNames(player.getUniqueID());
            if (names.isEmpty()) {
                notifyCommandListener(sender, this, "You do not own any friends.");
            } else {
                notifyCommandListener(sender, this, "Your friends: " + String.join(", ", names));
            }
            return;
        }
        if ("say".equals(sub)) {
            if (args.length < 3) {
                throw new WrongUsageException("/friend say <name> <message>");
            }
            String name = args[1];
            String message = buildString(args, 2);
            String result = friendManager.sendInstruction(player, name, message, true);
            notifyCommandListener(sender, this, result);
            return;
        }
        if ("stop".equals(sub)) {
            if (args.length != 2) {
                throw new WrongUsageException("/friend stop <name>");
            }
            String result = friendManager.stopFriend(player, args[1]);
            notifyCommandListener(sender, this, result);
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "add", "remove", "list", "say", "stop", "reloadconfig");
        }
        if (args.length == 2 && sender.getCommandSenderEntity() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
            return getListOfStringsMatchingLastWord(args, friendManager.listOwnedFriendNames(player.getUniqueID()));
        }
        return Collections.emptyList();
    }

    private static EntityPlayerMP requirePlayer(ICommandSender sender) throws PlayerNotFoundException {
        if (sender.getCommandSenderEntity() instanceof EntityPlayerMP) {
            return (EntityPlayerMP) sender.getCommandSenderEntity();
        }
        throw new PlayerNotFoundException("This command can only be used by a player.");
    }
}
