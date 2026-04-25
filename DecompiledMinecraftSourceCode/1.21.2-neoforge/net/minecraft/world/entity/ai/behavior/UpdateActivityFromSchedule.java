package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;

public class UpdateActivityFromSchedule {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(p_259429_ -> p_259429_.point((p_390607_, p_390608_, p_390609_) -> {
            p_390608_.getBrain().updateActivityFromSchedule(p_390607_.getDayTime(), p_390607_.getGameTime());
            return true;
        }));
    }
}
