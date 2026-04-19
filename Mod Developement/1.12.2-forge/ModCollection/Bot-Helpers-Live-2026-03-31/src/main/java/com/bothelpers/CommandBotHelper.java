package com.bothelpers;

import com.bothelpers.entity.EntityBotHelper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.Random;

public class CommandBotHelper extends CommandBase {
    
    private static final String[] FIRST_NAMES = {"Bob", "Alice", "Steve", "Alex", "John", "Sarah"};
    private static final String[] LAST_NAMES = {"Smith", "Doe", "Johnson", "Brown", "Miller"};
    private final Random rand = new Random();

    @Override
    public String getName() {
        return "bothelper";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/bothelper spawn [name]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 1 && args[0].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof EntityPlayer)) return;
            EntityPlayer player = (EntityPlayer) sender;
            World world = player.world;

            String name = "";
            if (args.length >= 2) {
                name = args[1];
            } else {
                int attempts = 0;
                while (attempts < 3) {
                    name = FIRST_NAMES[rand.nextInt(FIRST_NAMES.length)] + " " + LAST_NAMES[rand.nextInt(LAST_NAMES.length)];
                    if (!nameExists(world, name)) break;
                    attempts++;
                }
                if (attempts >= 3) name = LAST_NAMES[rand.nextInt(LAST_NAMES.length)] + rand.nextInt(100);
            }

            EntityBotHelper bot = new EntityBotHelper(world);
            bot.setCustomNameTag(name);
            bot.setAlwaysRenderNameTag(true);
            bot.setLocationAndAngles(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
            world.spawnEntity(bot);
            
            // Send greeting directly to the player who spawned it
            player.sendMessage(new net.minecraft.util.text.TextComponentString("<" + name + "> Hello, how can I help you?"));
        }
    }

    private boolean nameExists(World world, String name) {
        for (EntityPlayer p : world.playerEntities) if (p.getName().equalsIgnoreCase(name)) return true;
        for (net.minecraft.entity.Entity e : world.loadedEntityList) {
            if (e instanceof EntityBotHelper && e.getCustomNameTag().equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
