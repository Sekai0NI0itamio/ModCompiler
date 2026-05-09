package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// TEMPLATE NOTES (Forge 26.1+):
// - No mappings block in build.gradle — obfuscation removed in 26.1.
// - EventBus 7: @SubscribeEvent is now from net.minecraftforge.eventbus.api.listener
// - Constructor takes FMLJavaModLoadingContext; use context.getModBusGroup() for mod bus.
// - Cancellable listeners return boolean (true = cancel) instead of event.setCanceled(true).
// - Use EventName.BUS.addListener() for game bus events.
// - annotationProcessor 'net.minecraftforge:eventbus-validator:7.0.1' validates at compile time.
@Mod(ExampleMod.MODID)
public final class ExampleMod {
    public static final String MODID = "examplemod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        // Register mod lifecycle events on the mod bus group
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        // Register game/world events directly on their BUS field (EventBus 7)
        // Example: BlockEvent.FarmlandTrampleEvent.BUS.addListener(true, ExampleMod::onTrample)

        // Register our mod's config
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }
}
