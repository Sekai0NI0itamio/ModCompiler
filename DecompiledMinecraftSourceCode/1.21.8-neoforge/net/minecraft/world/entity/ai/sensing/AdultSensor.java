package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class AdultSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }

    @Override
    protected void doTick(ServerLevel p_26620_, LivingEntity p_26621_) {
        p_26621_.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).ifPresent(p_405419_ -> this.setNearestVisibleAdult(p_26621_, p_405419_));
    }

    protected void setNearestVisibleAdult(LivingEntity p_408844_, NearestVisibleLivingEntities p_186142_) {
        Optional<LivingEntity> optional = p_186142_.findClosest(p_405417_ -> p_405417_.getType() == p_408844_.getType() && !p_405417_.isBaby())
            .map(LivingEntity.class::cast);
        p_408844_.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, optional);
    }
}
