package net.minecraft.server.bossevents;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class CustomBossEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Map<ResourceLocation, CustomBossEvent.Packed>> EVENTS_CODEC = Codec.unboundedMap(
        ResourceLocation.CODEC, CustomBossEvent.Packed.CODEC
    );
    private final Map<ResourceLocation, CustomBossEvent> events = Maps.newHashMap();

    @Nullable
    public CustomBossEvent get(ResourceLocation p_136298_) {
        return this.events.get(p_136298_);
    }

    public CustomBossEvent create(ResourceLocation p_136300_, Component p_136301_) {
        CustomBossEvent custombossevent = new CustomBossEvent(p_136300_, p_136301_);
        this.events.put(p_136300_, custombossevent);
        return custombossevent;
    }

    public void remove(CustomBossEvent p_136303_) {
        this.events.remove(p_136303_.getTextId());
    }

    public Collection<ResourceLocation> getIds() {
        return this.events.keySet();
    }

    public Collection<CustomBossEvent> getEvents() {
        return this.events.values();
    }

    public CompoundTag save(HolderLookup.Provider p_328754_) {
        Map<ResourceLocation, CustomBossEvent.Packed> map = Util.mapValues(this.events, CustomBossEvent::pack);
        return (CompoundTag)EVENTS_CODEC.encodeStart(p_328754_.createSerializationContext(NbtOps.INSTANCE), map).getOrThrow();
    }

    public void load(CompoundTag p_136296_, HolderLookup.Provider p_329843_) {
        Map<ResourceLocation, CustomBossEvent.Packed> map = EVENTS_CODEC.parse(p_329843_.createSerializationContext(NbtOps.INSTANCE), p_136296_)
            .resultOrPartial(p_397243_ -> LOGGER.error("Failed to parse boss bar events: {}", p_397243_))
            .orElse(Map.of());
        map.forEach((p_393451_, p_392436_) -> this.events.put(p_393451_, CustomBossEvent.load(p_393451_, p_392436_)));
    }

    public void onPlayerConnect(ServerPlayer p_136294_) {
        for (CustomBossEvent custombossevent : this.events.values()) {
            custombossevent.onPlayerConnect(p_136294_);
        }
    }

    public void onPlayerDisconnect(ServerPlayer p_136306_) {
        for (CustomBossEvent custombossevent : this.events.values()) {
            custombossevent.onPlayerDisconnect(p_136306_);
        }
    }
}
