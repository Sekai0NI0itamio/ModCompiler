package com.itamio.snowaccumulation.forge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(SnowAccumulationForgeMod.MOD_ID)
public final class SnowAccumulationForgeMod {
    public static final String MOD_ID = "snowaccumulation";

    public SnowAccumulationForgeMod() {
        SnowAccumulationConfig.load();
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        System.out.println("[Snow Accumulation] Forge server logic loaded.");
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Object server = resolveServer(event);
        if (server != null) {
            SnowAccumulationHandler.onServerTick(server);
        }
    }

    private static Object resolveServer(TickEvent.ServerTickEvent event) {
        try {
            Method method = event.getClass().getMethod("getServer");
            return method.invoke(event);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Field field = event.getClass().getField("server");
            return field.get(event);
        } catch (ReflectiveOperationException ignored) {
        }

        for (String className : new String[] {
            "net.minecraftforge.server.ServerLifecycleHooks",
            "net.minecraftforge.fml.server.ServerLifecycleHooks",
        }) {
            try {
                Class<?> hooksClass = Class.forName(className);
                Method method = hooksClass.getMethod("getCurrentServer");
                return method.invoke(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }
}
