package com.itamio.accountswitcher;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigHandler {
    private static final String CONFIG_FILE = "account.txt";
    private static final String CONFIG_DIR = "config";
    private Path configPath;
    private String currentAccount = "Unknown";
    private long lastModified = 0L;
    private ScheduledExecutorService executor;

    public ConfigHandler() {
        initializeConfigFile();
    }

    private void initializeConfigFile() {
        try {
            File configDir = new File("config");
            if (!configDir.exists()) configDir.mkdirs();
            configPath = Paths.get("config", "account.txt");
            if (!Files.exists(configPath)) {
                createConfigWithCurrentAccount();
            } else {
                currentAccount = readAccountFromFile();
            }
            AccountSwitcherMod.getLogger().info("Config initialized. Current account: " + currentAccount);
        } catch (IOException e) {
            AccountSwitcherMod.getLogger().error("Failed to initialize config file", e);
        }
    }

    private void createConfigWithCurrentAccount() throws IOException {
        String name = SessionManager.getCurrentAccount();
        if (name == null || name.isEmpty() || name.equals("Unknown")) name = "Player";
        try (BufferedWriter w = Files.newBufferedWriter(configPath)) {
            w.write("# Account Switcher Configuration"); w.newLine();
            w.write("# Change the account name below to switch accounts"); w.newLine();
            w.write("account=" + name); w.newLine();
        }
        currentAccount = name;
        AccountSwitcherMod.getLogger().info("Created config file with account: " + name);
    }

    private String readAccountFromFile() {
        try {
            if (Files.exists(configPath)) {
                for (String line : Files.readAllLines(configPath)) {
                    line = line.trim();
                    if (line.startsWith("account=")) return line.substring(8).trim();
                }
            }
        } catch (IOException e) {
            AccountSwitcherMod.getLogger().error("Failed to read config file", e);
        }
        return "Unknown";
    }

    public void startMonitoring() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            if (!currentAccount.equals("Unknown")) {
                AccountSwitcherMod.getLogger().info("Performing initial account switch to: " + currentAccount);
                int retries = 0;
                while (retries < 10 && !SessionManager.isMinecraftReady()) {
                    try { Thread.sleep(1000L); retries++; }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
                switchAccount(currentAccount);
                AccountSwitcherMod.updateCurrentAccount(currentAccount);
            }
        }, 3L, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(this::checkForChanges, 5L, 1L, TimeUnit.SECONDS);
        AccountSwitcherMod.getLogger().info("Started monitoring config file for changes");
    }

    private void checkForChanges() {
        try {
            if (Files.exists(configPath)) {
                long modified = Files.getLastModifiedTime(configPath).toMillis();
                if (modified > lastModified) {
                    lastModified = modified;
                    String newAccount = readAccountFromFile();
                    if (!newAccount.equals(currentAccount)) {
                        AccountSwitcherMod.getLogger().info("Account change detected: " + currentAccount + " -> " + newAccount);
                        currentAccount = newAccount;
                        switchAccount(newAccount);
                        AccountSwitcherMod.updateCurrentAccount(newAccount);
                    }
                }
            }
        } catch (IOException e) {
            AccountSwitcherMod.getLogger().error("Error checking config changes", e);
        }
    }

    private void switchAccount(String accountName) {
        try {
            if (!SessionManager.isMinecraftReady()) {
                AccountSwitcherMod.getLogger().warn("Minecraft not ready, skipping account switch");
                return;
            }
            boolean success = SessionManager.switchToAccount(accountName);
            if (success) {
                currentAccount = accountName;
                AccountSwitcherMod.getLogger().info("Account switch completed: " + accountName);
            } else {
                AccountSwitcherMod.getLogger().error("Account switch failed");
            }
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Failed to switch account", e);
        }
    }

    public String getCurrentAccount() { return currentAccount; }

    public void stopMonitoring() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
