/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common;

import net.minecraft.block.DispenserBlock;
import net.minecraft.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.item.Item.Properties;

public class ForgeSpawnEggItem extends SpawnEggItem
{
    private static final List<ForgeSpawnEggItem> MOD_EGGS = new ArrayList<>();
    private static final Map<EntityType<?>, ForgeSpawnEggItem> TYPE_MAP = new IdentityHashMap<>();
    private final Supplier<? extends EntityType<?>> typeSupplier;

    public ForgeSpawnEggItem(Supplier<? extends EntityType<?>> type, int backgroundColor, int highlightColor, Properties props)
    {
        super((EntityType<?>) null, backgroundColor, highlightColor, props);
        this.typeSupplier = type;

        MOD_EGGS.add(this);
    }

    @Override
    public EntityType<?> func_208076_b(@Nullable CompoundNBT tag)
    {
        EntityType<?> type = super.func_208076_b(tag);
        return type != null ? type : typeSupplier.get();
    }

    @Nullable
    protected DefaultDispenseItemBehavior createDispenseBehavior()
    {
        return DEFAULT_DISPENSE_BEHAVIOR;
    }

    @Nullable
    public static SpawnEggItem fromEntityType(@Nullable EntityType<?> type)
    {
        SpawnEggItem ret = TYPE_MAP.get(type);
        return ret != null ? ret : SpawnEggItem.func_200889_b(type);
    }



    private static final DefaultDispenseItemBehavior DEFAULT_DISPENSE_BEHAVIOR = new DefaultDispenseItemBehavior()
    {
        protected ItemStack func_82487_b(IBlockSource source, ItemStack stack)
        {
            Direction face = source.func_189992_e().func_177229_b(DispenserBlock.field_176441_a);
            EntityType<?> type = ((SpawnEggItem) stack.func_77973_b()).func_208076_b(stack.func_77978_p());

            // FORGE: fix potential crash
            try
            {
                type.func_220331_a(source.func_197524_h(), stack, null, source.func_180699_d().func_177972_a(face), SpawnReason.DISPENSER, face != Direction.UP, false);
            }
            catch (Exception exception)
            {
                DefaultDispenseItemBehavior.LOGGER.error("Error while dispensing spawn egg from dispenser at {}", source.func_180699_d(), exception);
                return ItemStack.field_190927_a;
            }

            stack.func_190918_g(1);
            return stack;
        }
    };

    @Mod.EventBusSubscriber(modid = "forge", bus = Mod.EventBusSubscriber.Bus.MOD)
    private static class CommonHandler
    {
        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event)
        {
            MOD_EGGS.forEach(egg ->
            {
                DefaultDispenseItemBehavior dispenseBehavior = egg.createDispenseBehavior();
                if (dispenseBehavior != null)
                {
                    DispenserBlock.func_199774_a(egg, dispenseBehavior);
                }

                TYPE_MAP.put(egg.typeSupplier.get(), egg);
            });
        }
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "forge", bus = Mod.EventBusSubscriber.Bus.MOD)
    private static class ColorRegisterHandler
    {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void registerSpawnEggColors(ColorHandlerEvent.Item event)
        {
            MOD_EGGS.forEach(egg ->
                    event.getItemColors().func_199877_a((stack, layer) -> egg.func_195983_a(layer), egg)
            );
        }
    }
}