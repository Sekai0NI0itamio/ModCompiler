package com.bothelpers.client.gui;

import com.bothelpers.entity.EntityBotHelper;
import com.bothelpers.script.BotJobScriptPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class BotJobScriptStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BotJobScriptStorage() {
    }

    static List<BotJobEditorFrame.ScratchBlock> load(EntityBotHelper bot) {
        List<BotJobEditorFrame.ScratchBlock> blocks = new ArrayList<>();
        File scriptFile = resolveScriptFile(bot);
        if (scriptFile == null || !scriptFile.isFile()) {
            return blocks;
        }

        try {
            String json = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
            SavedScript savedScript = GSON.fromJson(json, SavedScript.class);
            if (savedScript == null || savedScript.blocks == null) {
                return blocks;
            }

            List<Integer> nextIndexes = new ArrayList<>();
            List<Integer> branchIndexes = new ArrayList<>();
            for (SavedBlock savedBlock : savedScript.blocks) {
                if (savedBlock == null) {
                    continue;
                }

                BotJobEditorFrame.ScratchBlock block = new BotJobEditorFrame.ScratchBlock(
                    savedBlock.text == null ? "" : savedBlock.text,
                    savedBlock.x,
                    savedBlock.y,
                    savedBlock.hasInput
                );
                block.inputType = (savedBlock.inputType == null || savedBlock.inputType.isEmpty()) ? "Name" : savedBlock.inputType;
                block.inputValue = savedBlock.inputValue == null ? "" : savedBlock.inputValue;
                block.coordX = savedBlock.coordX;
                block.coordY = savedBlock.coordY;
                block.coordZ = savedBlock.coordZ;
                block.secondaryType = savedBlock.secondaryType == null ? "Coordinate" : savedBlock.secondaryType;
                block.secondaryValue = savedBlock.secondaryValue == null ? "" : savedBlock.secondaryValue;
                block.secondaryCoordX = savedBlock.secondaryCoordX;
                block.secondaryCoordY = savedBlock.secondaryCoordY;
                block.secondaryCoordZ = savedBlock.secondaryCoordZ;
                block.toolType = savedBlock.toolType == null ? "Inventory Tool" : savedBlock.toolType;
                block.toolValue = savedBlock.toolValue == null ? "" : savedBlock.toolValue;
                block.regionMode = savedBlock.regionMode == null ? "No Region Restriction" : savedBlock.regionMode;
                block.regionValue = savedBlock.regionValue == null ? "" : savedBlock.regionValue;
                block.saplingMode = savedBlock.saplingMode == null ? "No Replant" : savedBlock.saplingMode;
                block.saplingFilterMode = savedBlock.saplingFilterMode == null ? "Whitelist" : savedBlock.saplingFilterMode;
                block.saplingValue = savedBlock.saplingValue == null ? "" : savedBlock.saplingValue;
                block.refreshLayout();

                blocks.add(block);
                nextIndexes.add(savedBlock.nextIndex);
                branchIndexes.add(savedBlock.branchIndex);
            }

            for (int i = 0; i < blocks.size(); i++) {
                Integer nextIndex = nextIndexes.get(i);
                if (nextIndex != null && nextIndex >= 0 && nextIndex < blocks.size()) {
                    blocks.get(i).next = blocks.get(nextIndex);
                }
                Integer branchIndex = branchIndexes.get(i);
                if (branchIndex != null && branchIndex >= 0 && branchIndex < blocks.size()) {
                    blocks.get(i).branch = blocks.get(branchIndex);
                }
            }
        } catch (IOException | RuntimeException ex) {
            System.err.println("Bot Helpers: Failed to load bot script from " + scriptFile.getAbsolutePath());
            ex.printStackTrace();
        }

        return blocks;
    }

    static File save(EntityBotHelper bot, List<BotJobEditorFrame.ScratchBlock> blocks) throws IOException {
        File scriptFile = resolveScriptFile(bot);
        if (scriptFile == null) {
            throw new IOException("Could not resolve the bot script directory.");
        }

        Files.createDirectories(scriptFile.getParentFile().toPath());

        SavedScript savedScript = new SavedScript();
        savedScript.botName = bot.getName();
        savedScript.botUuid = bot.getUniqueID().toString();
        savedScript.dimension = bot.dimension;

        Map<BotJobEditorFrame.ScratchBlock, Integer> indexes = new IdentityHashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            indexes.put(blocks.get(i), i);
        }

        for (BotJobEditorFrame.ScratchBlock block : blocks) {
            SavedBlock savedBlock = new SavedBlock();
            savedBlock.text = block.text;
            savedBlock.x = block.x;
            savedBlock.y = block.y;
            savedBlock.hasInput = block.hasInput;
            savedBlock.inputType = block.inputType;
            savedBlock.inputValue = block.inputValue;
            savedBlock.coordX = block.coordX;
            savedBlock.coordY = block.coordY;
            savedBlock.coordZ = block.coordZ;
            savedBlock.secondaryType = block.secondaryType;
            savedBlock.secondaryValue = block.secondaryValue;
            savedBlock.secondaryCoordX = block.secondaryCoordX;
            savedBlock.secondaryCoordY = block.secondaryCoordY;
            savedBlock.secondaryCoordZ = block.secondaryCoordZ;
            savedBlock.toolType = block.toolType;
            savedBlock.toolValue = block.toolValue;
            savedBlock.regionMode = block.regionMode;
            savedBlock.regionValue = block.regionValue;
            savedBlock.saplingMode = block.saplingMode;
            savedBlock.saplingFilterMode = block.saplingFilterMode;
            savedBlock.saplingValue = block.saplingValue;
            savedBlock.nextIndex = block.next == null ? null : indexes.get(block.next);
            savedBlock.branchIndex = block.branch == null ? null : indexes.get(block.branch);
            savedScript.blocks.add(savedBlock);
        }

        Files.write(scriptFile.toPath(), GSON.toJson(savedScript).getBytes(StandardCharsets.UTF_8));
        return scriptFile;
    }

    private static File resolveScriptFile(EntityBotHelper bot) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return null;
        }

        File worldDirectory = resolveWorldDirectory(minecraft, bot.dimension);
        File rootDirectory = worldDirectory != null ? worldDirectory : minecraft.gameDir;
        return BotJobScriptPaths.getScriptFile(rootDirectory, bot.getName(), bot.getUniqueID());
    }

    private static File resolveWorldDirectory(Minecraft minecraft, int dimension) {
        if (!minecraft.isSingleplayer()) {
            return null;
        }

        IntegratedServer server = minecraft.getIntegratedServer();
        if (server == null) {
            return null;
        }

        World world = server.getWorld(dimension);
        if (world == null) {
            world = server.getWorld(0);
        }

        return world == null || world.getSaveHandler() == null ? null : world.getSaveHandler().getWorldDirectory();
    }

    private static final class SavedScript {
        String botName;
        String botUuid;
        int dimension;
        List<SavedBlock> blocks = new ArrayList<>();
    }

    private static final class SavedBlock {
        String text;
        int x;
        int y;
        boolean hasInput;
        String inputType;
        String inputValue;
        int coordX;
        int coordY;
        int coordZ;
        String secondaryType;
        String secondaryValue;
        int secondaryCoordX;
        int secondaryCoordY;
        int secondaryCoordZ;
        String toolType;
        String toolValue;
        String regionMode;
        String regionValue;
        String saplingMode;
        String saplingFilterMode;
        String saplingValue;
        Integer nextIndex;
        Integer branchIndex;
    }
}
