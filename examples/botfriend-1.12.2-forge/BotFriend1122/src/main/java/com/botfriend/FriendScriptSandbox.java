package com.botfriend;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.apache.logging.log4j.Logger;

public final class FriendScriptSandbox {
    private FriendScriptSandbox() {
    }

    public static String execute(BotFriendPlayer friend, String script, Logger logger) throws Exception {
        if (!BotFriendConfig.data.selfCoding.enabled) {
            throw new IllegalStateException("Self-coding is disabled in config.");
        }
        if (script == null || script.trim().isEmpty()) {
            return "No script content to run.";
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(new ScriptTask(friend, script));
            return future.get(Math.max(1, BotFriendConfig.data.selfCoding.maxMillis), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            logger.warn("BotFriend sandbox rejected script: {}", ex.getMessage());
            throw ex;
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class ScriptTask implements Callable<String> {
        private final BotFriendPlayer friend;
        private final String script;

        private ScriptTask(BotFriendPlayer friend, String script) {
            this.friend = friend;
            this.script = script;
        }

        @Override
        public String call() throws ScriptException {
            NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
            ScriptEngine engine = factory.getScriptEngine(new NoJavaClassFilter());
            Bindings bindings = engine.createBindings();
            bindings.put("api", new ScriptApi(friend));
            engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
            Object output = engine.eval(script);
            return output == null ? "Script completed." : output.toString();
        }
    }

    private static final class NoJavaClassFilter implements ClassFilter {
        @Override
        public boolean exposeToScripts(String className) {
            return false;
        }
    }

    public static final class ScriptApi {
        private final BotFriendPlayer friend;

        public ScriptApi(BotFriendPlayer friend) {
            this.friend = friend;
        }

        public void say(String message) {
            friend.say(message);
        }

        public void followOwner() {
            friend.followOwner();
        }

        public void stop() {
            friend.stopActions();
        }

        public void guardOwner() {
            friend.guardOwner();
        }

        public void goTo(double x, double y, double z) {
            friend.setGotoTarget(x, y, z);
        }

        public void mineNearest(String blockName) {
            friend.setMineNearestNamedBlock(blockName);
        }

        public void attackNearest(String entityType) {
            friend.attackNearest(entityType);
        }
    }
}
