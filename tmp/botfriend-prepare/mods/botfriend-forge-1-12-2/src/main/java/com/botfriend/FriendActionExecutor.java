package com.botfriend;

import com.botfriend.FriendBrainService.ActionStep;
import com.botfriend.FriendBrainService.BrainPlan;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.Logger;

public final class FriendActionExecutor {
    private final Logger logger;

    public FriendActionExecutor(Logger logger) {
        this.logger = logger;
    }

    public void apply(BotFriendPlayer friend, BrainPlan plan) {
        if (plan == null) {
            friend.tellOwner("I couldn't understand that request.");
            return;
        }
        if (plan.memorySummary != null && !plan.memorySummary.trim().isEmpty()) {
            friend.getRecord().setMemorySummary(trim(plan.memorySummary, BotFriendConfig.data.maxMemoryCharacters));
        }
        if (plan.mode != null && !plan.mode.trim().isEmpty()) {
            friend.setMode(plan.mode);
        }
        if (plan.chat != null && !plan.chat.trim().isEmpty()) {
            friend.say(plan.chat.trim());
        }
        for (ActionStep step : plan.actions) {
            runAction(friend, step);
        }
    }

    private void runAction(BotFriendPlayer friend, ActionStep step) {
        if (step == null || step.type == null) {
            return;
        }
        String type = step.type.trim().toLowerCase();
        if ("say".equals(type)) {
            if (step.item != null && !step.item.isEmpty()) {
                friend.say(step.item);
            }
            return;
        }
        if ("follow_owner".equals(type) || "go_to_owner".equals(type)) {
            friend.followOwner();
            return;
        }
        if ("stay".equals(type) || "stop".equals(type)) {
            friend.stopActions();
            return;
        }
        if ("go_to_coords".equals(type)) {
            friend.setGotoTarget(step.x, step.y, step.z);
            return;
        }
        if ("equip".equals(type)) {
            if (step.item != null && !step.item.isEmpty()) {
                friend.equipNamedItem(step.item);
            }
            return;
        }
        if ("guard_owner".equals(type)) {
            friend.guardOwner();
            return;
        }
        if ("attack_nearest".equals(type)) {
            friend.attackNearest(step.entityType);
            return;
        }
        if ("mine_block".equals(type)) {
            friend.setMineTarget(new BlockPos(step.x, step.y, step.z));
            return;
        }
        if ("mine_nearest_named_block".equals(type)) {
            friend.setMineNearestNamedBlock(step.block);
            return;
        }
        if ("script".equals(type) || "run_script".equals(type)) {
            if (friend.getRecord().isSelfCodingEnabled() && step.script != null && !step.script.trim().isEmpty()) {
                try {
                    String outcome = FriendScriptSandbox.execute(friend, step.script, logger);
                    if (outcome != null && !outcome.trim().isEmpty()) {
                        friend.tellOwner(outcome);
                    }
                } catch (Exception ex) {
                    friend.tellOwner("Script rejected: " + ex.getMessage());
                }
            }
        }
    }

    private static String trim(String input, int limit) {
        if (input == null) {
            return "";
        }
        if (input.length() <= limit) {
            return input;
        }
        return input.substring(0, Math.max(0, limit));
    }
}
