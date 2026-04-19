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
            logger.info("[BOTFRIEND:EXEC] {} received null plan", friend.getFriendName());
            friend.tellOwner("I couldn't understand that request.");
            return;
        }
        logger.info("[BOTFRIEND:EXEC] === {} executing plan ===", friend.getFriendName());
        if (plan.memorySummary != null && !plan.memorySummary.trim().isEmpty()) {
            logger.info("[BOTFRIEND:EXEC] memory: {}", plan.memorySummary);
            friend.getRecord().setMemorySummary(trim(plan.memorySummary, BotFriendConfig.data.maxMemoryCharacters));
        }
        if (plan.mode != null && !plan.mode.trim().isEmpty()) {
            logger.info("[BOTFRIEND:EXEC] mode: {}", plan.mode);
            friend.setMode(plan.mode);
        }
        if (plan.chat != null && !plan.chat.trim().isEmpty()) {
            logger.info("[BOTFRIEND:EXEC] chat: {}", plan.chat);
            friend.say(plan.chat.trim());
        }
        for (int i = 0; i < plan.actions.size(); i++) {
            ActionStep step = plan.actions.get(i);
            logger.info("[BOTFRIEND:EXEC] action[{}]: type={} text={} item={} block={} x={} y={} z={}",
                i, step.type, step.text, step.item, step.block, step.x, step.y, step.z);
            runAction(friend, step);
        }
    }

    private void runAction(BotFriendPlayer friend, ActionStep step) {
        if (step == null || step.type == null) {
            return;
        }
        String type = step.type.trim().toLowerCase();
        if ("say".equals(type)) {
            String text = step.text != null && !step.text.isEmpty() ? step.text : step.item;
            if (text != null && !text.isEmpty()) {
                friend.say(text);
            }
            return;
        }
        if ("follow_owner".equals(type) || "go_to_owner".equals(type)) {
            friend.followOwner();
            logger.info("[BOTFRIEND:EXEC] -> followOwner OK");
            return;
        }
        if ("stay".equals(type) || "stop".equals(type)) {
            friend.stopActions();
            logger.info("[BOTFRIEND:EXEC] -> stopActions OK");
            return;
        }
        if ("go_to_coords".equals(type)) {
            friend.setGotoTarget(step.x, step.y, step.z);
            logger.info("[BOTFRIEND:EXEC] -> go_to_coords ({},{},{}) OK", step.x, step.y, step.z);
            return;
        }
        if ("equip".equals(type)) {
            if (step.item != null && !step.item.isEmpty()) {
                friend.equipNamedItem(step.item);
                logger.info("[BOTFRIEND:EXEC] -> equip {} OK", step.item);
            }
            return;
        }
        if ("guard_owner".equals(type)) {
            friend.guardOwner();
            logger.info("[BOTFRIEND:EXEC] -> guardOwner OK");
            return;
        }
        if ("attack_nearest".equals(type)) {
            friend.attackNearest(step.entityType);
            logger.info("[BOTFRIEND:EXEC] -> attack_nearest type={} OK", step.entityType);
            return;
        }
        if ("mine_block".equals(type)) {
            friend.setMineTarget(new BlockPos(step.x, step.y, step.z));
            logger.info("[BOTFRIEND:EXEC] -> mine_block ({},{},{}) OK", step.x, step.y, step.z);
            return;
        }
        if ("mine_nearest_named_block".equals(type) || "mine_nearest".equals(type)) {
            friend.setMineNearestNamedBlock(step.block);
            logger.info("[BOTFRIEND:EXEC] -> mine_nearest {} OK", step.block);
            return;
        }
        if ("script".equals(type) || "run_script".equals(type)) {
            if (friend.getRecord().isSelfCodingEnabled() && step.script != null && !step.script.trim().isEmpty()) {
                try {
                    String outcome = FriendScriptSandbox.execute(friend, step.script, logger);
                    logger.info("[BOTFRIEND:EXEC] -> script result: {}", outcome);
                    if (outcome != null && !outcome.trim().isEmpty()) {
                        friend.tellOwner(outcome);
                    }
                } catch (Exception ex) {
                    logger.warn("[BOTFRIEND:EXEC] -> script rejected: {}", ex.getMessage());
                    friend.tellOwner("Script rejected: " + ex.getMessage());
                }
            }
            return;
        }
        logger.warn("[BOTFRIEND:EXEC] -> unknown action type: {}", type);
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
