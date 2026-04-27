package asd.itamio.veinminer;

import net.minecraftforge.common.config.Configuration;

public class VeinMinerConfig {
    public boolean enableVeinMiner;
    public boolean requireSneak;
    public int maxBlocks;
    public boolean consumeDurability;
    public boolean consumeHunger;
    public float hungerMultiplier;
    public boolean limitToCorrectTool;
    public boolean dropAtOneLocation;
    public boolean disableParticles;
    public boolean disableSound;
    public int cooldownTicks;
    public boolean mineOres;
    public boolean mineLogs;
    public boolean mineStone;
    public boolean mineDirt;
    public boolean mineGravel;
    public boolean mineSand;
    public boolean mineClay;
    public boolean mineNetherrack;
    public boolean mineEndStone;
    public boolean mineGlowstone;

    public VeinMinerConfig(Configuration config) {
        config.load();
        enableVeinMiner = config.getBoolean("Enable Vein Miner", "general", true, "Enable or disable vein mining");
        requireSneak = config.getBoolean("Require Sneak", "general", true, "Require player to sneak to activate vein mining");
        maxBlocks = config.getInt("Max Blocks", "general", 64, 1, 1000, "Maximum number of blocks to mine in one vein");
        consumeDurability = config.getBoolean("Consume Durability", "balance", true, "Consume tool durability for each block mined");
        consumeHunger = config.getBoolean("Consume Hunger", "balance", true, "Consume hunger/exhaustion for vein mining");
        hungerMultiplier = config.getFloat("Hunger Multiplier", "balance", 1.0f, 0.0f, 10.0f, "Multiplier for hunger exhaustion");
        limitToCorrectTool = config.getBoolean("Limit To Correct Tool", "balance", true, "Only allow vein mining with the correct tool type");
        dropAtOneLocation = config.getBoolean("Drop At One Location", "performance", true, "Drop all items at the first mined block location");
        disableParticles = config.getBoolean("Disable Particles", "performance", true, "Disable block break particles for vein mined blocks");
        disableSound = config.getBoolean("Disable Sound", "performance", false, "Disable individual block break sounds");
        cooldownTicks = config.getInt("Cooldown Ticks", "balance", 0, 0, 200, "Cooldown in ticks between vein mining uses");
        mineOres = config.getBoolean("Mine Ores", "blocks", true, "Allow vein mining of ore blocks");
        mineLogs = config.getBoolean("Mine Logs", "blocks", true, "Allow vein mining of log blocks");
        mineStone = config.getBoolean("Mine Stone", "blocks", false, "Allow vein mining of stone/cobblestone");
        mineDirt = config.getBoolean("Mine Dirt", "blocks", false, "Allow vein mining of dirt");
        mineGravel = config.getBoolean("Mine Gravel", "blocks", false, "Allow vein mining of gravel");
        mineSand = config.getBoolean("Mine Sand", "blocks", false, "Allow vein mining of sand");
        mineClay = config.getBoolean("Mine Clay", "blocks", false, "Allow vein mining of clay");
        mineNetherrack = config.getBoolean("Mine Netherrack", "blocks", false, "Allow vein mining of netherrack");
        mineEndStone = config.getBoolean("Mine End Stone", "blocks", false, "Allow vein mining of end stone");
        mineGlowstone = config.getBoolean("Mine Glowstone", "blocks", true, "Allow vein mining of glowstone");
        if (config.hasChanged()) config.save();
    }
}
