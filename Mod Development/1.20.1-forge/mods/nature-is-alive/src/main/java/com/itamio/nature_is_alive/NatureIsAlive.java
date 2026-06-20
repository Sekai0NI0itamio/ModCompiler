package com.itamio.nature_is_alive;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(NatureIsAlive.MODID)
public class NatureIsAlive {
    public static final String MODID = "nature_is_alive";

    public NatureIsAlive() {
        NIAConfig.load(FMLPaths.CONFIGDIR.get());
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(new NatureHandler());
        MinecraftForge.EVENT_BUS.register(new StepEventHandler());
        MinecraftForge.EVENT_BUS.register(NIACommand.class);
        MinecraftForge.EVENT_BUS.register(new SkeletonDeathTracker());
    }
}
