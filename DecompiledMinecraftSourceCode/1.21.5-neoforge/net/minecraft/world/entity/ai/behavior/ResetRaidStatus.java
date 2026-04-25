package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;

public class ResetRaidStatus {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(p_259870_ -> p_259870_.point((p_390575_, p_390576_, p_390577_) -> {
            if (p_390575_.random.nextInt(20) != 0) {
                return false;
            } else {
                Brain<?> brain = p_390576_.getBrain();
                Raid raid = p_390575_.getRaidAt(p_390576_.blockPosition());
                if (raid == null || raid.isStopped() || raid.isLoss()) {
                    brain.setDefaultActivity(Activity.IDLE);
                    brain.updateActivityFromSchedule(p_390575_.getDayTime(), p_390575_.getGameTime());
                }

                return true;
            }
        }));
    }
}
