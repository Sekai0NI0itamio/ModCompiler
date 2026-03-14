package com.forsteri.createliquidfuel.core;

import com.forsteri.createliquidfuel.util.Triplet;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.Registries;

public final class LiquidBurnerFuelJsonLoader extends JsonDataLoader implements IdentifiableResourceReloadListener {
    public static final Identifier IDENTIFIER = new Identifier("createliquidfuel", "blaze_burner_fuel_loader");
    private static final Gson GSON = new Gson();
    public static final LiquidBurnerFuelJsonLoader INSTANCE = new LiquidBurnerFuelJsonLoader();

    private LiquidBurnerFuelJsonLoader() {
        super(GSON, "blaze_burner_fuel");
    }

    @Override
    public Identifier getFabricId() {
        return IDENTIFIER;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager resourceManager, Profiler profiler) {
        Iterator<Entry<Fluid, Pair<Identifier, Triplet<Integer, Boolean, Integer>>>> iterator =
            BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Fluid, Pair<Identifier, Triplet<Integer, Boolean, Integer>>> entry = iterator.next();
            if (IDENTIFIER.equals(entry.getValue().getFirst())) {
                iterator.remove();
            }
        }

        for (Entry<Identifier, JsonElement> entry : data.entrySet()) {
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            JsonElement fluidElement = object.get("fluid");
            if (fluidElement == null) {
                throw new RuntimeException("No fluid specified for liquid burner fuel: " + entry.getKey());
            }

            Identifier fluidId = new Identifier(fluidElement.getAsString());
            if (!Registries.FLUID.containsId(fluidId)) {
                throw new RuntimeException("Fluid liquid burner fuel " + entry.getKey() + " has invalid fluid: " + fluidElement.getAsString());
            }

            Fluid fluid = Registries.FLUID.get(fluidId);
            boolean superHeat = object.has("superHeat") && object.get("superHeat").getAsBoolean();
            int burnTime = object.has("burnTime") ? object.get("burnTime").getAsInt() : (superHeat ? 32 : 20);
            int amount = object.has("amountConsumedPerTick") ? object.get("amountConsumedPerTick").getAsInt() : (superHeat ? 10 : 1);

            BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP
                .put(fluid, Pair.of(IDENTIFIER, Triplet.of(burnTime, superHeat, amount)));
        }
    }
}
