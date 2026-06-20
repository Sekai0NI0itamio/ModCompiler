package com.itamio.nature_is_alive;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class NIACommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("nia")
            .requires(source -> source.hasPermission(2))

            .then(Commands.literal("updatetickspeed")
                .then(Commands.literal("random")
                    .then(Commands.argument("min", IntegerArgumentType.integer(1))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int min = IntegerArgumentType.getInteger(ctx, "min");
                                int max = IntegerArgumentType.getInteger(ctx, "max");
                                if (min > max) {
                                    ctx.getSource().sendFailure(Component.literal("Min cannot be greater than max."));
                                    return 0;
                                }
                                NIAConfig.setTickInterval(min, max);
                                ctx.getSource().sendSuccess(() -> Component.literal("Tick interval set to random(" + min + ", " + max + ")"), true);
                                return 1;
                            })
                        )
                    )
                )
            )

            .then(Commands.literal("speed")
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0))
                    .executes(ctx -> {
                        double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                        NIAConfig.set("globalSpeedMultiplier", mult);
                        NIAConfig.save(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
                        ctx.getSource().sendSuccess(() -> Component.literal("Global speed multiplier set to " + mult + "x (1.0 = normal)"), true);
                        return 1;
                    })
                )
            )

            .then(Commands.literal("set")
                .then(Commands.argument("key", StringArgumentType.word())
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                        .executes(ctx -> {
                            String key = StringArgumentType.getString(ctx, "key");
                            double value = DoubleArgumentType.getDouble(ctx, "value");
                            try {
                                NIAConfig.set(key, value);
                                NIAConfig.save(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
                                ctx.getSource().sendSuccess(() -> Component.literal("Set " + key + " = " + value), true);
                                return 1;
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(Component.literal("Unknown config key: " + key));
                                return 0;
                            }
                        })
                    )
                )
            )

            .then(Commands.literal("list")
                .executes(ctx -> {
                    StringBuilder sb = new StringBuilder("NIA Config:\n");
                    sb.append("tickIntervalMin=").append(NIAConfig.getTickIntervalMin()).append("\n");
                    sb.append("tickIntervalMax=").append(NIAConfig.getTickIntervalMax()).append("\n");
                    sb.append("tickRadius=").append(NIAConfig.getTickRadius()).append("\n");
                    sb.append("mossConversionRadius=").append(NIAConfig.getMossConversionRadius()).append("\n");
                    sb.append("maxBlocksPerTick=").append(NIAConfig.getMaxBlocksPerTick()).append("\n");
                    sb.append("maxWritesPerTick=").append(NIAConfig.getMaxWritesPerTick()).append("\n");
                    sb.append("mossConversionChance=").append(NIAConfig.getMossConversionChance()).append("\n");
                    sb.append("decayChance=").append(NIAConfig.getDecayChance()).append("\n");
                    sb.append("grassSpreadChance=").append(NIAConfig.getGrassSpreadChance()).append("\n");
                    sb.append("cobbleDowngradeChance=").append(NIAConfig.getCobbleDowngradeChance()).append("\n");
                    sb.append("crackedDowngradeChance=").append(NIAConfig.getCrackedDowngradeChance()).append("\n");
                    sb.append("polishedDowngradeChance=").append(NIAConfig.getPolishedDowngradeChance()).append("\n");
                    sb.append("grassSpawnChance=").append(NIAConfig.getGrassSpawnChance()).append("\n");
                    sb.append("flowerSpawnChance=").append(NIAConfig.getFlowerSpawnChance()).append("\n");
                    sb.append("grassGrowthChance=").append(NIAConfig.getGrassGrowthChance()).append("\n");
                    sb.append("saplingSpawnChance=").append(NIAConfig.getSaplingSpawnChance()).append("\n");
                    sb.append("saplingGrowthChance=").append(NIAConfig.getSaplingGrowthChance()).append("\n");
                    sb.append("vineGrowthChance=").append(NIAConfig.getVineGrowthChance()).append("\n");
                    sb.append("cobwebSpawnChance=").append(NIAConfig.getCobwebSpawnChance()).append("\n");
                    sb.append("waterErosionChance=").append(NIAConfig.getWaterErosionChance()).append("\n");
                    sb.append("snowAccumulationChance=").append(NIAConfig.getSnowAccumulationChance()).append("\n");
                    sb.append("mudFormationChance=").append(NIAConfig.getMudFormationChance()).append("\n");
                    sb.append("pathFormationSteps=").append(NIAConfig.getPathFormationSteps()).append("\n");
                    sb.append("pathDegradationChance=").append(NIAConfig.getPathDegradationChance()).append("\n");
                    sb.append("boneMealChance=").append(NIAConfig.getBoneMealChance()).append("\n");
                    sb.append("iceCrackChance=").append(NIAConfig.getIceCrackChance()).append("\n");
                    sb.append("copperOxidationChance=").append(NIAConfig.getCopperOxidationChance()).append("\n");
                    sb.append("globalSpeedMultiplier=").append(NIAConfig.getGlobalSpeedMultiplier());
                    ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                    return 1;
                })
            )

            .then(Commands.literal("save")
                .executes(ctx -> {
                    NIAConfig.save(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
                    ctx.getSource().sendSuccess(() -> Component.literal("Config saved to config/natureisalive/config.txt"), true);
                    return 1;
                })
            )
        );
    }
}
