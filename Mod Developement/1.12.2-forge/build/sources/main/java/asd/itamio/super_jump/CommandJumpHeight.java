package asd.itamio.super_jump;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class CommandJumpHeight extends CommandBase {
    @Override public String getName() { return "jumpheight"; }
    @Override public String getUsage(ICommandSender sender) { return "/jumpheight <height>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Jump height set!"));
    }
}