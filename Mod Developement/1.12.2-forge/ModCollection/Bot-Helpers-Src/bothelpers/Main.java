package com.bothelpers;

import com.bothelpers.entity.EntityBotHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;

@Mod(modid = "bothelpers", name = "Bot Helpers", version = "1.0")
public class Main {

    @Mod.Instance
    public static Main instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        EntityRegistry.registerModEntity(new ResourceLocation("bothelpers", "bothelper"), 
            EntityBotHelper.class, "BotHelper", 1, instance, 64, 3, true, 0x00FF00, 0x0000FF);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Networking & GUI registry would go here
    }

    @Mod.EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBotHelper());
    }
}
