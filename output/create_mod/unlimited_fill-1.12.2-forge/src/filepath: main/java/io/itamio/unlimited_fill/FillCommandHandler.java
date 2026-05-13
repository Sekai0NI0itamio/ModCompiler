package io.itamio.unlimited_fill;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandFill;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;

import static io.itamio.unlimited_fill.UnlimitedFillMod.LOGGER;

public class FillCommandHandler {

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        // Intercept the /fill command to remove block limit and add batching
        if (event.getCommand() instanceof CommandFill) {
            event.setCanceled(true);
            MinecraftServer server = event.getSender().getServer();
            if (server != null) {
                server.addScheduledTask(() -> {
                    try {
                        UnlimitedFillExecutor.executeFill(event.getSender(), event.getParameters());
                    } catch (CommandException e) {
                        event.getSender().sendMessage(e.getComponent());
                    }
                });
            }
        }
    }
}