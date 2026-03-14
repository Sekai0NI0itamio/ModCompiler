package com.forsteri.createliquidfuel.core;

import com.forsteri.createliquidfuel.util.MathUtil;
import com.forsteri.createliquidfuel.util.Triplet;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllTags.AllItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

public class DrainableFuelLoader {
   public static final ResourceLocation IDENTIFIER = ResourceLocation.m_135822_("createliquidfuel:drainable_fuel_loader", ':');

   public static void load() {
      ForgeHooks.updateBurns();
      ForgeRegistries.ITEMS
         .forEach(
            item -> {
               ItemStack stack = item.m_7968_();
               int burnTime = ForgeHooks.getBurnTime(stack, null);
               if (burnTime > 0) {
                  stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                     .ifPresent(
                        handler -> {
                           if (handler.getTanks() == 1) {
                              boolean superHeats = AllItemTags.BLAZE_BURNER_FUEL_SPECIAL.matches(stack);
                              int amount = handler.getFluidInTank(0).getAmount();
                              if (BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP.get(handler.getFluidInTank(0).getFluid()) == null
                                 || !((ResourceLocation)BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP.get(handler.getFluidInTank(0).getFluid()).getFirst())
                                    .equals(LiquidBurnerFuelJsonLoader.IDENTIFIER)) {
                                 BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP
                                    .put(
                                       handler.getFluidInTank(0).getFluid(),
                                       Pair.of(
                                          IDENTIFIER,
                                          Triplet.of(
                                             superHeats ? 32 : burnTime / MathUtil.gcd(burnTime, amount),
                                             AllItemTags.BLAZE_BURNER_FUEL_SPECIAL.matches(stack),
                                             superHeats ? 10 : amount / MathUtil.gcd(burnTime, amount)
                                          )
                                       )
                                    );
                              }
                           }
                        }
                     );
               }
            }
         );
   }
}
