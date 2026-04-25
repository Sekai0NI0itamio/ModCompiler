package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RealmsSlotUpdateDto implements ReflectionBasedSerialization {
    @SerializedName("slotId")
    public final int slotId;
    @SerializedName("pvp")
    private final boolean pvp;
    @SerializedName("spawnMonsters")
    private final boolean spawnMonsters;
    @SerializedName("spawnProtection")
    private final int spawnProtection;
    @SerializedName("commandBlocks")
    private final boolean commandBlocks;
    @SerializedName("forceGameMode")
    private final boolean forceGameMode;
    @SerializedName("difficulty")
    private final int difficulty;
    @SerializedName("gameMode")
    private final int gameMode;
    @SerializedName("slotName")
    private final String slotName;
    @SerializedName("version")
    private final String version;
    @SerializedName("compatibility")
    private final RealmsServer.Compatibility compatibility;
    @SerializedName("worldTemplateId")
    private final long templateId;
    @Nullable
    @SerializedName("worldTemplateImage")
    private final String templateImage;
    @SerializedName("hardcore")
    private final boolean hardcore;

    public RealmsSlotUpdateDto(int p_407504_, RealmsWorldOptions p_409419_, boolean p_406504_) {
        this.slotId = p_407504_;
        this.pvp = p_409419_.pvp;
        this.spawnMonsters = p_409419_.spawnMonsters;
        this.spawnProtection = p_409419_.spawnProtection;
        this.commandBlocks = p_409419_.commandBlocks;
        this.forceGameMode = p_409419_.forceGameMode;
        this.difficulty = p_409419_.difficulty;
        this.gameMode = p_409419_.gameMode;
        this.slotName = p_409419_.getSlotName(p_407504_);
        this.version = p_409419_.version;
        this.compatibility = p_409419_.compatibility;
        this.templateId = p_409419_.templateId;
        this.templateImage = p_409419_.templateImage;
        this.hardcore = p_406504_;
    }
}
